# syntax=docker/dockerfile:1

# ---- build stage: compile the Vite/React app to static assets ----
FROM node:20-alpine AS build
WORKDIR /app
# Copy manifests first for a cached dependency layer.
COPY package.json package-lock.json* ./
RUN npm ci
COPY . .
# `npm run build` = `tsc --noEmit && vite build` (type-check then bundle).
RUN npm run build

# ---- serve stage: nginx serves the build + reverse-proxies /api -> backend ----
FROM nginx:alpine
# Replace the stock server block with ours (SPA fallback + /api proxy, no CORS).
RUN rm -f /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/app.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
