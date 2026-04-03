FROM node:22-alpine AS build
WORKDIR /app

# Copy workspace root (package.json + all local packages)
COPY package.json package-lock.json tsconfig.json ./
COPY model/ ./model/
COPY parser/ ./parser/
COPY ui-components/ ./ui-components/
COPY report-viewer/ ./report-viewer/

# Install all workspace dependencies
RUN npm ci --ignore-scripts

# Build the report-viewer app (skip type-check — workspace tsconfig path issues in Docker)
RUN cd report-viewer && npx vite build

FROM nginx:alpine
COPY --from=build /app/report-viewer/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
