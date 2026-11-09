//! A recording belongs to its task until a history row takes ownership.
//! Dropping a task, a cancelled join, or a failed write removes its temporary WAV.
use anyhow::Result;
use std::path::Path;
use tempfile::{Builder, NamedTempFile};

pub struct PendingRecording(NamedTempFile);

impl PendingRecording {
    pub fn save_if_enabled(dir: &Path, samples: &[f32], enabled: bool) -> Result<Option<Self>> {
        if !enabled {
            return Ok(None);
        }
        let file = Builder::new()
            .prefix("dictation-")
            .suffix(".wav")
            .tempfile_in(dir)?;
        let recording = Self(file);
        crate::audio_toolkit::save_wav_file(recording.0.path(), samples)?;
        crate::audio_toolkit::verify_wav_file(recording.0.path(), samples.len())?;
        Ok(Some(recording))
    }

    pub fn file_name(&self) -> String {
        self.0
            .path()
            .file_name()
            .expect("generated temporary filename")
            .to_string_lossy()
            .into_owned()
    }

    /// Infallible ownership transfer, only after the row has been inserted.
    pub fn retain(mut self) {
        self.0.disable_cleanup(true);
    }
}

pub fn managed_recording_name(name: &str) -> bool {
    let Some(stem) = name.strip_suffix(".wav") else {
        return false;
    };
    if let Some(stamp) = stem.strip_prefix("handy-") {
        return !stamp.is_empty() && stamp.bytes().all(|b| b.is_ascii_digit());
    }
    stem.strip_prefix("dictation-")
        .is_some_and(|id| id.len() >= 6 && id.bytes().all(|b| b.is_ascii_alphanumeric()))
}

/// Called before any recording tasks start. Never follow links or remove imports/user files.
pub fn remove_unreferenced(
    dir: &Path,
    referenced: &std::collections::HashSet<String>,
) -> std::io::Result<()> {
    for entry in std::fs::read_dir(dir)? {
        let entry = entry?;
        let name = entry.file_name();
        if entry.file_type()?.is_file()
            && managed_recording_name(&name.to_string_lossy())
            && !referenced.contains(name.to_string_lossy().as_ref())
        {
            std::fs::remove_file(entry.path())?;
        }
    }
    Ok(())
}

#[cfg(test)]
mod privacy_regression {
    use super::*;

    #[test]
    fn orphan_recovery_preserves_referenced_audio_imports_and_foreign_files() {
        let directory = tempfile::tempdir().unwrap();
        for file in [
            "handy-123.wav",
            "dictation-AbCd12.wav",
            "handy-file-456.wav",
            "my-audio.wav",
        ] {
            std::fs::write(directory.path().join(file), "synthetic").unwrap();
        }
        let referenced = ["dictation-AbCd12.wav".to_string()].into_iter().collect();
        remove_unreferenced(directory.path(), &referenced).unwrap();
        assert!(!directory.path().join("handy-123.wav").exists());
        for file in ["dictation-AbCd12.wav", "handy-file-456.wav", "my-audio.wav"] {
            assert!(directory.path().join(file).is_file());
        }
    }

    #[cfg(unix)]
    #[test]
    fn orphan_recovery_never_follows_a_symlink() {
        let directory = tempfile::tempdir().unwrap();
        let target = directory.path().join("private.txt");
        std::fs::write(&target, "synthetic").unwrap();
        let link = directory.path().join("handy-123.wav");
        std::os::unix::fs::symlink(&target, &link).unwrap();
        remove_unreferenced(directory.path(), &Default::default()).unwrap();
        assert!(link.is_symlink());
        assert!(target.is_file());
    }

    #[test]
    fn disabled_history_creates_no_file_or_directory() {
        let directory = tempfile::tempdir().unwrap();
        let absent = directory.path().join("must-not-exist");
        assert!(
            PendingRecording::save_if_enabled(&absent, &[0.1; 1600], false)
                .unwrap()
                .is_none()
        );
        assert!(!absent.exists());
    }

    #[test]
    fn cancelled_recording_is_deleted_and_committed_one_survives() {
        let directory = tempfile::tempdir().unwrap();
        let first = PendingRecording::save_if_enabled(directory.path(), &[0.1; 1600], true)
            .unwrap()
            .unwrap();
        let first_path = directory.path().join(first.file_name());
        assert!(first_path.is_file());
        let second = PendingRecording::save_if_enabled(directory.path(), &[0.1; 1600], true)
            .unwrap()
            .unwrap();
        let second_path = directory.path().join(second.file_name());
        assert_ne!(first_path, second_path);
        drop(first);
        assert!(!first_path.exists());
        second.retain();
        assert!(second_path.is_file());
    }

    #[tokio::test]
    async fn abandoning_the_writer_does_not_leave_a_late_file() {
        struct Observed {
            recording: Option<PendingRecording>,
            finished: Option<tokio::sync::oneshot::Sender<()>>,
        }
        impl Drop for Observed {
            fn drop(&mut self) {
                drop(self.recording.take());
                if let Some(done) = self.finished.take() {
                    let _ = done.send(());
                }
            }
        }
        let directory = tempfile::tempdir().unwrap();
        let path = directory.path().to_owned();
        let (release, wait) = std::sync::mpsc::channel();
        let (finished, completion) = tokio::sync::oneshot::channel();
        let task = tokio::task::spawn_blocking(move || {
            wait.recv().unwrap();
            Observed {
                recording: PendingRecording::save_if_enabled(&path, &[0.1; 1600], true).unwrap(),
                finished: Some(finished),
            }
        });
        drop(task);
        release.send(()).unwrap();
        tokio::time::timeout(std::time::Duration::from_secs(5), completion)
            .await
            .unwrap()
            .unwrap();
        assert_eq!(std::fs::read_dir(directory.path()).unwrap().count(), 0);
    }

    #[test]
    fn orphan_cleanup_recognizes_only_reserved_names() {
        assert!(managed_recording_name("handy-1794172821.wav"));
        assert!(managed_recording_name("dictation-aB34cd.wav"));
        for name in [
            "my-recording.wav",
            "handy-not-ours.wav",
            "dictation-../secret.wav",
            "dictation-aB34cd.mp3",
        ] {
            assert!(!managed_recording_name(name));
        }
    }
}
