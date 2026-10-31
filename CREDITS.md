<!-- SPDX-License-Identifier: GPL-3.0-only -->
# Credits

Aria builds on the work of other open-source projects. Per module: what was taken and whether it was copied, adapted, or only inspired. The authoritative per-directory record is `docs/provenance.csv`.

| Origin | What | How |
|---|---|---|
| [Meld](https://github.com/FrancescoGrazioso/Meld) (GPL-3.0) | `:innertube` module; Spotify integration design (later) | See `docs/provenance.csv` for copied vs adapted, per directory |
| [Echo Music](https://github.com/EchoMusicApp/Echo-Music) (GPL-3.0) | Room entities; Liquid Glass backdrop code | See `docs/provenance.csv` for copied vs adapted, per directory |
| [Metrolist](https://github.com/MetrolistGroup/Metrolist) | Common ancestor of Meld and Echo | Indirect |
| [Kyant0/backdrop](https://github.com/Kyant0/backdrop) v2.0.0 (Apache-2.0) | Backdrop blur, lens and highlight effects (30 files in `designsystem/.../backdrop`) | Vendored by Echo Music and taken from there with Echo's changes (resolution scale added, shapes support removed); package renamed. Original copyright headers kept; license text in `licenses/Apache-2.0.txt` |
