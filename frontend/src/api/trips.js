import request from './request'

/**
 * 创建行程
 */
export function createTrip(data) {
  return request({
    url: '/trips',
    method: 'post',
    data
  })
}

/**
 * 获取完整行程
 */
export function getTripById(tripId) {
  return request({
    url: `/trips/${tripId}`,
    method: 'get'
  })
}

/**
 * 生成行程（异步）
 */
export function generateItinerary(data) {
  return request({
    url: '/trips/generate',
    method: 'post',
    data
  })
}

/**
 * 获取行程生成状态
 */
export function getTripStatus(tripId) {
  return request({
    url: `/trips/${tripId}/status`,
    method: 'get'
  })
}

/**
 * 获取用户最新的行程 ID
 */
export function getLatestTrip(userId) {
  return request({
    url: `/trips/users/${userId}/latest`,
    method: 'get'
  })
}

/**
 * 获取用户的所有行程列表
 */
export function getUserTrips(userId) {
  return request({
    url: `/trips/users/${userId}`,
    method: 'get'
  })
}
