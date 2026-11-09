# GLib 0.18 compatibility security backport

This is the crates.io glib 0.18.5 source, with its original MIT license retained.

Tauri's GTK3 backend requires the 0.18 API. `src/variant_iter.rs` backports the upstream fix from https://github.com/gtk-rs/gtk-rs-core/pull/1343: the C out-argument must be `&mut p`, and `p` must be declared mutable. This fixes RUSTSEC-2024-0429 without replacing GTK3's dependency with an incompatible GLib major version.

The Linux CI job runs the real variant iterator tests with optimization enabled. Remove this patch when the compatible dependency line contains the fix or the GUI stack migrates.
