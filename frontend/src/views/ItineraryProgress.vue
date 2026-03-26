<template>
  <AppLayout>
    <section class="progress-scene">
      <div class="ambient ambient-a" aria-hidden="true"></div>
      <div class="ambient ambient-b" aria-hidden="true"></div>
      <div class="ambient ambient-c" aria-hidden="true"></div>

      <el-card class="progress-card" shadow="never">
        <header class="card-header">
          <div class="status-pill">
            <span class="dot"></span>
            AI Itinerary Generation
          </div>
          <h2>Crafting Your Travel Story</h2>
          <p class="subtitle">Real-time planning, optimization, and route tuning in progress.</p>

          <div class="meta-row">
            <div class="meta-chip">Trip #{{ tripId }}</div>
            <div class="meta-chip" :class="connectionClass">{{ connectionLabel }}</div>
            <div class="meta-chip">{{ progress }}% Complete</div>
          </div>
        </header>

        <div class="progress-content">
          <el-progress
            :percentage="progress"
            :status="progressStatus"
            :stroke-width="16"
            :color="progressColor"
          />

          <div class="current-step" role="status" aria-live="polite">
            <el-icon class="step-icon" :size="22">
              <Loading v-if="!hasTerminalState" class="spin" />
              <CircleCheck v-else-if="progressStatus === 'success'" />
              <Warning v-else />
            </el-icon>
            <span class="step-text">{{ currentStep }}</span>
          </div>

          <div class="step-grid">
            <article
              v-for="(step, index) in steps"
              :key="index"
              class="step-card"
              :class="`step-${getStepState(index)}`"
            >
              <div class="step-index">{{ String(index + 1).padStart(2, '0') }}</div>
              <div class="step-main">
                <el-icon
                  class="step-symbol"
                  :class="{ spinning: getStepState(index) === 'active' && !hasTerminalState }"
                >
                  <component :is="getStepIcon(index)" />
                </el-icon>
                <p>{{ step }}</p>
              </div>
            </article>
          </div>

          <div v-if="progress >= 100 && progressStatus === 'success'" class="success-actions">
            <el-button type="primary" size="large" @click="handleViewItinerary">
              View My Itinerary
            </el-button>
          </div>

          <div v-if="progress < 100" class="hint-panel">
            <h3>What is happening now</h3>
            <ul>
              <li>Understanding destination context and preferences</li>
              <li>Composing daily activities with budget constraints</li>
              <li>Optimizing sequence and travel routes</li>
            </ul>
          </div>

          <el-alert
            v-if="error"
            :title="error"
            type="error"
            show-icon
            @close="goBack"
          />
        </div>
      </el-card>
    </section>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading, Clock, CircleCheck, Warning } from '@element-plus/icons-vue'
import { getTripStatus } from '@/api/trips'
import { createTripProgressSocket } from '@/api/tripProgressSocket'
import AppLayout from '@/layouts/AppLayout.vue'

const route = useRoute()
const router = useRouter()

const tripId = ref(route.params.tripId)
const progress = ref(0)
const currentStep = ref('Initializing...')
const error = ref(null)
const progressStatus = ref('')

const steps = [
  'Creating trip record',
  'Analyzing destination',
  'Generating daily plans',
  'Finding activities',
  'Optimizing schedule',
  'Saving itinerary'
]

let pollInterval = null
let socketConnection = null
const hasTerminalState = ref(false)
const wsConnected = ref(false)

const connectionLabel = computed(() => (wsConnected.value ? 'Realtime Sync' : 'Fallback Sync'))
const connectionClass = computed(() => (wsConnected.value ? 'chip-live' : 'chip-fallback'))

const progressColor = computed(() => {
  if (progressStatus.value === 'exception') return '#ef4444'
  if (progressStatus.value === 'success') return '#22c55e'
  return '#0ea5e9'
})

const activeStepIndex = computed(() => {
  if (progress.value >= 100) return steps.length - 1
  const ratio = Math.max(0, progress.value) / 100
  return Math.min(steps.length - 1, Math.floor(ratio * steps.length))
})

const stopPolling = () => {
  if (pollInterval) {
    clearInterval(pollInterval)
    pollInterval = null
  }
}

const startFallbackPolling = () => {
  if (hasTerminalState.value || pollInterval) return
  pollInterval = setInterval(fetchProgress, 2000)
}

const handleStatusPayload = (data, notifySuccess = false) => {
  const rawProgress = data.progress || 0
  progress.value = Math.max(0, Math.min(100, rawProgress))
  currentStep.value = data.currentStep || 'Processing...'
  error.value = null

  if (data.status === 'completed' && !hasTerminalState.value) {
    hasTerminalState.value = true
    progressStatus.value = 'success'
    progress.value = 100
    stopPolling()

    if (notifySuccess) {
      ElMessage.success('Itinerary generated successfully!')
    }

    setTimeout(() => {
      router.push({
        name: 'ItineraryOverview',
        query: { tripId: tripId.value }
      }).catch(err => {
        console.error('Navigation error:', err)
        ElMessage.error('Failed to navigate. Please refresh the page.')
      })
    }, 1000)
    return
  }

  if (data.status === 'failed' && !hasTerminalState.value) {
    hasTerminalState.value = true
    progressStatus.value = 'exception'
    progress.value = 0
    error.value = data.errorMessage || 'Failed to generate itinerary. Please try again.'
    stopPolling()

    ElMessage.error({
      message: error.value,
      duration: 5000
    })
  }
}

const fetchProgress = async () => {
  try {
    const data = await getTripStatus(tripId.value)
    handleStatusPayload(data, false)
  } catch (err) {
    console.error('Failed to fetch progress:', err)
    if (!wsConnected.value && !hasTerminalState.value) {
      startFallbackPolling()
    }
  }
}

const connectProgressSocket = () => {
  socketConnection = createTripProgressSocket(tripId.value, {
    onConnect: () => {
      wsConnected.value = true
      stopPolling()
    },
    onMessage: payload => {
      handleStatusPayload(payload, true)
    },
    onError: err => {
      console.warn('Trip progress websocket error:', err)
      wsConnected.value = false
      if (!hasTerminalState.value) {
        startFallbackPolling()
      }
    },
    onDisconnect: () => {
      wsConnected.value = false
      if (!hasTerminalState.value) {
        startFallbackPolling()
      }
    }
  })
}

const isStepCompleted = index => {
  const stepProgress = ((index + 1) / steps.length) * 100
  return progress.value >= stepProgress
}

const getStepState = index => {
  if (isStepCompleted(index)) return 'done'
  if (!hasTerminalState.value && index === activeStepIndex.value) return 'active'
  return 'pending'
}

const getStepIcon = index => {
  const state = getStepState(index)
  if (state === 'done') return CircleCheck
  if (state === 'active') return Loading
  return Clock
}

const handleViewItinerary = () => {
  router.push({
    name: 'ItineraryOverview',
    query: { tripId: tripId.value }
  }).catch(err => {
    console.error('Manual navigation error:', err)
    ElMessage.error('Navigation failed: ' + err.message)
  })
}

const goBack = () => {
  router.push('/plan/destinations')
}

onMounted(() => {
  if (!tripId.value) {
    ElMessage.error('Invalid trip ID')
    goBack()
    return
  }

  fetchProgress()
  connectProgressSocket()
})

onUnmounted(() => {
  stopPolling()
  if (socketConnection) {
    socketConnection.disconnect().catch(() => {})
    socketConnection = null
  }
})
</script>

<style scoped>
.progress-scene {
  --bg-deep: #0b1220;
  --bg-mid: #13233d;
  --bg-soft: #1d4d7a;
  --surface: rgba(255, 255, 255, 0.78);
  --surface-border: rgba(255, 255, 255, 0.36);
  --text-primary: #0f172a;
  --text-secondary: #334155;
  --brand: #0ea5e9;
  --brand-strong: #0369a1;
  --ok: #16a34a;
  --warn: #f59e0b;

  position: relative;
  overflow: hidden;
  max-width: 980px;
  margin: 28px auto;
  padding: 28px 18px;
  border-radius: 28px;
  background: radial-gradient(circle at 15% 10%, rgba(56, 189, 248, 0.28), transparent 35%),
    radial-gradient(circle at 85% 12%, rgba(99, 102, 241, 0.2), transparent 38%),
    linear-gradient(140deg, var(--bg-deep), var(--bg-mid) 55%, var(--bg-soft));
}

.ambient {
  position: absolute;
  border-radius: 999px;
  filter: blur(40px);
  pointer-events: none;
}

.ambient-a {
  width: 180px;
  height: 180px;
  background: rgba(14, 165, 233, 0.3);
  top: -30px;
  left: -20px;
}

.ambient-b {
  width: 220px;
  height: 220px;
  background: rgba(56, 189, 248, 0.22);
  right: -40px;
  top: 12%;
}

.ambient-c {
  width: 170px;
  height: 170px;
  background: rgba(125, 211, 252, 0.2);
  left: 40%;
  bottom: -70px;
}

.progress-card {
  position: relative;
  border: 1px solid var(--surface-border);
  border-radius: 24px;
  background: var(--surface);
  backdrop-filter: blur(12px);
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.24);
}

.card-header {
  text-align: center;
  padding: 2px 10px 0;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid rgba(14, 165, 233, 0.3);
  background: rgba(224, 242, 254, 0.9);
  color: var(--brand-strong);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.03em;
  text-transform: uppercase;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #22c55e;
}

.card-header h2 {
  margin: 14px 0 8px;
  color: var(--text-primary);
  font-size: clamp(1.6rem, 4vw, 2.2rem);
  line-height: 1.2;
}

.subtitle {
  margin: 0;
  color: var(--text-secondary);
  font-size: 0.98rem;
  line-height: 1.5;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin-top: 18px;
}

.meta-chip {
  min-height: 34px;
  padding: 7px 12px;
  border-radius: 999px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: rgba(255, 255, 255, 0.9);
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.chip-live {
  border-color: rgba(22, 163, 74, 0.35);
  background: rgba(220, 252, 231, 0.88);
  color: #166534;
}

.chip-fallback {
  border-color: rgba(245, 158, 11, 0.35);
  background: rgba(254, 249, 195, 0.88);
  color: #92400e;
}

.progress-content {
  padding: 20px 8px 6px;
}

.current-step {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin: 20px 0 18px;
  color: var(--brand-strong);
  font-size: 1rem;
  font-weight: 600;
}

.step-icon {
  color: var(--brand-strong);
}

.step-text {
  max-width: 95%;
  text-align: center;
}

.step-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 16px 0 26px;
}

.step-card {
  display: flex;
  gap: 12px;
  align-items: center;
  min-height: 76px;
  padding: 12px;
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  background: rgba(255, 255, 255, 0.92);
  transition: transform 200ms ease, box-shadow 200ms ease, border-color 200ms ease;
}

.step-index {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(226, 232, 240, 0.85);
  color: #334155;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.05em;
}

.step-main {
  display: flex;
  align-items: center;
  gap: 10px;
}

.step-main p {
  margin: 0;
  color: #1e293b;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.45;
}

.step-symbol {
  color: #64748b;
  font-size: 18px;
}

.step-active {
  border-color: rgba(14, 165, 233, 0.45);
  box-shadow: 0 10px 24px rgba(14, 165, 233, 0.18);
  transform: translateY(-1px);
}

.step-active .step-index {
  background: rgba(186, 230, 253, 0.96);
  color: #075985;
}

.step-active .step-symbol,
.step-active .step-main p {
  color: #075985;
}

.step-done {
  border-color: rgba(22, 163, 74, 0.4);
  background: rgba(240, 253, 244, 0.92);
}

.step-done .step-index {
  background: rgba(187, 247, 208, 0.92);
  color: #166534;
}

.step-done .step-symbol,
.step-done .step-main p {
  color: #166534;
}

.success-actions {
  margin: 14px 0 16px;
  text-align: center;
}

.hint-panel {
  border: 1px solid rgba(14, 165, 233, 0.24);
  background: rgba(224, 242, 254, 0.62);
  border-radius: 14px;
  padding: 14px 16px;
  color: #0f172a;
}

.hint-panel h3 {
  margin: 0 0 8px;
  font-size: 14px;
  color: #0c4a6e;
}

.hint-panel ul {
  margin: 0;
  padding-left: 20px;
}

.hint-panel li {
  margin: 6px 0;
  line-height: 1.5;
  color: #0f172a;
  font-size: 13px;
}

.spin,
.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 760px) {
  .progress-scene {
    margin: 12px;
    padding: 16px 10px;
    border-radius: 18px;
  }

  .progress-content {
    padding: 16px 2px 4px;
  }

  .step-grid {
    grid-template-columns: 1fr;
  }

  .card-header h2 {
    font-size: 1.6rem;
  }

  .meta-chip {
    font-size: 12px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .spin,
  .spinning,
  .step-card {
    animation: none !important;
    transition: none !important;
  }
}
</style>
