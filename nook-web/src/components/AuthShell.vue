<script setup lang="ts">
import ThemeToggle from './ThemeToggle.vue'

defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<template>
  <main class="auth-shell">
    <div class="auth-bg" aria-hidden="true">
      <span class="orb orb-a" />
      <span class="orb orb-b" />
      <span class="orb orb-c" />
      <span class="grid" />
    </div>

    <div class="theme-slot">
      <ThemeToggle />
    </div>

    <section class="auth-card" role="region" aria-labelledby="auth-title">
      <header class="auth-head">
        <div class="brand">
          <img src="/logo.svg" alt="" class="brand-logo" width="28" height="28" />
          <span class="brand-name">Nook</span>
        </div>
        <h1 id="auth-title" class="auth-title">{{ title }}</h1>
        <p v-if="subtitle" class="auth-sub">{{ subtitle }}</p>
      </header>

      <slot />

      <footer class="auth-foot">
        <slot name="footer" />
      </footer>
    </section>
  </main>
</template>

<style scoped>
.auth-shell {
  position: relative;
  min-height: 100vh;
  min-height: 100dvh;
  display: grid;
  place-items: center;
  padding: 24px 16px;
  overflow: hidden;
  isolation: isolate;
}

.theme-slot {
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 10;
}
@media (max-width: 480px) {
  .theme-slot {
    top: 14px;
    right: 14px;
  }
}

.auth-bg {
  position: absolute;
  inset: 0;
  z-index: -1;
  background: radial-gradient(120% 80% at 50% -20%, #ccfbf1 0%, #f0fdfa 45%, #fff7ed 100%);
}

html.dark .auth-bg {
  background: radial-gradient(120% 80% at 50% -20%, #0f3a36 0%, #042f2e 55%, #021410 100%);
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.5;
  animation: drift 18s ease-in-out infinite;
}
.orb-a {
  width: 380px;
  height: 380px;
  left: -80px;
  top: -60px;
  background: #14b8a6;
}
.orb-b {
  width: 320px;
  height: 320px;
  right: -60px;
  top: 28%;
  background: #fb923c;
  animation-delay: -6s;
}
.orb-c {
  width: 280px;
  height: 280px;
  left: 32%;
  bottom: -90px;
  background: #fbbf24;
  animation-delay: -12s;
}
html.dark .orb {
  opacity: 0.32;
}

.grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(20, 184, 166, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(20, 184, 166, 0.08) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(60% 60% at 50% 40%, #000 30%, transparent 80%);
}
html.dark .grid {
  background-image:
    linear-gradient(rgba(94, 234, 212, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(94, 234, 212, 0.08) 1px, transparent 1px);
}

@keyframes drift {
  0%, 100% { transform: translate3d(0, 0, 0) scale(1); }
  50% { transform: translate3d(20px, -30px, 0) scale(1.08); }
}

.auth-card {
  position: relative;
  width: min(420px, 100%);
  padding: 38px 32px 28px;
  border-radius: var(--r-xl);
  background: var(--nook-surface-raised);
  border: 1px solid var(--nook-surface-border);
  backdrop-filter: blur(24px) saturate(150%);
  -webkit-backdrop-filter: blur(24px) saturate(150%);
  box-shadow: var(--shadow-lg);
  color: var(--nook-text);
  overflow: hidden;
  animation: card-in var(--dur-slow) var(--ease-spring) both;
}
/* 卡片顶部一道高光，强化玻璃边缘 */
.auth-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.7), transparent);
}
html.dark .auth-card::before {
  background: linear-gradient(90deg, transparent, rgba(94, 234, 212, 0.4), transparent);
}
@keyframes card-in {
  from { opacity: 0; transform: translateY(22px) scale(0.97); }
  to { opacity: 1; transform: none; }
}

@media (max-width: 480px) {
  .auth-card {
    padding: 28px 22px 22px;
    border-radius: 22px;
  }
}

.auth-head {
  margin-bottom: 24px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 6px 14px 6px 6px;
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(20, 184, 166, 0.14), rgba(251, 146, 60, 0.14));
  margin-bottom: 18px;
}
.brand-logo {
  display: block;
  border-radius: 8px;
  box-shadow: 0 4px 10px -2px rgba(15, 118, 110, 0.35);
}
.brand-name {
  font-family: var(--nook-font-display);
  font-weight: 600;
  letter-spacing: 0.01em;
  background: var(--nook-gradient-brand);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.auth-title {
  font-family: var(--nook-font-display);
  font-weight: 700;
  font-size: 31px;
  line-height: 1.12;
  letter-spacing: -0.02em;
  margin: 0 0 8px;
  color: var(--nook-text);
}
.auth-sub {
  margin: 0;
  color: var(--nook-text-muted);
  font-size: 14px;
  line-height: 1.55;
}

.auth-foot {
  margin-top: 18px;
  text-align: center;
  font-size: 13.5px;
  color: var(--nook-text-muted);
}
</style>
