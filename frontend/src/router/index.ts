import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: MainLayout,
      redirect: '/analyze',
      children: [
        {
          path: 'analyze',
          name: 'analyze',
          component: () => import('@/views/AnalyzeView.vue'),
          meta: { title: 'SQL 分析' },
        },
        {
          path: 'datasource',
          name: 'datasource',
          component: () => import('@/views/DataSourceView.vue'),
          meta: { title: '数据源管理' },
        },
        {
          path: 'history',
          name: 'history',
          component: () => import('@/views/HistoryView.vue'),
          meta: { title: '分析历史' },
        },
      ],
    },
  ],
})

export default router
