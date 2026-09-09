import { NotifyPlugin } from 'tdesign-vue-next';

type NotifyTheme = 'success' | 'info' | 'warning' | 'error';

type NotifyOnceOptions = {
  key: string;
  title: string;
  content?: string;
  theme?: NotifyTheme;
  duration?: number;
};

const activeNotifyKeys = new Set<string>();

export const notifyOnce = ({
  key,
  title,
  content,
  theme = 'warning',
  duration = 3000,
}: NotifyOnceOptions) => {
  if (activeNotifyKeys.has(key)) return;
  activeNotifyKeys.add(key);
  const options = { title, content, duration };
  if (theme === 'success') {
    NotifyPlugin.success(options);
  } else if (theme === 'info') {
    NotifyPlugin.info(options);
  } else if (theme === 'error') {
    NotifyPlugin.error(options);
  } else {
    NotifyPlugin.warning(options);
  }
  window.setTimeout(() => {
    activeNotifyKeys.delete(key);
  }, duration + 300);
};
