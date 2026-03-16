/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: '#2E4036',
        accent: '#CC5833',
        background: '#F2F0E9',
        charcoal: '#1A1A1A',
      },
      fontFamily: {
        sans: ['Plus Jakarta Sans', 'sans-serif'],
        outfit: ['Outfit', 'sans-serif'],
        drama: ['Cormorant Garamond', 'serif'],
        data: ['IBM Plex Mono', 'monospace'],
      },
      borderRadius: {
        '2xl': '2rem',
        '3xl': '3rem',
      }
    },
  },
  plugins: [],
}
