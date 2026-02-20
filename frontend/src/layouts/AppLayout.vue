<!-- src/layouts/AppLayout.vue -->
<template>
  <div class="main-interface">
    <NavMenu />
    <div class="content-area">
      <slot></slot>
    </div>
  </div>
</template>

<script setup>
import NavMenu from '@/components/NavMenu.vue'
</script>

<style scoped>
.main-interface {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  min-height: 100dvh;
  padding: 20px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  width: 100%;
}

.content-area {
  flex: 1;
  min-height: 0;
  margin-top: 20px;
  padding: 50px 70px;
  background-color: white;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  width: 100%;
  overflow-x: hidden;
}

@media (max-width: 768px) {
  .main-interface {
    padding: 10px;
  }
  
  .content-area {
    padding: 20px;
  }
}
</style>
