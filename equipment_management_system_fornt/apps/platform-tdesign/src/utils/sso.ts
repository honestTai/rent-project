type SsoUser = Record<string, unknown>;

const SSO_QUERY_KEYS = ['ssoToken', 'ssoUser', 'ssoState', 'token', 'access_token'];

// 兼容历史跳转中出现的多层 encodeURIComponent，避免 redirect 仍是 "%252F..."。
const decodeRepeatedly = (value: string) => {
  let result = value;
  for (let index = 0; index < 3; index += 1) {
    try {
      const decoded = decodeURIComponent(result);
      if (decoded === result) break;
      result = decoded;
    } catch (error) {
      break;
    }
  }
  return result;
};

const prepareUrlValue = (value: string) => {
  let result = value || '';
  for (let index = 0; index < 3; index += 1) {
    if (/^https?:\/\//i.test(result) || result.startsWith('/') || result.startsWith('#')) break;
    try {
      const decoded = decodeURIComponent(result);
      if (decoded === result) break;
      result = decoded;
    } catch (error) {
      break;
    }
  }
  return result;
};

const removeSsoQuery = (params: URLSearchParams) => {
  SSO_QUERY_KEYS.forEach((key) => params.delete(key));
};

const removeSsoHashQuery = (hash: string) => {
  if (!hash || !hash.includes('?')) return hash;
  const normalizedHash = hash.startsWith('#') ? hash.slice(1) : hash;
  const queryIndex = normalizedHash.indexOf('?');
  const hashPath = normalizedHash.slice(0, queryIndex);
  const params = new URLSearchParams(normalizedHash.slice(queryIndex + 1));
  removeSsoQuery(params);
  const queryText = params.toString();
  return `#${queryText ? `${hashPath}?${queryText}` : hashPath}`;
};

const stripSsoPayload = (value: string) => {
  const redirect = prepareUrlValue(value);
  if (!redirect) return redirect;
  try {
    const isAbsolute = /^https?:\/\//i.test(redirect);
    const base = typeof window !== 'undefined' ? window.location.origin : 'http://localhost';
    const url = new URL(redirect, base);
    removeSsoQuery(url.searchParams);
    if (url.hash) {
      url.hash = removeSsoHashQuery(url.hash);
    }
    return isAbsolute ? url.toString() : `${url.pathname}${url.search}${url.hash}`;
  } catch (error) {
    return redirect;
  }
};

// 避免把中台登录页本身作为回跳地址；只保留最内层的业务系统 redirect。
const unwrapLoginRedirect = (value: string) => {
  const redirect = stripSsoPayload(value);
  if (!redirect || (!redirect.includes('/#/login') && !redirect.includes('/platform/#/login'))) return redirect;

  try {
    const url = new URL(redirect);
    const queryIndex = url.hash.indexOf('?');
    if (queryIndex < 0) return redirect;
    return new URLSearchParams(url.hash.slice(queryIndex + 1)).get('redirect') || redirect;
  } catch (error) {
    const match = redirect.match(/[?&]redirect=([^&]+)/);
    return match ? match[1] : redirect;
  }
};

// 统一把 redirect 归一成最终目标地址：业务系统绝对地址，或中台内部路由。
export const normalizeSsoRedirect = (redirect?: string) => {
  if (!redirect) return '/platform/overview';
  let result = redirect;
  for (let index = 0; index < 3; index += 1) {
    const next = unwrapLoginRedirect(result);
    if (!next || next === result) break;
    result = next;
  }
  return stripSsoPayload(result);
};

// 本地不同端口 localStorage 不共享，回跳业务系统时必须显式携带一次 token。
export const appendSsoPayload = (redirectUrl: string, payload?: { token?: string; user?: SsoUser }) => {
  const cleanedRedirect = stripSsoPayload(redirectUrl);
  if (!payload?.token || !/^https?:\/\//.test(cleanedRedirect)) return cleanedRedirect;

  const url = new URL(cleanedRedirect);
  const hash = url.hash || '#/';
  const queryIndex = hash.indexOf('?');
  const hashPath = queryIndex >= 0 ? hash.slice(0, queryIndex) : hash;
  const hashQuery = queryIndex >= 0 ? hash.slice(queryIndex + 1) : '';
  const params = new URLSearchParams(hashQuery);
  params.set('ssoToken', payload.token);
  if (payload.user) {
    const { id, realName, userName, userPhoneNum, userSex } = payload.user;
    params.set('ssoUser', JSON.stringify({ id, realName, userName, userPhoneNum, userSex }));
  }
  url.hash = `${hashPath}?${params.toString()}`;
  return url.toString();
};

// 中台已经登录时再次进入 /login?redirect=...，直接用现有登录态完成 SSO 回跳。
export const buildStoredSsoPayload = () => {
  const token = localStorage.getItem('token') || '';
  if (!token) return undefined;
  try {
    return { token, user: JSON.parse(localStorage.getItem('userInfo') || '{}') || {} };
  } catch (error) {
    return { token, user: {} };
  }
};

// 外部业务系统用 location.href 回跳；中台内部路由交给调用方 router.push。
export const redirectWithSsoPayload = (redirect: string, payload?: { token?: string; user?: SsoUser }) => {
  const normalizedRedirect = normalizeSsoRedirect(redirect);
  const nextUrl = appendSsoPayload(normalizedRedirect, payload);
  if (/^https?:\/\//.test(normalizedRedirect) || normalizedRedirect.startsWith('//')) {
    window.location.href = nextUrl;
    return;
  }
  return nextUrl;
};
