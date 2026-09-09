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

const getPlatformOrigin = () => {
  const configuredOrigin = String(import.meta.env.VITE_PLATFORM_ORIGIN || '').trim();
  if (configuredOrigin) return trimTrailingSlash(configuredOrigin);
  return typeof window !== 'undefined' ? window.location.origin : '';
};

const getPlatformPublicPath = () => {
  const configuredPath = String(import.meta.env.VITE_PLATFORM_PUBLIC_PATH || '').trim();
  const publicPath = configuredPath || '/';
  if (!publicPath || publicPath === '/') return '/';
  return `/${publicPath.replace(/^\/+|\/+$/g, '')}/`;
};

const decodeRedirect = (value: string | null) => {
  let redirect = value || '';
  for (let index = 0; index < 3; index += 1) {
    try {
      const decoded = decodeURIComponent(redirect);
      if (decoded === redirect) break;
      redirect = decoded;
    } catch (error) {
      break;
    }
  }
  return redirect;
};

const extractRedirectFromLoginUrl = (value: string) => {
  const redirect = decodeRedirect(value);
  if (!redirect || (!redirect.includes('/platform/#/login') && !redirect.includes('/#/login'))) return redirect;
  try {
    const url = new URL(redirect);
    const hashQueryIndex = url.hash.indexOf('?');
    if (hashQueryIndex < 0) return '';
    return decodeRedirect(new URLSearchParams(url.hash.slice(hashQueryIndex + 1)).get('redirect'));
  } catch (error) {
    const match = redirect.match(/[?&]redirect=([^&]+)/);
    return match ? decodeRedirect(match[1]) : '';
  }
};

const normalizePlatformRedirect = (value: string) => {
  let redirect = extractRedirectFromLoginUrl(value);
  for (
    let index = 0;
    index < 3 && redirect && (redirect.includes('/platform/#/login') || redirect.includes('/#/login'));
    index += 1
  ) {
    const nextRedirect = extractRedirectFromLoginUrl(redirect);
    if (!nextRedirect || nextRedirect === redirect) return '';
    redirect = nextRedirect;
  }
  return redirect;
};

export const buildPlatformLoginUrl = (redirectUrl = window.location.href) => {
  const loginUrl = `${getPlatformOrigin()}${getPlatformPublicPath()}#/login`;
  const redirect = normalizePlatformRedirect(buildRentAppUrl(redirectUrl));
  return redirect ? `${loginUrl}?redirect=${encodeURIComponent(redirect)}` : loginUrl;
};

export const buildRentAppUrl = (redirect = '/') => {
  const decoded = decodeRedirect(redirect);
  if (/^https?:\/\//i.test(decoded)) return decoded;
  const normalizedHash = decoded.startsWith('/') ? decoded : `/${decoded}`;
  return `${window.location.origin}/#${normalizedHash}`;
};
