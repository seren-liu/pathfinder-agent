/**
 * 地图自动刷新 Composable
 * 用于在地理编码完成后自动刷新地图
 */
import { ref, onUnmounted } from 'vue'
import { getTripById } from '@/api/trips'

export function useMapAutoRefresh(tripId, onUpdate) {
  const isPolling = ref(false)
  const pollInterval = ref(null)
  const checkCount = ref(0)
  const maxChecks = 30 // 最多检查 30 次（30 秒）

  /**
   * 检查坐标是否已更新
   */
  const checkCoordinatesUpdated = async () => {
    try {
      const response = await getTripById(tripId.value || tripId)
      const trip = response.data
      
      if (!trip || !trip.days) return false

      // 检查是否有活动有坐标
      const hasCoordinates = trip.days.some(day => 
        day.activities && day.activities.some(activity => 
          activity.latitude && activity.longitude
        )
      )

      // 计算有坐标的活动数量
      let coordCount = 0
      let totalCount = 0
      
      trip.days.forEach(day => {
        if (day.activities) {
          day.activities.forEach(activity => {
            totalCount++
            if (activity.latitude && activity.longitude) {
              coordCount++
            }
          })
        }
      })

      

      // 如果所有活动都有坐标，或者已经有一半以上有坐标
      if (coordCount > 0 && coordCount >= totalCount * 0.5) {
        
        return true
      }

      return false
    } catch (error) {
      console.error('Failed to check coordinates:', error)
      return false
    }
  }

  /**
   * 开始轮询
   */
  const startPolling = () => {
    if (isPolling.value) return

    
    isPolling.value = true
    checkCount.value = 0

    pollInterval.value = setInterval(async () => {
      checkCount.value++

      // 超过最大检查次数，停止轮询
      if (checkCount.value > maxChecks) {
        
        stopPolling()
        return
      }

      const updated = await checkCoordinatesUpdated()
      
      if (updated) {
        // 坐标已更新，通知父组件刷新
        if (onUpdate) {
          onUpdate()
        }
        stopPolling()
      }
    }, 2000) // 每 2 秒检查一次
  }

  /**
   * 停止轮询
   */
  const stopPolling = () => {
    if (pollInterval.value) {
      clearInterval(pollInterval.value)
      pollInterval.value = null
    }
    isPolling.value = false
    
  }

  // 组件卸载时停止轮询
  onUnmounted(() => {
    stopPolling()
  })

  return {
    isPolling,
    startPolling,
    stopPolling
  }
}
