import request from './request'

/**
 * 解析旅行意图
 */
export function parseIntentApi(data) {
  return request({
    url: '/ai/parse-intent',
    method: 'post',
    data
  })
}

/**
 * 推荐目的地
 */
export function recommendDestinationsApi(data) {
  return request({
    url: '/ai/destinations/recommend',
    method: 'post',
    data
  })
}

/**
 * 换一批目的地
 */
export function nextBatchApi(data) {
  return request({
    url: '/ai/destinations/recommend/next-batch',
    method: 'post',
    data
  })
}

/**
 * 获取目的地详情
 */
export function getDestinationDetailApi(destinationId) {
  return request({
    url: `/destinations/${destinationId}`,
    method: 'get'
  })
}
