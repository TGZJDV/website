/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // 音乐主题色（介于蓝色与青色之间）
        surface: '#121212',
        surface2: '#1e1e1e',
        surface3: '#2a2a2a',
        primary: '#0ea5e9',
        primaryDark: '#0284c7',
        accent: '#e91e63',
        text: '#f5f5f5',
        muted: '#a0a0a0',
      },
    },
  },
  plugins: [],
};
