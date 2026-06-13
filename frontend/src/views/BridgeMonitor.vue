<template>
  <div class="h-full flex flex-col">
    <div class="bg-white border-b border-stone-200 px-6 py-3 flex items-center justify-between">
      <div>
        <h1 class="text-xl font-bold text-stone-800">三维监测视图</h1>
        <p class="text-xs text-stone-500">Three.js + LOD + 模型简化</p>
      </div>
      <div class="flex items-center gap-3 text-sm text-stone-600">
        <span>点击监测点查看趋势</span>
      </div>
    </div>
    <div class="flex-1 relative">
      <BridgeViewer />
    </div>
  </div>
</template>

<script setup>
import BridgeViewer from '@/components/BridgeViewer.vue'
</script>
