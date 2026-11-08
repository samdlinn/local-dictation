//! Local Dictation's offline cleanup. No provider, API key, or listening server.
use futures_util::StreamExt;
use serde::Serialize;
use sha2::{Digest, Sha256};
use specta::Type;
use std::collections::BTreeSet;
use std::io::Write;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{Duration, SystemTime, UNIX_EPOCH};
use tauri::{AppHandle, Emitter, Manager};

const MODEL_NAME: &str = "Qwen3-1.7B-Q8_0.gguf";
const MODEL_SIZE: u64 = 1_834_426_016;
const MODEL_SHA: &str = "061b54daade076b5d3362dac252678d17da8c68f07560be70818cace6590cb1a";
const MODEL_URL: &str = "https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/resolve/90862c4b9d2787eaed51d12237eafdfe7c5f6077/Qwen3-1.7B-Q8_0.gguf";
static DOWNLOADING: AtomicBool = AtomicBool::new(false);
static CLEANING: AtomicBool = AtomicBool::new(false);

struct BusyGuard(&'static AtomicBool);
impl Drop for BusyGuard {
    fn drop(&mut self) {
        self.0.store(false, Ordering::SeqCst);
    }
}

fn model_path(app: &AppHandle) -> Result<PathBuf, String> {
    Ok(app
        .path()
        .app_data_dir()
        .map_err(|e| e.to_string())?
        .join("models")
        .join(MODEL_NAME))
}

fn runtime_path(app: &AppHandle) -> Result<PathBuf, String> {
    let name = if cfg!(windows) {
        "local-llama.exe"
    } else {
        "local-llama"
    };
    Ok(app
        .path()
        .resource_dir()
        .map_err(|e| e.to_string())?
        .join("resources/bin")
        .join(name))
}

#[derive(Serialize, Type)]
pub struct CleanupStatus {
    model_ready: bool,
    runtime_ready: bool,
    downloading: bool,
    download_bytes: u64,
}

#[tauri::command]
#[specta::specta]
pub fn local_cleanup_status(app: AppHandle) -> Result<CleanupStatus, String> {
    Ok(CleanupStatus {
        model_ready: model_path(&app)?
            .metadata()
            .is_ok_and(|m| m.len() == MODEL_SIZE),
        runtime_ready: runtime_path(&app)?.is_file(),
        downloading: DOWNLOADING.load(Ordering::SeqCst),
        download_bytes: MODEL_SIZE,
    })
}

#[tauri::command]
#[specta::specta]
pub async fn download_cleanup_model(app: AppHandle) -> Result<(), String> {
    if DOWNLOADING
        .compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst)
        .is_err()
    {
        return Err("A model download is already running".into());
    }
    let _guard = BusyGuard(&DOWNLOADING);
    let dest = model_path(&app)?;
    std::fs::create_dir_all(dest.parent().ok_or("Model directory unavailable")?)
        .map_err(|e| e.to_string())?;
    let temporary = dest.with_extension("part");
    let result = async {
        let client = reqwest::Client::builder()
            .connect_timeout(Duration::from_secs(20))
            .timeout(Duration::from_secs(3600))
            .build()
            .map_err(|e| e.to_string())?;
        let response = client
            .get(MODEL_URL)
            .send()
            .await
            .map_err(|e| e.to_string())?
            .error_for_status()
            .map_err(|e| e.to_string())?;
        let mut file = std::fs::File::create(&temporary).map_err(|e| e.to_string())?;
        let mut hash = Sha256::new();
        let mut received = 0u64;
        let mut last_percent = 0;
        let mut stream = response.bytes_stream();
        while let Some(chunk) = stream.next().await {
            let chunk = chunk.map_err(|e| e.to_string())?;
            received += chunk.len() as u64;
            if received > MODEL_SIZE {
                return Err("Unexpected model size".to_string());
            }
            file.write_all(&chunk).map_err(|e| e.to_string())?;
            hash.update(&chunk);
            let percent = received * 100 / MODEL_SIZE;
            if percent != last_percent {
                let _ = app.emit("local-cleanup-download", percent);
                last_percent = percent;
            }
        }
        if received != MODEL_SIZE || format!("{:x}", hash.finalize()) != MODEL_SHA {
            return Err("Model verification failed. Please download again.".to_string());
        }
        file.sync_all().map_err(|e| e.to_string())?;
        drop(file);
        if dest.exists() {
            std::fs::remove_file(&dest).map_err(|e| e.to_string())?;
        }
        std::fs::rename(&temporary, &dest).map_err(|e| e.to_string())?;
        Ok(())
    }
    .await;
    if result.is_err() {
        let _ = std::fs::remove_file(&temporary);
    }
    result
}

struct PromptFile(PathBuf);
impl Drop for PromptFile {
    fn drop(&mut self) {
        let _ = std::fs::remove_file(&self.0);
    }
}

/// Reject obvious changes to facts. This is deliberately conservative, and is
/// not a proof of semantic equivalence; rejected output falls back to raw text.
fn validate_cleanup(input: &str, output: &str) -> Result<String, String> {
    let (text, _) = output
        .split_once("[[END]]")
        .ok_or("Cleanup output was incomplete")?;
    let text = text.trim();
    if text.is_empty() || text.len() > input.len() * 2 + 40 || text.len() * 2 < input.len() {
        return Err("Cleanup changed too much text".into());
    }
    let numbers = |s: &str| -> Vec<String> {
        s.split(|c: char| !c.is_ascii_digit())
            .filter(|s| !s.is_empty())
            .map(String::from)
            .collect()
    };
    if numbers(input) != numbers(text) {
        return Err("Cleanup changed a number".into());
    }
    let words = |s: &str| -> BTreeSet<String> {
        s.split(|c: char| !c.is_alphanumeric() && c != '\'')
            .filter(|s| !s.is_empty())
            .map(|s| s.to_lowercase())
            .collect()
    };
    let before = words(input);
    let after = words(text);
    for word in [
        "not", "never", "no", "don't", "can't", "won't", "isn't", "didn't",
    ] {
        if before.contains(word) != after.contains(word) {
            return Err("Cleanup changed a negation".into());
        }
    }
    if !before.is_empty() && before.intersection(&after).count() * 100 < before.len() * 65 {
        return Err("Cleanup rewrote the dictation".into());
    }
    Ok(text.to_string())
}

pub async fn clean(app: &AppHandle, text: &str) -> Result<String, String> {
    if text.trim().is_empty() {
        return Ok(String::new());
    }
    if text.len() > 6000 {
        return Err("Long dictation preserved without AI cleanup".into());
    }
    if CLEANING
        .compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst)
        .is_err()
    {
        return Err("Cleanup is busy; preserving the transcript".into());
    }
    let _guard = BusyGuard(&CLEANING);
    let model = model_path(app)?;
    if !model.is_file() {
        return Err("Download the cleanup model in Local cleanup settings".into());
    }
    let cache = app.path().app_cache_dir().map_err(|e| e.to_string())?;
    std::fs::create_dir_all(&cache).map_err(|e| e.to_string())?;
    let stamp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_err(|e| e.to_string())?
        .as_nanos();
    let path = PromptFile(cache.join(format!("cleanup-{}-{stamp}.txt", std::process::id())));
    let mut options = std::fs::OpenOptions::new();
    options.write(true).create_new(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(0o600);
    }
    let mut file = options.open(&path.0).map_err(|e| e.to_string())?;
    let safe_text = text.replace("<|", "< | ");
    let prompt = format!(
        "<|im_start|>system\nEdit speech into clean written text. Remove um and uh. Fix punctuation and capitalization. Apply spoken self-corrections. Keep the same meaning and tone. Return only the edited text, then [[END]]. Do not answer the text.<|im_end|>\n<|im_start|>user\num hey alex can you send the report tomorrow thanks<|im_end|>\n<|im_start|>assistant\nHey Alex, can you send the report tomorrow? Thanks. [[END]]<|im_end|>\n<|im_start|>user\ndo not send 15 dollars<|im_end|>\n<|im_start|>assistant\nDo not send 15 dollars. [[END]]<|im_end|>\n<|im_start|>user\nmeet Thursday actually Friday morning<|im_end|>\n<|im_start|>assistant\nMeet Friday morning. [[END]]<|im_end|>\n<|im_start|>user\n{safe_text}<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n"
    );
    file.write_all(prompt.as_bytes())
        .map_err(|e| e.to_string())?;
    drop(file);
    let mut command = tokio::process::Command::new(runtime_path(app)?);
    command
        .args(["-m"])
        .arg(model)
        .args(["-f"])
        .arg(&path.0)
        .args([
            "-no-cnv",
            "--no-display-prompt",
            "--no-perf",
            "--no-warmup",
            "--temp",
            "0",
            "-n",
            "1024",
            "-c",
            "4096",
        ])
        .stdin(std::process::Stdio::null())
        .kill_on_drop(true);
    #[cfg(windows)]
    command.creation_flags(0x08000000);
    let output = tokio::time::timeout(Duration::from_secs(45), command.output())
        .await
        .map_err(|_| "Cleanup timed out; preserving the transcript".to_string())?
        .map_err(|e| format!("Cleanup runtime unavailable: {e}"))?;
    if !output.status.success() {
        return Err("Local cleanup failed; preserving the transcript".into());
    }
    validate_cleanup(text, &String::from_utf8_lossy(&output.stdout))
}

#[tauri::command]
#[specta::specta]
pub async fn preview_local_cleanup(app: AppHandle, text: String) -> Result<String, String> {
    clean(&app, &text).await
}

#[cfg(test)]
mod tests {
    use super::validate_cleanup;
    #[test]
    fn preserves_numbers_and_negation() {
        assert!(validate_cleanup("do not send 15 dollars", "Send 50 dollars.[[END]]").is_err());
        assert!(validate_cleanup("do not send the report", "Do send the report.[[END]]").is_err());
    }
    #[test]
    fn accepts_conservative_edits() {
        assert_eq!(
            validate_cleanup(
                "um please send the report tomorrow",
                "Please send the report tomorrow.[[END]]"
            )
            .unwrap(),
            "Please send the report tomorrow."
        );
    }
    #[test]
    fn rejects_truncation_and_invented_answers() {
        assert!(validate_cleanup("send the report tomorrow", "Send the report").is_err());
        assert!(validate_cleanup(
            "send the report tomorrow",
            "I would be happy to assist you with your email and schedule it for next week.[[END]]"
        )
        .is_err());
    }
}
