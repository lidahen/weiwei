/** AI 音乐创作 · 工作台(默认页: 我的作品列表 + 快捷入口) */
import { PageContainer } from '@ant-design/pro-components';
import { Card, Col, Row, Button, Space, Tag, Empty, Spin, Typography } from 'antd';
import {
  SoundOutlined,
  AudioOutlined,
  EditOutlined,
  CustomerServiceOutlined,
} from '@ant-design/icons';
import { history } from 'umi';
import { useEffect, useState } from 'react';
import { listMyWorks, MusicWork, pollSongStatus } from '@/services/ant-design-pro/aimusic';

const { Title, Paragraph } = Typography;

const STATUS_LABEL: Record<number, { text: string; color: string }> = {
  0: { text: '排队中', color: 'default' },
  1: { text: '生成中', color: 'processing' },
  2: { text: '已完成', color: 'success' },
  3: { text: '失败', color: 'error' },
};

const TYPE_LABEL: Record<number, string> = {
  1: '文生歌',
  2: '平台音色翻唱',
  3: '克隆说唱',
  4: '纯音乐',
};

const Index: React.FC = () => {
  const [works, setWorks] = useState<MusicWork[]>([]);
  const [loading, setLoading] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const res = await listMyWorks({ status: 2 });
      setWorks(res.records?.slice(0, 8) ?? []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  // 自动轮询生成中的作品
  useEffect(() => {
    const pending = works.some((w) => w.status === 1);
    if (!pending) return;
    const timer = setInterval(async () => {
      const pendingIds = works.filter((w) => w.status === 1).map((w) => w.id);
      await Promise.allSettled(pendingIds.map((id) => pollSongStatus(id)));
      load();
    }, 5000);
    return () => clearInterval(timer);
  }, [works]);

  return (
    <PageContainer title="AI 音乐创作">
      <Row gutter={[16, 16]}>
        <Col xs={12} lg={6}>
          <Card hoverable onClick={() => history.push('/ai/music/works')}>
            <Space direction="vertical" size={4}>
              <SoundOutlined style={{ fontSize: 28, color: '#1677ff' }} />
              <Title level={5} style={{ margin: 0 }}>我的作品</Title>
              <Paragraph type="secondary" style={{ margin: 0 }}>查看历史歌曲,二次创作</Paragraph>
            </Space>
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card hoverable onClick={() => history.push('/ai/music/voices')}>
            <Space direction="vertical" size={4}>
              <AudioOutlined style={{ fontSize: 28, color: '#722ed1' }} />
              <Title level={5} style={{ margin: 0 }}>我的音色</Title>
              <Paragraph type="secondary" style={{ margin: 0 }}>上传样本训练克隆音色</Paragraph>
            </Space>
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card hoverable onClick={() => history.push('/ai/music/works')}>
            <Space direction="vertical" size={4}>
              <EditOutlined style={{ fontSize: 28, color: '#52c41a' }} />
              <Title level={5} style={{ margin: 0 }}>AI 歌词助手</Title>
              <Paragraph type="secondary" style={{ margin: 0 }}>主题 → 结构化歌词</Paragraph>
            </Space>
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card>
            <Space direction="vertical" size={4}>
              <CustomerServiceOutlined style={{ fontSize: 28, color: '#fa8c16' }} />
              <Title level={5} style={{ margin: 0 }}>今日配额</Title>
              <Paragraph type="secondary" style={{ margin: 0 }}>文生歌 5/首 · 写词 1/次</Paragraph>
            </Space>
          </Card>
        </Col>
      </Row>

      <Card title="最近作品" style={{ marginTop: 16 }}>
        {loading ? (
          <Spin />
        ) : works.length === 0 ? (
          <Empty description="还没有作品,去工作台创建吧" />
        ) : (
          <Row gutter={[16, 16]}>
            {works.map((w) => (
              <Col xs={12} sm={8} lg={6} key={w.id}>
                <Card
                  hoverable
                  cover={w.coverUrl ? <img src={w.coverUrl} alt={w.title || ''} /> : null}
                  onClick={() => history.push(`/ai/music/works/${w.id}`)}
                >
                  <Card.Meta
                    title={w.title || `作品 #${w.id}`}
                    description={
                      <Space direction="vertical" size={2}>
                        <Tag>{TYPE_LABEL[w.type]}</Tag>
                        <Tag color={STATUS_LABEL[w.status]?.color}>
                          {STATUS_LABEL[w.status]?.text}
                        </Tag>
                        {w.audioDurationMs ? (
                          <span style={{ fontSize: 12, color: '#999' }}>
                            {(w.audioDurationMs / 1000).toFixed(0)}s
                          </span>
                        ) : null}
                      </Space>
                    }
                  />
                </Card>
              </Col>
            ))}
          </Row>
        )}
      </Card>

      <Card title="快速创建" style={{ marginTop: 16 }}>
        <Space>
          <Button type="primary" onClick={() => history.push('/ai/music/works?action=create')}>
            一键文生歌
          </Button>
          <Button onClick={() => history.push('/ai/music/voices')}>
            先训练我的音色
          </Button>
        </Space>
      </Card>
    </PageContainer>
  );
};

export default Index;
