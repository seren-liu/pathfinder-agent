<!-- src/views/Welcome.vue -->
<template>
  <AppLayout>
    <div class="welcome-container">
      <!-- Hero Section -->
      <div class="hero-section">
        <h1 class="hero-title">
          Welcome to Pathfinder Agent
        </h1>
        <p class="hero-subtitle">
          Let AI plan your perfect journey
        </p>
        
        <div class="cta-buttons">
          <el-button type="primary" size="large" round @click="startJourney">
            <el-icon class="button-icon"><Promotion /></el-icon>
            Start My Journey
          </el-button>
          
          <el-button size="large" round @click="goToLogin">
            Already have an account? Log In
          </el-button>
        </div>
      </div>

      <!-- How It Works - 横向4列 -->
      <div class="how-it-works-section">
        <h2 class="section-title">How It Works</h2>
        
        <div class="steps-grid">
          <div class="step-card" v-for="(step, index) in steps" :key="index">
            <div class="step-number">{{ index + 1 }}</div>
            <el-icon :size="48" class="step-icon">
              <component :is="step.icon" />
            </el-icon>
            <h3 class="step-title">{{ step.title }}</h3>
            <p class="step-description">{{ step.description }}</p>
          </div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { useRouter } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import {
  Promotion,
  UserFilled,
  Search,
  Document,
  Place
} from '@element-plus/icons-vue'

const router = useRouter()

const steps = [
  {
    icon: UserFilled,
    title: 'Create Profile',
    description: 'Tell us about your travel preferences'
  },
  {
    icon: Search,
    title: 'Describe Your Dream Trip',
    description: 'AI analyzes and finds perfect destinations'
  },
  {
    icon: Document,
    title: 'Get Your Itinerary',
    description: 'Receive a personalized day-by-day plan'
  },
  {
    icon: Place,
    title: 'Enjoy Your Journey',
    description: 'Travel with confidence and flexibility'
  }
]

const startJourney = () => {
  console.log('Start Journey clicked, navigating to /register')
  router.push('/register').catch(err => {
    console.error('Navigation error:', err)
  })
}

const goToLogin = () => {
  console.log('Login clicked, navigating to /login')
  router.push('/login').catch(err => {
    console.error('Navigation error:', err)
  })
}
</script>

<style scoped>
.welcome-container {
  width: 100%;
  max-width: 100%;
}

/* Hero Section */
.hero-section {
  text-align: center;
  padding: 60px 50px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.05) 0%, rgba(118, 75, 162, 0.05) 100%);
  border-radius: 16px;
  margin-bottom: 50px;
}

.hero-title {
  font-size: 48px;
  font-weight: 800;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 20px;
  line-height: 1.2;
}

.hero-subtitle {
  font-size: 22px;
  color: #606266;
  margin-bottom: 40px;
  font-weight: 300;
}

.cta-buttons {
  display: flex;
  gap: 24px;
  justify-content: center;
  flex-wrap: wrap;
}

.cta-buttons .el-button {
  padding: 14px 36px;
  font-size: 16px;
  font-weight: 600;
  transition: all 0.3s ease;
}

.cta-buttons .el-button:hover {
  transform: translateY(-2px);
}

.button-icon {
  margin-right: 8px;
}

/* How It Works - 横向4列网格 */
.section-title {
  text-align: center;
  font-size: 36px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 48px;
}
.how-it-works-section {
  margin-bottom: 60px;
}

.steps-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr); /* 强制4列 */
  gap: 28px;
  width: 100%;
}

.step-card {
  text-align: center;
  padding: 35px 18px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.05) 0%, rgba(118, 75, 162, 0.05) 100%);
  border-radius: 16px;
  transition: all 0.3s ease;
  position: relative;
  min-height: 260px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
}

.step-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 12px 24px rgba(102, 126, 234, 0.15);
}

.step-number {
  position: absolute;
  top: -18px;
  left: 50%;
  transform: translateX(-50%);
  width: 44px;
  height: 44px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.step-icon {
  color: #667eea;
  margin: 28px 0 16px;
}

.step-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
}

.step-description {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
}

/* 平板响应式 (768px - 1200px) */
@media (max-width: 1200px) {
  .steps-grid {
    grid-template-columns: repeat(2, 1fr); /* 2列 */
  }
}

/* 手机响应式 (<768px) */
@media (max-width: 768px) {
  .hero-section {
    padding: 60px 20px;
  }
  
  .hero-title {
    font-size: 36px;
  }
  
  .hero-subtitle {
    font-size: 20px;
  }
  
  .cta-buttons {
    flex-direction: column;
    align-items: stretch;
  }
  
  .cta-buttons .el-button {
    width: 100%;
  }
  
  .section-title {
    font-size: 32px;
  }
  
  .steps-grid {
    grid-template-columns: 1fr; /* 单列 */
    gap: 40px;
  }
  
  .step-card {
    margin-top: 24px;
  }
}

/* 大屏优化 (>1400px) */
@media (min-width: 1400px) {
  .hero-title {
    font-size: 64px;
  }
  
  .hero-subtitle {
    font-size: 32px;
  }
  
  .section-title {
    font-size: 48px;
  }
  
  .steps-grid {
    gap: 40px;
  }
}
</style>