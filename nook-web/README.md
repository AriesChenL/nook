# nook-web

Nook 的前端模块（Vue 3 + Vite + TypeScript）。

## 技术栈

- Vue 3.5 + TypeScript + Vite 5
- Vue Router 4 / Pinia 2
- Axios（通过 `/api` 代理到网关 `http://localhost:8080`）
- Element Plus（按需引入 + 图标）

## 启动

```bash
pnpm install
pnpm dev          # http://localhost:5173
pnpm build
```

## 目录约定

```
src/
  api/       # 接口封装（http.ts 拦截 token + 401）
  router/    # 路由 + requiresAuth 守卫
  stores/    # Pinia（auth 存 token/user，localStorage 持久化）
  views/     # 页面
```

## 与后端联调

- 开发期 vite 代理 `/api/**` → `http://localhost:8080`（nook-gateway）
- 登录成功后 token 自动存 localStorage，并由 axios 请求拦截器附加 `Authorization: Bearer <token>`
- 401 响应自动清空本地登录态并跳转 `/login`
