<template>
  <div class="maplibre-container">
    <div ref="mapContainer" :style="{ height: mapHeight }" class="map"></div>
    
    <!-- 控制按钮 -->
    <div class="map-controls">
      <el-button 
        @click="fitBounds" 
        size="small" 
        type="primary"
        :icon="Location"
      >
        Fit All
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import { Location, Odometer, Clock } from '@element-plus/icons-vue'

const props = defineProps({
  activities: { type: Array, default: () => [] },
  center: { type: Object, default: () => ({ lat: -28.6418, lng: 153.6123 }) },
  zoom: { type: Number, default: 12 },
  mapHeight: { type: String, default: '400px' },
  highlightDay: { type: Number, default: null },
  showRoutesDefault: { type: Boolean, default: true }
})

const mapContainer = ref(null)
const map = ref(null)
const markers = ref([])

const apiKey = import.meta.env.VITE_GEOAPIFY_API_KEY

// ========== 初始化地图 ==========
const initMap = () => {
  if (!mapContainer.value) return
  
  // 调试：检查 API key
  console.log('Geoapify API Key:', apiKey)
  
  if (!apiKey) {
    console.error('Geoapify API key is missing! Please check .env file.')
    return
  }
  
  // 使用免费的 OpenStreetMap 瓦片（不需要 API key）
  const osmStyle = {
    version: 8,
    sources: {
      'osm': {
        type: 'raster',
        tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
        tileSize: 256,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
      }
    },
    layers: [{
      id: 'osm',
      type: 'raster',
      source: 'osm',
      minzoom: 0,
      maxzoom: 19
    }]
  }
  
  console.log('Using OpenStreetMap tiles (free, no API key required)')
  
  try {
    map.value = new maplibregl.Map({
      container: mapContainer.value,
      style: osmStyle,
      center: [props.center.lng, props.center.lat],
      zoom: props.zoom,
      attributionControl: true
    })
    
    map.value.addControl(new maplibregl.NavigationControl(), 'top-right')
    
    map.value.on('load', () => {
      console.log('Map loaded successfully')
      addMarkers()
    })
    
    map.value.on('error', (e) => {
      console.error('Map error:', e)
    })
  } catch (error) {
    console.error('Failed to initialize map:', error)
  }
}

// ========== 添加标记 ==========
const addMarkers = () => {
  markers.value.forEach(marker => marker.remove())
  markers.value = []
  
  const validActivities = props.activities.filter(activity => 
    activity.latitude && 
    activity.longitude &&
    activity.activityType !== 'transportation'  // ✅ 过滤掉 transportation
  )
  
  validActivities.forEach((activity, index) => {
    // ✅ 解决坐标重叠问题：为相同坐标添加微小偏移
    let lng = Number(activity.longitude)
    let lat = Number(activity.latitude)
    
    // 检查是否与之前的标记坐标重复
    const offset = 0.0005 // 约50米的偏移量
    const existingMarker = markers.value.find(m => {
      const mLngLat = m.getLngLat()
      return Math.abs(mLngLat.lng - lng) < 0.00001 && 
             Math.abs(mLngLat.lat - lat) < 0.00001
    })
    
    if (existingMarker) {
      // 如果坐标重复，添加螺旋偏移
      const angle = (markers.value.length % 8) * (Math.PI / 4) // 每45度一个标记
      const distance = offset * (1 + Math.floor(markers.value.length / 8))
      lng += distance * Math.cos(angle)
      lat += distance * Math.sin(angle)
    }
    
    const el = document.createElement('div')
    el.className = 'custom-marker'
    el.style.width = '32px'
    el.style.height = '32px'
    el.style.borderRadius = '50%'
    el.style.backgroundColor = getActivityColor(activity.activityType)
    el.style.border = '3px solid white'
    el.style.boxShadow = '0 2px 8px rgba(0,0,0,0.3)'
    el.style.opacity = '1'
    el.style.cursor = 'pointer'
    el.style.display = 'flex'
    el.style.alignItems = 'center'
    el.style.justifyContent = 'center'
    el.style.color = 'white'
    el.style.fontWeight = 'bold'
    el.style.fontSize = '14px'
    el.textContent = index + 1
    
    const popup = new maplibregl.Popup({ offset: 25 }).setHTML(`
      <div style="min-width: 200px; padding: 8px;">
        <h3 style="margin: 0 0 8px 0; color: #303133; font-size: 16px;">
          ${activity.activityName}
        </h3>
        <div style="color: #606266; font-size: 14px; line-height: 1.6;">
          <p style="margin: 4px 0;">
            <strong>Type:</strong> ${activity.activityType}
          </p>
          <p style="margin: 4px 0;">
            <strong>Time:</strong> ${activity.startTime} (${activity.durationMinutes} mins)
          </p>
          <p style="margin: 4px 0;">
            <strong>Location:</strong> ${activity.location || 'N/A'}
          </p>
          <p style="margin: 4px 0;">
            <strong>Cost:</strong> <span style="color: #67C23A; font-weight: bold;">$${activity.cost || 0}</span>
          </p>
        </div>
      </div>
    `)
    
    const marker = new maplibregl.Marker({ element: el })
      .setLngLat([lng, lat])  // ✅ 使用偏移后的坐标
      .setPopup(popup)
      .addTo(map.value)
    
    markers.value.push(marker)
  })
  
  if (markers.value.length > 0) {
    fitBounds()
  }
}

// ========== 自动适应 ==========
const fitBounds = () => {
  if (!map.value || markers.value.length === 0) return
  
  const validActivities = props.activities.filter(activity => 
    activity.latitude && 
    activity.longitude &&
    activity.activityType !== 'transportation'  // ✅ 过滤掉 transportation
  )
  
  if (validActivities.length === 0) return
  
  const bounds = new maplibregl.LngLatBounds()
  validActivities.forEach(activity => {
    bounds.extend([activity.longitude, activity.latitude])
  })
  
  map.value.fitBounds(bounds, {
    padding: { top: 50, bottom: 50, left: 50, right: 50 },
    maxZoom: 15
  })
}

// ========== 获取颜色 ==========
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

// ========== 生命周期 ==========
onMounted(async () => {
  await nextTick()
  initMap()
})

onBeforeUnmount(() => {
  if (map.value) {
    map.value.remove()
  }
})

// ========== 监听变化 ==========
watch(() => props.activities, () => {
  if (map.value && map.value.loaded()) {
    addMarkers()
  }
}, { deep: true })
</script>

<style scoped>
.maplibre-container {
  position: relative;
  width: 100%;
}

.map {
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

.route-info {
  position: absolute;
  bottom: 20px;
  left: 20px;
  z-index: 1000;
  max-width: 250px;
}

.route-stats {
  display: flex;
  gap: 20px;
}

.stat {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

:deep(.maplibregl-popup-content) {
  padding: 0;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

:deep(.maplibregl-popup-close-button) {
  font-size: 20px;
  padding: 4px 8px;
}
</style>
