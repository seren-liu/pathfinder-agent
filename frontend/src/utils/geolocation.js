/**
 * 地理位置工具类
 * 使用浏览器 Geolocation API 获取用户位置
 */

/**
 * 获取用户当前位置（经纬度）
 * @returns {Promise<{latitude: number, longitude: number}>}
 */
export function getCurrentPosition() {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('Geolocation is not supported by your browser'))
      return
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        resolve({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude
        })
      },
      (error) => {
        let errorMessage = 'Unable to retrieve your location'
        
        switch (error.code) {
          case error.PERMISSION_DENIED:
            errorMessage = 'User denied the request for Geolocation'
            break
          case error.POSITION_UNAVAILABLE:
            errorMessage = 'Location information is unavailable'
            break
          case error.TIMEOUT:
            errorMessage = 'The request to get user location timed out'
            break
        }
        
        reject(new Error(errorMessage))
      },
      {
        enableHighAccuracy: false, // 不需要高精度，节省电量
        timeout: 10000, // 10秒超时
        maximumAge: 300000 // 缓存5分钟
      }
    )
  })
}

/**
 * 使用 Nominatim API（OpenStreetMap）反向地理编码
 * 将经纬度转换为地址
 * @param {number} latitude 
 * @param {number} longitude 
 * @returns {Promise<string>} 返回城市名称，格式如 "Sydney, NSW"
 */
export async function reverseGeocode(latitude, longitude) {
  try {
    // 使用 Nominatim API（免费，无需 API key）
    const response = await fetch(
      `https://nominatim.openstreetmap.org/reverse?format=json&lat=${latitude}&lon=${longitude}&addressdetails=1`,
      {
        headers: {
          'Accept': 'application/json',
          'User-Agent': 'TravelAgent/1.0' // Nominatim 要求提供 User-Agent
        }
      }
    )

    if (!response.ok) {
      throw new Error('Geocoding request failed')
    }

    const data = await response.json()
    
    // 提取城市和州信息
    const address = data.address || {}
    const city = address.city || address.town || address.village || address.suburb || ''
    const state = address.state || ''
    
    // 澳大利亚州缩写映射
    const australianStates = {
      'New South Wales': 'NSW',
      'Victoria': 'VIC',
      'Queensland': 'QLD',
      'South Australia': 'SA',
      'Western Australia': 'WA',
      'Tasmania': 'TAS',
      'Northern Territory': 'NT',
      'Australian Capital Territory': 'ACT'
    }
    
    // 如果是澳大利亚，使用缩写
    const stateCode = australianStates[state] || state
    
    // 返回格式：城市, 州
    if (city && stateCode) {
      return `${city}, ${stateCode}`
    } else if (city) {
      return city
    } else {
      return 'Unknown Location'
    }
  } catch (error) {
    console.error('Reverse geocoding failed:', error)
    throw error
  }
}

/**
 * 获取用户位置并转换为地址
 * 一步完成：获取经纬度 → 反向地理编码
 * @returns {Promise<string>} 返回地址字符串，如 "Sydney, NSW"
 */
export async function getLocationAddress() {
  try {
    // 1. 获取经纬度
    const coords = await getCurrentPosition()
    
    // 2. 反向地理编码
    const address = await reverseGeocode(coords.latitude, coords.longitude)
    
    return address
  } catch (error) {
    console.error('Failed to get location address:', error)
    throw error
  }
}

/**
 * 检查浏览器是否支持 Geolocation
 * @returns {boolean}
 */
export function isGeolocationSupported() {
  return 'geolocation' in navigator
}

