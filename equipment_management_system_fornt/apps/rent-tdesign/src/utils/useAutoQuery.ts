import { nextTick, watch, type WatchSource } from 'vue';

type AutoQuerySource = WatchSource<unknown> | WatchSource<unknown>[] | Record<string, unknown>;

type AutoQueryOptions = {
  debounce?: number;
  beforeQuery?: () => void;
};

export function useAutoQuery(source: AutoQuerySource, query: () => void | Promise<void>, options: AutoQueryOptions = {}) {
  const debounce = options.debounce ?? 350;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let paused = false;

  const clearTimer = () => {
    if (timer) {
      clearTimeout(timer);
      timer = undefined;
    }
  };

  const run = () => {
    clearTimer();
    options.beforeQuery?.();
    void query();
  };

  const pauseAutoQuery = async <T>(callback: () => T | Promise<T>) => {
    paused = true;
    clearTimer();
    try {
      return await callback();
    } finally {
      await nextTick();
      paused = false;
    }
  };

  const stopAutoQuery = watch(
    source as any,
    () => {
      if (paused) return;
      clearTimer();
      timer = setTimeout(run, debounce);
    },
    { deep: true },
  );

  return { pauseAutoQuery, runAutoQuery: run, stopAutoQuery };
}
