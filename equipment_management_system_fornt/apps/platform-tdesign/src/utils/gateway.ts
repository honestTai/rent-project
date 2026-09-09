const DEFAULT_DEV_GATEWAY_ORIGIN = 'http://127.0.0.1:7777';
const DEFAULT_PROD_GATEWAY_ORIGIN = '/gateway';

const trimTrailingSlash = (value: string) => value.replace(/\/+$/, '');

export const getGatewayOrigin = () => {
  const envOrigin = String(import.meta.env.VITE_GATEWAY_ORIGIN || '').trim();

  if (envOrigin) {
    return trimTrailingSlash(envOrigin);
  }

  if (import.meta.env.PROD) {
    return DEFAULT_PROD_GATEWAY_ORIGIN;
  }

  return DEFAULT_DEV_GATEWAY_ORIGIN;
};

export const buildGatewayUrl = (path: string) => {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${getGatewayOrigin()}${normalizedPath}`;
};
