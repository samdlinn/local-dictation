import { useEffect, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { useTranslation } from "react-i18next";
import { useSettings } from "../../../hooks/useSettings";
import { Button } from "../../ui/Button";
import { SettingsGroup } from "../../ui/SettingsGroup";
import { ShortcutInput } from "../ShortcutInput";

type Status = {
  model_ready: boolean;
  runtime_ready: boolean;
  downloading: boolean;
  download_bytes: number;
};
export const LocalCleanupSettings = () => {
  const { t } = useTranslation();
  const { getSetting, updateSetting } = useSettings();
  const [status, setStatus] = useState<Status>();
  const [progress, setProgress] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [input, setInput] = useState("");
  const [output, setOutput] = useState("");
  const refresh = () =>
    invoke<Status>("local_cleanup_status")
      .then(setStatus)
      .catch((e) => setError(String(e)));
  useEffect(() => {
    void refresh();
    const unlisten = listen<number>("local-cleanup-download", (event) =>
      setProgress(event.payload),
    );
    return () => {
      void unlisten.then((stop) => stop());
    };
  }, []);
  const download = async () => {
    setBusy(true);
    setError("");
    try {
      await invoke("download_cleanup_model");
      await refresh();
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  };
  const preview = async () => {
    setBusy(true);
    setError("");
    setOutput("");
    try {
      setOutput(await invoke<string>("preview_local_cleanup", { text: input }));
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="max-w-3xl w-full mx-auto space-y-6">
      <SettingsGroup title={t("localCleanup.title")}>
        <div className="p-5 space-y-4">
          <p className="text-sm text-ink-3">{t("localCleanup.description")}</p>
          {!status?.runtime_ready && (
            <p role="alert">{t("localCleanup.runtimeMissing")}</p>
          )}
          <p className="text-sm">
            {t(
              status?.model_ready
                ? "localCleanup.ready"
                : "localCleanup.needsModel",
            )}
          </p>
          {!status?.model_ready && (
            <Button
              onClick={download}
              disabled={busy || status?.downloading || !status?.runtime_ready}
            >
              {busy || status?.downloading
                ? t("localCleanup.downloading", { progress })
                : t("localCleanup.download")}
            </Button>
          )}
          <label className="flex items-center gap-3 text-sm">
            <input
              type="checkbox"
              checked={getSetting("post_process_enabled") ?? false}
              disabled={!status?.model_ready || !status.runtime_ready}
              onChange={(e) =>
                updateSetting("post_process_enabled", e.target.checked)
              }
            />
            {t("localCleanup.enable")}
          </label>
          <p className="text-xs text-ink-3">{t("localCleanup.fallback")}</p>
          {error && (
            <p role="alert" className="text-sm text-red-500">
              {error}
            </p>
          )}
        </div>
      </SettingsGroup>
      <SettingsGroup title={t("localCleanup.shortcut")}>
        <ShortcutInput shortcutId="transcribe_with_post_process" grouped />
      </SettingsGroup>
      <SettingsGroup title={t("localCleanup.try")}>
        <div className="p-5 space-y-3">
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            rows={4}
            maxLength={6000}
            aria-label={t("localCleanup.input")}
            placeholder={t("localCleanup.input")}
            className="w-full rounded-lg border border-line bg-transparent p-3 text-sm"
          />
          <Button
            disabled={
              busy ||
              !input.trim() ||
              !status?.model_ready ||
              !status?.runtime_ready
            }
            onClick={preview}
          >
            {t("localCleanup.preview")}
          </Button>
          {output && (
            <p aria-live="polite" className="text-sm whitespace-pre-wrap">
              {output}
            </p>
          )}
        </div>
      </SettingsGroup>
    </div>
  );
};
