import { render } from '@testing-library/react';
import React from 'react';
import SeoHeading from './Heading';

describe('SeoHeading', () => {
    it('渲染 h1 且文案原样输出', () => {
        const { container } = render(<SeoHeading>动态广场</SeoHeading>);

        const h1 = container.querySelector('h1');
        expect(h1).not.toBeNull();
        expect(h1!.textContent).toBe('动态广场');
    });

    it('视觉隐藏但保留语义（不是 display:none，爬虫仍可读）', () => {
        const { container } = render(<SeoHeading>交换广场</SeoHeading>);

        const h1 = container.querySelector('h1') as HTMLElement;
        expect(h1.style.display).toBe('');
        expect(h1.style.position).toBe('absolute');
        expect(h1.style.width).toBe('1px');
        expect(h1.style.height).toBe('1px');
        expect(h1.style.overflow).toBe('hidden');
    });
});
