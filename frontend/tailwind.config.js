/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: '#1e3a8a',
        secondary: '#0f766e',
        stone: {
          50: '#fafaf9',
          200: '#e7e5e4',
          400: '#a8a29e',
          600: '#57534e',
          800: '#292524'
        }
      },
      fontFamily: {
        sans: ['"PingFang SC"', '"Microsoft YaHei"', 'sans-serif']
      }
    }
  },
  plugins: []
}
