# Build the learner app (learn/) into static files.
FROM node:24-alpine AS learn
WORKDIR /app
COPY learn/package.json learn/package-lock.json ./learn/
RUN npm ci --prefix learn --no-audit --no-fund
COPY data ./data
COPY learn ./learn
RUN npm run build --prefix learn

FROM node:24-alpine
WORKDIR /app
COPY package*.json ./
RUN npm install --omit=dev --ignore-scripts --no-audit --no-fund
COPY --chown=node:node . .
COPY --from=learn --chown=node:node /app/learn/dist ./learn/dist
USER node
ENV NODE_ENV=production
EXPOSE 3000
CMD ["npm", "start"]
