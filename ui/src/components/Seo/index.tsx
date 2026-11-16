import React from 'react';
import { Helmet } from 'react-helmet-async';
import { SITE_DESCRIPTION, SITE_NAME, absoluteUrl, resolveSiteUrl, serializeJsonLd } from '@/utils/seo';

export interface SeoProps {
    title?: string;
    description?: string;
    canonicalPath?: string;
    image?: string;
    type?: 'website' | 'article' | 'product';
    noindex?: boolean;
    jsonLd?: object | object[] | null;
}

/** 统一注入页面 head：title / description / canonical / OG / robots / JSON-LD。 */
export default function Seo({
    title,
    description,
    canonicalPath,
    image,
    type = 'website',
    noindex = false,
    jsonLd,
}: SeoProps) {
    const siteUrl = resolveSiteUrl(
        process.env.UMI_APP_SITE_URL,
        typeof window !== 'undefined' ? window.location.origin : '',
    );
    const fullTitle = title ? `${title} - ${SITE_NAME}` : SITE_NAME;
    const desc = (description || SITE_DESCRIPTION).slice(0, 155);
    const canonical = canonicalPath ? absoluteUrl(canonicalPath, siteUrl) : null;
    // 绝对 URL 缺失时整段 JSON-LD 不输出（相对 URL 会被 Rich Results 判为无效）
    const payload = siteUrl
        ? ((Array.isArray(jsonLd) ? jsonLd : [jsonLd]).filter(Boolean) as object[])
        : [];

    return (
        <Helmet>
            <title>{fullTitle}</title>
            <meta name="description" content={desc} />
            <meta name="robots" content={noindex ? 'noindex,follow' : 'index,follow'} />
            <meta property="og:title" content={fullTitle} />
            <meta property="og:description" content={desc} />
            <meta property="og:type" content={type} />
            <meta property="og:site_name" content={SITE_NAME} />
            <meta name="twitter:card" content="summary_large_image" />
            {canonical && <link rel="canonical" href={canonical} />}
            {canonical && <meta property="og:url" content={canonical} />}
            {image && <meta property="og:image" content={image} />}
            {payload.map((item, index) => (
                <script key={index} type="application/ld+json">
                    {serializeJsonLd(item)}
                </script>
            ))}
        </Helmet>
    );
}