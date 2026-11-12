# Token Tema Molina (core-ui)

- Dasar: `molina_bg #0E1114`, `molina_surface #171B20`, teks `molina_on_surface #E6EAEE`.
- Terminal: teal `#00BFA5`, tipografi monospace (`TextAppearance.Molina.Mono`).
- Cinema (Home Theater): amber `#FFB300` di atas hitam pekat `#07090B`, sekunder merah `#E53935`.
- Bentuk: sudut 12dp. Spasi: `molina_space_xs..xl` (4/8/16/24/32dp).
- Tema per tab: `Theme.Molina.Cinema`, `Theme.Molina.Terminal`; cangkang: `Theme.Molina.App`.
- Pakai di Fragment: `inflater.cloneInContext(requireContext().molinaThemed(R.style.Theme_Molina_Cinema))`.
