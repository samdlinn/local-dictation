#!/usr/bin/env python3
"""Regression check for accidental upstream update or cloud defaults."""
from pathlib import Path
import json

root = Path(__file__).resolve().parents[1]
config = json.loads((root / 'src-tauri/tauri.conf.json').read_text(encoding="utf-8"))
assert config['identifier'] == 'dev.samdlinn.localdictation'
assert not config['bundle']['createUpdaterArtifacts']
assert 'updater' not in config.get('plugins', {})
client = (root / 'src-tauri/src/llm_client.rs').read_text(encoding="utf-8")
assert 'Err("Cloud inference is disabled in Local Dictation".to_string())' in client
settings = (root / 'src-tauri/src/settings.rs').read_text(encoding="utf-8")
assert 'fn default_history_limit() -> usize {\n    0' in settings
assert 'pub fn update_checks_forced_disabled() -> bool {\n    true' in settings
assert 'local_cleanup::clean(app, &final_text).await' in (root/'src-tauri/src/actions.rs').read_text(encoding="utf-8")
assert '.plugin(tauri_plugin_updater::Builder' not in (root/'src-tauri/src/lib.rs').read_text(encoding="utf-8")
print('Local processing and privacy defaults verified.')
