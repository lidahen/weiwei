import { serializeJsonLd } from './seo';

describe('serializeJsonLd', () => {
    it('把 < 转义成 \\u003c，防止用户内容里的 </script> 提前闭合脚本标签', () => {
        const out = serializeJsonLd({ name: '恶意</script><img src=x onerror=alert(1)>' });

        expect(out).not.toContain('</script>');
        expect(out).toContain('\\u003c');
        expect(out).toContain('恶意');
    });

    it('普通内容解析回来与原文等价', () => {
        const original = { name: '九成新机械键盘', price: '320' };
        expect(JSON.parse(serializeJsonLd(original))).toEqual(original);
    });

    it('含 < 的内容 JSON.parse 后仍是原文', () => {
        const original = { name: 'a<b' };
        expect(JSON.parse(serializeJsonLd(original))).toEqual(original);
    });
});
