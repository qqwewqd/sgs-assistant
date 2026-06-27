<template>
  <van-popup v-model:show="visible" teleport="body" class="image-modal" @click="close">
    <img v-if="image" :src="assetUrl(image.imagePath)" :alt="image.name" />
    <div v-if="image" class="image-modal__name">{{ image.name }}</div>
  </van-popup>
</template>

<script setup>
import { computed } from 'vue'
import { Popup as VanPopup } from 'vant'
import { assetUrl } from '../utils/pathHelper'

const props = defineProps({
  modelValue: Boolean,
  image: Object
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

function close() {
  visible.value = false
}
</script>

<style scoped>
.image-modal {
  display: grid;
  place-items: center;
  width: 100vw;
  height: 100vh;
  background: rgba(255, 239, 214, 0.92);
}

.image-modal img {
  max-width: 94vw;
  max-height: 88vh;
  border-radius: 8px;
  object-fit: contain;
  box-shadow: 0 20px 70px rgba(73, 39, 17, 0.38);
}

.image-modal__name {
  position: fixed;
  left: 16px;
  right: 16px;
  bottom: calc(18px + env(safe-area-inset-bottom));
  min-height: 38px;
  display: grid;
  place-items: center;
  color: var(--warm-primary-strong);
  font-weight: 900;
  text-align: center;
}
</style>
