import request from './request'

/**
 * 设置用户个人资料
 */
export function setupProfileApi(userId, data) {
  return request({
    url: `/users/${userId}/profile`,
    method: 'post',
    data
  })
}

/**
 * 获取用户详情
 */
export function getUserDetailApi(userId) {
  return request({
    url: `/users/${userId}/profile`,
    method: 'get'
  })
}

/**
 * 获取当前用户信息
 */
export function getCurrentUserApi() {
  return request({
    url: '/users/me',
    method: 'get'
  })
}

