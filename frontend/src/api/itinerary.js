import request from '@/utils/request'

/**
 * 获取可编辑的行程
 */
export function getEditableItinerary(tripId) {
  return request({
    url: `/trips/${tripId}/editor`,
    method: 'get'
  })
}

/**
 * 移动活动
 */
export function moveActivity(tripId, itemId, data) {
  return request({
    url: `/trips/${tripId}/editor/activities/${itemId}/move`,
    method: 'post',
    data
  })
}

/**
 * 添加新活动（AI推荐）
 */
export function addActivity(tripId, data) {
  return request({
    url: `/trips/${tripId}/editor/activities`,
    method: 'post',
    data
  })
}

/**
 * 删除活动
 */
export function deleteActivity(tripId, itemId) {
  return request({
    url: `/trips/${tripId}/editor/activities/${itemId}`,
    method: 'delete'
  })
}

/**
 * AI智能优化
 */
export function optimizeItinerary(tripId, data) {
  return request({
    url: `/trips/${tripId}/editor/optimize`,
    method: 'post',
    data
  })
}

/**
 * AI活动替换建议
 */
export function suggestReplacement(tripId, itemId, data) {
  return request({
    url: `/trips/${tripId}/editor/activities/${itemId}/suggest-replace`,
    method: 'post',
    data
  })
}

/**
 * 更新活动
 */
export function updateActivity(tripId, itemId, data) {
  return request({
    url: `/trips/${tripId}/editor/activities/${itemId}`,
    method: 'put',
    data
  })
}

/**
 * 保存编辑
 */
export function saveItineraryEdit(tripId, data) {
  return request({
    url: `/trips/${tripId}/editor/save`,
    method: 'post',
    data
  })
}

/**
 * 添加新的一天
 */
export function addNewDay(tripId, data) {
  return request({
    url: `/trips/${tripId}/editor/days`,
    method: 'post',
    data,
    headers: {
      'Content-Type': 'application/json'
    }
  })
}

/**
 * 更新天的日期
 */
export function updateDayDate(tripId, dayId, date) {
  return request({
    url: `/trips/${tripId}/editor/days/${dayId}`,
    method: 'put',
    data: { date }
  })
}

/**
 * 删除某一天
 */
export function deleteDay(tripId, dayId) {
  return request({
    url: `/trips/${tripId}/editor/days/${dayId}`,
    method: 'delete'
  })
}

