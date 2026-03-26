import { Client } from '@stomp/stompjs'

function getBrokerUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${protocol}://${window.location.host}/ws`
}

export function createTripProgressSocket(tripId, handlers = {}) {
  const {
    onConnect,
    onMessage,
    onError,
    onDisconnect
  } = handlers

  let subscription = null

  const client = new Client({
    brokerURL: getBrokerUrl(),
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: () => {}
  })

  client.onConnect = () => {
    subscription = client.subscribe(`/topic/trip-progress/${tripId}`, message => {
      try {
        const payload = JSON.parse(message.body)
        onMessage?.(payload)
      } catch (err) {
        onError?.(err)
      }
    })

    onConnect?.()
  }

  client.onStompError = frame => {
    onError?.(new Error(frame.headers['message'] || 'STOMP error'))
  }

  client.onWebSocketError = evt => {
    onError?.(evt instanceof Error ? evt : new Error('WebSocket error'))
  }

  client.onWebSocketClose = () => {
    onDisconnect?.()
  }

  client.activate()

  return {
    disconnect: async () => {
      try {
        subscription?.unsubscribe()
      } catch (e) {
        // ignore
      }
      subscription = null
      await client.deactivate()
    }
  }
}
