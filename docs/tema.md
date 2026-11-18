# Token Tema Molina (core-ui)

- Dasar: `molina_bg #0E1114`, `molina_surface #171B20`, teks `molina_on_surface #E6EAEE`.
- Terminal: teal `#00BFA5`, tipografi monospace (`TextAppearance.Molina.Mono`).
- Aksen hijau merek (token `molina_brand_*`, dulu `molina_cinema_*`; ala Linux Mint): latar charcoal `#1E1E1E`, panel kontrol `#2F2F2F` (garis `#3C3C3C`), aksen hijau mint `#87CF3E`, wadah `#2D4012`, sekunder merah `#E53935`.
  Catatan: `Theme.Molina.App` memakai token `molina_brand_*` sebagai warna sekunder.
- Bentuk: sudut 12dp. Spasi: `molina_space_xs..xl` (4/8/16/24/32dp).
- Tema per tab: `Theme.Molina.Brand`, `Theme.Molina.Terminal`; cangkang: `Theme.Molina.App`.
- Pakai di Fragment: `inflater.cloneInContext(requireContext().molinaThemed(R.style.Theme_Molina_Brand))`.
- Tombol ekstra terminal: panel `molina_terminal_container`; kode warna RGB `molina_key_red #FF5252` (ESC, CLEAR), `molina_key_green #00E676` (PASTE, COPY), `molina_key_blue #448AFF` (HOME, panah); aksen teal untuk TAB, menu, ENTER; CTRL/ALT menyala teal saat aktif. Tinggi tombol: `KEY_HEIGHT_DP` di `ExtraKeysView`.
- Ikon pohon (gaya Mint-Y): palet ada di `TreeIconPalette` dan jenis berkas di `FileKind` (:core-ui). Dipakai `TreeIconView` (View: Terminal dan Berkas) serta `MintTreeIcons` (Compose: :code). Mengubah warna cukup di satu tempat; bentuk gambar masih ditulis per renderer.
