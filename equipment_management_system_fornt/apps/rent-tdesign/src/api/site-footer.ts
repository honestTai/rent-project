import axios from 'axios';

import { buildGatewayUrl } from '@/utils/gateway';

export type SiteFooterConfig = {
  'site.footer.icp-record'?: string;
  'site.footer.police-record'?: string;
  'site.footer.copyright'?: string;
};

export const fetchSiteFooterConfig = async (): Promise<SiteFooterConfig> => {
  const response = await axios.get(buildGatewayUrl('/api/platform/public/site-footer'), {
    timeout: 300000,
  });
  const body = response.data || {};
  const code = Number(body.code);
  if (code === 0 || code === 200) {
    return body.data || {};
  }
  throw new Error(body.msg || '页脚配置读取失败');
};
