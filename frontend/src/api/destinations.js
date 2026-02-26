import request from './request'

/**
 * 保存目的地到数据库
 */
export function saveDestination(data) {
  return request({
    url: '/destinations',
    method: 'post',
    data
  })
}

/**
 * 获取目的地详情
 */
export function getDestinationById(id) {
  return request({
    url: `/destinations/${id}`,
    method: 'get'
  })
}
