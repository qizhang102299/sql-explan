<template>
  <el-empty v-if="!benchmark?.executed" description="未执行基准测试" />
  <div v-else class="bench-tab">
    <div class="stats-row">
      <el-statistic title="提升比例" :value="improvementDisplay" />
      <el-statistic title="交替次数（每侧）" :value="benchmark.iterations ?? '-'" />
    </div>
    <el-alert
      v-if="benchmark.equivalenceWarning"
      :title="benchmark.warningMessage || '优化前后结果可能不一致'"
      type="error"
      :closable="false"
      show-icon
    />
    <el-alert
      v-else-if="benchmark.equivalence"
      :title="equivalenceHint"
      :type="benchmark.equivalence.equivalent ? 'success' : 'warning'"
      :closable="false"
      show-icon
    />
    <div ref="chartRef" class="chart" />
    <el-descriptions :column="2" border size="small" class="detail-desc">
      <el-descriptions-item label="原 SQL 中位数 (ms)">
        {{ benchmark.originalStats?.medianMs ?? '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="优化 SQL 中位数 (ms)">
        {{ benchmark.optimizedStats?.medianMs ?? '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="原 SQL P95 (ms)">
        {{ benchmark.originalStats?.p95Ms ?? '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="优化 SQL P95 (ms)">
        {{ benchmark.optimizedStats?.p95Ms ?? '-' }}
      </el-descriptions-item>
    </el-descriptions>
    <template v-if="benchmark.indexBenefitVerified">
      <div class="section-title">索引收益验证</div>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="建索引前原 SQL 中位数 (ms)">
          {{ benchmark.originalMedianBeforeIndexMs ?? '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="建索引后原 SQL 中位数 (ms)">
          {{ benchmark.originalMedianAfterIndexMs ?? '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="说明">
          {{ benchmark.indexVerificationNote || '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { BenchmarkReport } from '@/types/models'
import { formatPercent } from '@/utils/format'

const props = defineProps<{
  benchmark?: BenchmarkReport
}>()

const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const improvementDisplay = computed(() => {
  const r = props.benchmark?.improvementRatio
  if (r == null) {
    return '-'
  }
  return formatPercent(r)
})

const equivalenceHint = computed(() => {
  const eq = props.benchmark?.equivalence
  if (!eq) {
    return '未返回等价性详情'
  }
  if (eq.equivalent) {
    return `结果一致：行数 ${eq.originalRowCount} / 校验和 ${eq.checksumSkipped ? '（大结果集仅比对行数）' : '已比对'}`
  }
  return (
    eq.message ||
    `结果不一致：原 ${eq.originalRowCount} 行，优化 ${eq.optimizedRowCount} 行`
  )
})

function renderChart(): void {
  if (!chartRef.value || !props.benchmark?.executed) {
    return
  }
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }
  const orig = props.benchmark.originalStats
  const opt = props.benchmark.optimizedStats
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['中位数', 'P95', '最小', '最大'] },
    xAxis: {
      type: 'category',
      data: ['原 SQL', '优化 SQL'],
    },
    yAxis: {
      type: 'value',
      name: '毫秒',
    },
    series: [
      {
        name: '中位数',
        type: 'bar',
        data: [orig?.medianMs ?? 0, opt?.medianMs ?? 0],
        itemStyle: { color: '#409eff' },
      },
      {
        name: 'P95',
        type: 'bar',
        data: [orig?.p95Ms ?? 0, opt?.p95Ms ?? 0],
        itemStyle: { color: '#e6a23c' },
      },
      {
        name: '最小',
        type: 'bar',
        data: [orig?.minMs ?? 0, opt?.minMs ?? 0],
        itemStyle: { color: '#67c23a' },
      },
      {
        name: '最大',
        type: 'bar',
        data: [orig?.maxMs ?? 0, opt?.maxMs ?? 0],
        itemStyle: { color: '#f56c6c' },
      },
    ],
  })
}

function onResize(): void {
  chart?.resize()
}

watch(
  () => props.benchmark,
  () => {
    renderChart()
  },
  { deep: true },
)

onMounted(() => {
  renderChart()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.bench-tab {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stats-row {
  display: flex;
  gap: 48px;
}

.chart {
  width: 100%;
  height: 320px;
  background: #fff;
  border-radius: 4px;
  border: 1px solid #ebeef5;
}

.detail-desc {
  background: #fff;
}
</style>
