<template>
  <div id="app">
    <router-view v-slot="{ Component }">
      <keep-alive>
        <component :is="Component" />
      </keep-alive>
    </router-view>
  </div>
</template>

<script setup>
</script>

<style scoped>
#app {
  min-height: 100vh;
  background: #f7f8fa;
}
</style>
