import { defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
  input: 'src/generated/openapi-spec.json',
  output: 'src/generated',
  plugins: [
    {
      name: '@hey-api/client-fetch',
      baseUrl: '',
    },
    '@tanstack/react-query',
  ],
});
