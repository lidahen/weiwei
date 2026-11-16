import { Card } from 'antd';
import * as echarts from 'echarts';
import React, { useEffect, useState } from 'react';
import styles from './styles.less';

/**
 * 折线图
 */
const LineChart: React.FC<{ dbSize: number; time: string }> = (props) => {
    // 图表参数
    const { dbSize } = props;

    let lineChartRef: HTMLDivElement;
    const [lineChart, setLineChart] = useState();

    const config: Record<string, any> = {
        series: [
            {
                name: 'Pressure',
                type: 'gauge',
                progress: {
                    show: true,
                },
                detail: {
                    valueAnimation: true,
                    formatter: '{value}',
                },
                data: [],
            },
        ],
    };

    // 设置echarts属性
    const setEcharts = (instance: any | undefined = undefined) => {
        if (!dbSize) {
            return;
        }

        const ctr = instance || lineChart;

        config.series[0].data = [
            {
                value: dbSize,
                name: 'Key数量',
            },
        ];
        // 设置数据
        ctr.setOption(config);
        ctr.hideLoading();
    };

    // 第一次渲染时执行
    useEffect(() => {
        // 获取echarts实例
        const instance: any = echarts.init(lineChartRef);
        instance.showLoading();
        // 设置参数
        setEcharts(instance);
        // 开启自适应
        window.addEventListener('resize', instance.resize);

        // 设置到state
        setLineChart(instance);
    }, []);

    // 依赖props更新
    useEffect(() => {
        if (!lineChart) {
            return;
        }
        setEcharts();
    }, [props]);

    return (
        <Card title="key数量">
            <div
                ref={(ref: HTMLDivElement) => {
                    lineChartRef = ref;
                }}
                className={styles.lineChart}
            />
        </Card>
    );
};

export default LineChart;
