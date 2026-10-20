<template>
  <main class="not-found-page">
    <section class="not-found-card" aria-labelledby="not-found-title">
      <div class="error-code" aria-hidden="true">404</div>
      <h1 id="not-found-title">页面不存在</h1>
      <p>请检查访问地址，或返回数据看板继续操作。</p>
      <router-link to="/index" class="return-link">返回数据看板</router-link>
    </section>
  </main>
</template>

<style scoped>
.not-found-page{min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px;background:#f8fbff;color:#303133;text-align:center}
.not-found-card{max-width:540px;width:100%}
.error-code{font-size:clamp(88px,16vw,140px);line-height:1;font-weight:700;color:#577bef;letter-spacing:8px}
h1{font-size:24px;margin:24px 0 12px}
p{font-size:14px;line-height:1.8;color:#606266;margin:0 0 28px}
.return-link{display:inline-block;padding:10px 22px;background:#577bef;color:#fff;border-radius:4px;font-size:14px;text-decoration:none}
</style>
