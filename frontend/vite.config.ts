import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    watch: {
      // 临时目录会导致 Windows 上 EBUSY；public 里是静态素材（含 4MB 秋景），也不需要热更新
      ignored: ["**/*.tmp", "**/.*.tmpdir/**", "**/*.tmpdir/**", "**/public/**"],
    },
  },
});
