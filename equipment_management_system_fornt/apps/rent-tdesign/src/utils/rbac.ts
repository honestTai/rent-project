const SYSTEM_CODE = 'alipay';

export const getStoredUserInfo = () => {
  try {
    return JSON.parse(localStorage.getItem('userInfo') || '{}') || {};
  } catch (error) {
    return {};
  }
};

export const isSuperAdmin = (user: Record<string, unknown> = getStoredUserInfo()) => {
  const roleCodes = Array.isArray(user.roleCodes) ? user.roleCodes : [];
  return roleCodes.includes('super_admin');
};

export const hasPagePermission = (pageCode?: string, user: Record<string, unknown> = getStoredUserInfo()) => {
  if (!pageCode || isSuperAdmin(user)) return true;
  const pageCodes = Array.isArray(user.pageCodes) ? user.pageCodes : [];
  return pageCodes.includes(`${SYSTEM_CODE}:${pageCode}`);
};

export const hasButtonPermission = (
  pageCode?: string,
  buttonCode?: string,
  user: Record<string, unknown> = getStoredUserInfo(),
) => {
  if (!pageCode || !buttonCode || isSuperAdmin(user)) return true;
  const buttonCodes = Array.isArray(user.buttonCodes) ? user.buttonCodes : [];
  return buttonCodes.includes(`${SYSTEM_CODE}:${pageCode}:${buttonCode}`);
};
