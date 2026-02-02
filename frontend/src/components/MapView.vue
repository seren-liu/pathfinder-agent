<template>
  <div class="map-container">
    <div id="map" :style="{ height: mapHeight }"></div>
    
    <div class="map-controls">
      <el-button @click="fitAllMarkers" size="small" type="primary">
        <el-icon><Location /></el-icon> Show All
      </el-button>
      <el-button @click="resetView" size="small">
        <el-icon><RefreshRight /></el-icon> Reset
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, onBeforeUnmount } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { Location, RefreshRight } from '@element-plus/icons-vue'

// 修复 Leaflet 默认图标路径问题
import icon from 'leaflet/dist/images/marker-icon.png'
import iconShadow from 'leaflet/dist/images/marker-shadow.png'
import iconRetina from 'leaflet/dist/images/marker-icon-2x.png'

delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({
  iconRetinaUrl: iconRetina,
  iconUrl: icon,
  shadowUrl: iconShadow,
})

const props = defineProps({
  activities: { type: Array, default: () => [] },
  center: { type: Object, default: () => ({ lat: -28.6418, lng: 153.6123 }) },
  zoom: { type: Number, default: 12 },
  mapHeight: { type: String, default: '400px' },
  highlightDay: { type: Number, default: null }
})

const map = ref(null)
const markers = ref([])

onMounted(() => {
  // 使用 nextTick 确保 DOM 已经渲染
  setTimeout(() => {
    initMap()
    updateMarkers()
  }, 100)
})

const initMap = () => {
  try {
    // 检查容器是否存在
    const container = document.getElementById('map')
    if (!container) {
      console.error('Map container not found')
      return
    }
    
    // 如果地图已经初始化，先销毁
    if (map.value) {
      map.value.remove()
    }
    
    map.value = L.map('map', {
      center: [props.center.lat, props.center.lng],
      zoom: props.zoom,
      zoomControl: true,
      scrollWheelZoom: true
    })
    
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap contributors',
      maxZoom: 19
    }).addTo(map.value)
  } catch (error) {
    console.error('Failed to initialize map:', error)
  }
}

const updateMarkers = () => {
  // 检查地图是否已初始化
  if (!map.value) {
    console.warn('Map not initialized yet')
    return
  }
  
  markers.value.forEach(marker => map.value.removeLayer(marker))
  markers.value = []
  
  props.activities.forEach((activity) => {
    if (activity.latitude && activity.longitude) {
      const iconColor = getActivityColor(activity.activityType)
      const isHighlighted = props.highlightDay === null || activity.dayNumber === props.highlightDay
      
      const marker = L.marker([activity.latitude, activity.longitude], {
        icon: createCustomIcon(iconColor, isHighlighted),
        opacity: isHighlighted ? 1 : 0.3
      })
        .addTo(map.value)
        .bindPopup(`
          <div style="min-width: 200px;">
            <strong>${activity.activityName}</strong><br>
            <span style="color: #666;">${activity.activityType}</span><br>
            <span style="color: #409EFF;">${activity.startTime}</span> 
            (${activity.durationMinutes} mins)<br>
            <strong style="color: #67C23A;">$${activity.cost}</strong>
          </div>
        `)
      
      markers.value.push(marker)
    }
  })
  
  if (markers.value.length > 0) {
    fitAllMarkers()
  }
}

const getActivityColor = (type) => {
  const colors = {
    accommodation: '#E6A23C',
    dining: '#F56C6C',
    activity: '#409EFF',
    transportation: '#909399',
    other: '#67C23A'
  }
  return colors[type] || colors.other
}

const createCustomIcon = (color, isHighlighted) => {
  return L.divIcon({
    className: 'custom-marker',
    html: `<div style="
      background-color: ${color};
      width: 24px;
      height: 24px;
      border-radius: 50%;
      border: 3px solid white;
      box-shadow: 0 2px 8px rgba(0,0,0,0.3);
      opacity: ${isHighlighted ? 1 : 0.5};
    "></div>`,
    iconSize: [24, 24],
    iconAnchor: [12, 12]
  })
}

const fitAllMarkers = () => {
  if (markers.value.length > 0) {
    const group = L.featureGroup(markers.value)
    map.value.fitBounds(group.getBounds().pad(0.1))
  }
}

const resetView = () => {
  map.value.setView([props.center.lat, props.center.lng], props.zoom)
}

watch(() => props.activities, () => updateMarkers(), { deep: true })
watch(() => props.highlightDay, () => updateMarkers())

onBeforeUnmount(() => {
  if (map.value) {
    map.value.remove()
  }
})
</script>

<style scoped>
.map-container {
  position: relative;
  width: 100%;
}

#map {
  width: 100%;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.1);
}

.map-controls {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 1000;
  display: flex;
  gap: 8px;
}
</style>
