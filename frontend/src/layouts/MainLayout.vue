<template>
  <el-container class="layout-root">
    <el-aside width="220px" class="layout-aside">
      <div class="brand">SQL 智能分析</div>
      <el-menu
        :default-active="active"
        router
        class="layout-menu"
        background-color="#1f2d3d"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <el-menu-item index="/analyze">
          <el-icon><DataAnalysis /></el-icon>
          <span>SQL 分析</span>
        </el-menu-item>
        <el-menu-item index="/datasource">
          <el-icon><Coin /></el-icon>
          <span>数据源管理</span>
        </el-menu-item>
        <el-menu-item index="/history">
          <el-icon><Clock /></el-icon>
          <span>分析历史</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="layout-header">
        <span class="header-title">{{ pageTitle }}</span>
      </el-header>
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Clock, Coin, DataAnalysis } from '@element-plus/icons-vue'

const route = useRoute()

const active = computed(() => route.path)

const pageTitle = computed(() => {
  const title = route.meta.title
  return typeof title === 'string' ? title : 'SQL 智能分析'
})
</script>

<style scoped>
.layout-root {
  height: 100%;
}

.layout-aside {
  background: #1f2d3d;
  display: flex;
  flex-direction: column;
}

.brand {
  color: #fff;
  font-weight: 700;
  font-size: 16px;
  padding: 20px 16px;
  border-bottom: 1px solid #324157;
}

.layout-menu {
  border-right: none;
  flex: 1;
}

.layout-header {
  display: flex;
  align-items: center;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
  height: 56px;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
}

.layout-main {
  background: #f5f7fa;
  min-height: calc(100vh - 56px);
}
</style>
