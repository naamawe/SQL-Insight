<template>
  <div class="chart-renderer">
    <div ref="chartRef" class="chart-container"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import type { ChartConfig, VisualizationType } from '@/types/visualization'

interface Props {
  type: VisualizationType
  data: Record<string, unknown>[]
  chartConfig?: ChartConfig
}

const props = defineProps<Props>()

const chartRef = ref<HTMLDivElement>()
let chartInstance: echarts.ECharts | null = null

const initChart = () => {
  if (!chartRef.value || !props.data || props.data.length === 0) return

  // 销毁旧实例
  if (chartInstance) {
    chartInstance.dispose()
  }

  // 创建新实例
  chartInstance = echarts.init(chartRef.value)

  const firstRow = props.data[0]
  if (!firstRow) return

  const xField = props.chartConfig?.xAxis || Object.keys(firstRow)[0]
  const yField = props.chartConfig?.yAxis || Object.keys(firstRow)[1]

  if (!xField || !yField) return

  // 如果是时间字段，按时间排序
  let sortedData = [...props.data]
  if (xField && isTimeField(xField, firstRow[xField])) {
    sortedData.sort((a, b) => {
      const timeA = new Date(a[xField] as string).getTime()
      const timeB = new Date(b[xField] as string).getTime()
      return timeA - timeB
    })
  }

  const xData = sortedData.map(item => String(item[xField]))
  const yData = sortedData.map(item => Number(item[yField]) || 0)

  let option: echarts.EChartsOption = {}

  switch (props.type) {
    case 'BAR_CHART':
      option = {
        title: {
          text: props.chartConfig?.title || '',
          left: 'center',
          top: 10,
          textStyle: {
            fontSize: 18,
            fontWeight: 600,
            color: '#1f2937'
          }
        },
        tooltip: {
          trigger: 'axis',
          backgroundColor: 'rgba(255, 255, 255, 0.95)',
          borderColor: '#e5e7eb',
          borderWidth: 1,
          textStyle: {
            color: '#374151'
          },
          axisPointer: {
            type: 'shadow',
            shadowStyle: {
              color: 'rgba(102, 126, 234, 0.1)'
            }
          }
        },
        grid: {
          left: '5%',
          right: '5%',
          bottom: '15%',
          top: '15%',
          containLabel: true
        },
        xAxis: {
          type: 'category',
          data: xData,
          axisLabel: {
            rotate: xData.length > 10 ? 45 : 0,
            interval: 0,
            fontSize: 11,
            color: '#6b7280',
            formatter: (value: string) => {
              return value.length > 15 ? value.substring(0, 15) + '...' : value
            }
          },
          axisLine: {
            lineStyle: {
              color: '#e5e7eb'
            }
          },
          axisTick: {
            show: false
          }
        },
        yAxis: {
          type: 'value',
          axisLabel: {
            fontSize: 11,
            color: '#6b7280'
          },
          axisLine: {
            show: false
          },
          axisTick: {
            show: false
          },
          splitLine: {
            lineStyle: {
              color: '#f3f4f6',
              type: 'dashed'
            }
          }
        },
        series: [
          {
            type: 'bar',
            data: yData,
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: '#667eea' },
                { offset: 1, color: '#764ba2' }
              ]),
              borderRadius: [8, 8, 0, 0]
            },
            barMaxWidth: 50,
            emphasis: {
              itemStyle: {
                shadowBlur: 10,
                shadowColor: 'rgba(102, 126, 234, 0.5)'
              }
            }
          }
        ]
      }
      break

    case 'LINE_CHART':
    case 'AREA_CHART':
      option = {
        title: {
          text: props.chartConfig?.title || '',
          left: 'center',
          top: 10,
          textStyle: {
            fontSize: 18,
            fontWeight: 600,
            color: '#1f2937'
          }
        },
        tooltip: {
          trigger: 'axis',
          backgroundColor: 'rgba(255, 255, 255, 0.95)',
          borderColor: '#e5e7eb',
          borderWidth: 1,
          textStyle: {
            color: '#374151'
          },
          axisPointer: {
            type: 'cross',
            crossStyle: {
              color: '#999'
            }
          }
        },
        grid: {
          left: '5%',
          right: '5%',
          bottom: '15%',
          top: '15%',
          containLabel: true
        },
        xAxis: {
          type: 'category',
          data: xData,
          boundaryGap: false,
          axisLabel: {
            rotate: xData.length > 10 ? 45 : 0,
            interval: 0,
            fontSize: 11,
            color: '#6b7280',
            formatter: (value: string) => {
              // 如果标签太长，截断并添加省略号
              return value.length > 15 ? value.substring(0, 15) + '...' : value
            }
          },
          axisLine: {
            lineStyle: {
              color: '#e5e7eb'
            }
          },
          axisTick: {
            show: false
          }
        },
        yAxis: {
          type: 'value',
          axisLabel: {
            fontSize: 11,
            color: '#6b7280'
          },
          axisLine: {
            show: false
          },
          axisTick: {
            show: false
          },
          splitLine: {
            lineStyle: {
              color: '#f3f4f6',
              type: 'dashed'
            }
          }
        },
        series: [
          {
            type: 'line',
            data: yData,
            smooth: true,
            symbol: 'circle',
            symbolSize: 8,
            lineStyle: {
              width: 3,
              color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
                { offset: 0, color: '#667eea' },
                { offset: 1, color: '#764ba2' }
              ])
            },
            itemStyle: {
              color: '#667eea',
              borderColor: '#fff',
              borderWidth: 2
            },
            areaStyle: props.type === 'AREA_CHART' ? {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: 'rgba(102, 126, 234, 0.4)' },
                { offset: 1, color: 'rgba(102, 126, 234, 0.05)' }
              ])
            } : undefined,
            emphasis: {
              focus: 'series',
              itemStyle: {
                color: '#667eea',
                borderColor: '#fff',
                borderWidth: 3,
                shadowBlur: 10,
                shadowColor: 'rgba(102, 126, 234, 0.5)'
              }
            }
          }
        ]
      }
      break

    case 'PIE_CHART':
      option = {
        title: {
          text: props.chartConfig?.title || '',
          left: 'center',
          textStyle: {
            fontSize: 16,
            fontWeight: 600
          }
        },
        tooltip: {
          trigger: 'item',
          formatter: '{a} <br/>{b}: {c} ({d}%)'
        },
        legend: {
          orient: 'vertical',
          right: '10%',
          top: 'center'
        },
        series: [
          {
            name: yField,
            type: 'pie',
            radius: ['40%', '70%'],
            center: ['35%', '50%'],
            avoidLabelOverlap: false,
            itemStyle: {
              borderRadius: 10,
              borderColor: '#fff',
              borderWidth: 2
            },
            label: {
              show: false,
              position: 'center'
            },
            emphasis: {
              label: {
                show: true,
                fontSize: 20,
                fontWeight: 'bold'
              }
            },
            labelLine: {
              show: false
            },
            data: props.data.map((item, index) => {
              const xValue = xField ? String(item[xField]) : ''
              const yValue = yField ? (Number(item[yField]) || 0) : 0
              return {
                value: yValue,
                name: xValue,
                itemStyle: {
                  color: getColor(index)
                }
              }
            })
          }
        ]
      }
      break
  }

  chartInstance.setOption(option)
}

const colors = ['#667eea', '#764ba2', '#f093fb', '#4facfe', '#43e97b', '#fa709a']

const getColor = (index: number) => {
  return colors[index % colors.length]
}

// 判断是否为时间字段
const isTimeField = (fieldName: string, value: unknown): boolean => {
  if (!value) return false
  const lowerName = fieldName.toLowerCase()
  // 检查字段名
  if (lowerName.includes('time') || lowerName.includes('date') ||
      lowerName.includes('created') || lowerName.includes('updated')) {
    return true
  }
  // 检查值是否可以转换为有效日期
  try {
    const date = new Date(value as string)
    return !isNaN(date.getTime())
  } catch {
    return false
  }
}

// 响应式调整
const handleResize = () => {
  chartInstance?.resize()
}

onMounted(() => {
  initChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
})

// 监听数据变化
watch(() => [props.data, props.type, props.chartConfig], () => {
  initChart()
}, { deep: true })
</script>

<style scoped>
.chart-renderer {
  padding: 20px;
  background: white;
  border-radius: 8px;
}

.chart-container {
  width: 100%;
  height: 400px;
}
</style>
