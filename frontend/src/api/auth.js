import request from './request'

/**
 * 用户注册
 */
export function registerApi(data) {
  return request({
    url: '/auth/register',
    method: 'post',
    data
  })
}

/**
 * 用户登录
 */
export function loginApi(data) {
  return request({
    url: '/auth/login',
    method: 'post',
    data
  })
}

/**
 * 用户退出
 */
export function logoutApi() {
  return request({
    url: '/auth/logout',
    method: 'post'
  })
}

