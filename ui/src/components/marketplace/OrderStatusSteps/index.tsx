import { Steps } from 'antd';
import React from 'react';

export interface OrderStatusStepsProps {
    current: number;
    status?: 'process' | 'finish' | 'error' | 'wait';
    items?: { title: string; description?: string }[];
}

const defaultItems = [
    { title: '待付款' },
    { title: '待发货' },
    { title: '待收货' },
    { title: '已完成' },
];

const OrderStatusSteps: React.FC<OrderStatusStepsProps> = ({
    current,
    status = 'process',
    items = defaultItems,
}) => <Steps current={current} status={status} items={items} />;

export default OrderStatusSteps;
