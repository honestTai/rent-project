import { DialogPlugin, LoadingPlugin } from 'tdesign-vue-next';

const MUTATION_CONFIRM_CANCELLED = 'MUTATION_CONFIRM_CANCELLED';
const LOADING_SHOW_DELAY = 180;
const LOADING_HIDE_GRACE = 300;
const LOADING_MIN_VISIBLE = 500;

let loadingCount = 0;
let loadingTimer: number | undefined;
let hideTimer: number | undefined;
let loadingVisibleAt = 0;
let loadingInstance: ReturnType<typeof LoadingPlugin> | null = null;

const normalizePath = (url?: string) => {
  if (!url) return '';
  try {
    return new URL(url, window.location.origin).pathname.replace(/\/+$/, '') || '/';
  } catch {
    return url.split('?')[0].replace(/\/+$/, '') || '/';
  }
};

const rentMutationPatterns = [
  /^\/api\/web\/report-center\/generate$/,
  /^\/api\/web\/rent-component\/(refund|merchant-confirm|send|confirm-send|complete|sync|close|remark)$/,
  /^\/api\/web\/rent-component\/deposit\/deduct(\/confirm)?$/,
  /^\/api\/web\/goods\/(up|down|sort-top|delete|public-status\/update|create|update|sync|sync-bidirectional)$/,
  /^\/api\/web\/goods\/attrs\/(create|update|delete)$/,
  /^\/api\/web\/goods\/grants\/(assign|revoke)$/,
  /^\/api\/web\/users\/request-status\/update$/,
];

const resolveMutationText = (path: string) => {
  if (/\/delete$/.test(path)) return '删除后数据可能无法恢复，是否继续？';
  if (/\/(create|add)$/.test(path)) return '即将新增数据，是否继续？';
  if (/\/update$/.test(path)) return '即将修改数据，是否继续？';
  if (/\/sync/.test(path)) return '即将同步外部平台数据，是否继续？';
  return '即将执行会改变数据状态的操作，是否继续？';
};

export const startGlobalRequestLoading = () => {
  loadingCount += 1;
  if (hideTimer) {
    window.clearTimeout(hideTimer);
    hideTimer = undefined;
  }
  if (loadingInstance || loadingTimer) return;
  loadingTimer = window.setTimeout(() => {
    loadingTimer = undefined;
    loadingVisibleAt = Date.now();
    loadingInstance = LoadingPlugin({
      fullscreen: true,
      text: '请求处理中...',
      showOverlay: true,
      preventScrollThrough: true,
    });
  }, LOADING_SHOW_DELAY);
};

export const stopGlobalRequestLoading = () => {
  loadingCount = Math.max(loadingCount - 1, 0);
  if (loadingCount > 0) return;
  if (loadingTimer) {
    window.clearTimeout(loadingTimer);
    loadingTimer = undefined;
  }
  if (!loadingInstance) return;
  if (hideTimer) return;
  const visibleDuration = Date.now() - loadingVisibleAt;
  const hideDelay = Math.max(LOADING_HIDE_GRACE, LOADING_MIN_VISIBLE - visibleDuration);
  hideTimer = window.setTimeout(() => {
    hideTimer = undefined;
    if (loadingCount > 0) return;
    loadingInstance?.hide();
    loadingInstance = null;
    loadingVisibleAt = 0;
  }, hideDelay);
};

export const forceStopGlobalRequestLoading = () => {
  loadingCount = 0;
  if (loadingTimer) {
    window.clearTimeout(loadingTimer);
    loadingTimer = undefined;
  }
  if (hideTimer) {
    window.clearTimeout(hideTimer);
    hideTimer = undefined;
  }
  loadingInstance?.hide();
  loadingInstance = null;
  loadingVisibleAt = 0;
};

export const shouldConfirmMutationRequest = (method?: string, url?: string) => {
  const requestMethod = (method || 'get').toLowerCase();
  const path = normalizePath(url);
  if (requestMethod === 'delete' || requestMethod === 'put' || requestMethod === 'patch') return true;
  return requestMethod === 'post' && rentMutationPatterns.some((pattern) => pattern.test(path));
};

export const confirmMutationRequest = (url?: string) => {
  const path = normalizePath(url);
  const body = resolveMutationText(path);
  return new Promise<void>((resolve, reject) => {
    let settled = false;
    let dialog: ReturnType<typeof DialogPlugin.confirm> | null = null;
    const finish = (confirmed: boolean, hideDialog = true) => {
      if (settled) return;
      settled = true;
      if (hideDialog) dialog?.hide();
      if (confirmed) {
        resolve();
        return;
      }
      const error = new Error('操作已取消');
      error.name = MUTATION_CONFIRM_CANCELLED;
      reject(error);
    };
    dialog = DialogPlugin.confirm({
      header: '操作确认',
      body,
      theme: 'warning',
      confirmBtn: '确认',
      cancelBtn: '取消',
      closeOnOverlayClick: false,
      onConfirm: () => finish(true),
      onCancel: () => finish(false),
      onClose: () => finish(false, false),
    });
  });
};
