import { computed, onMounted, reactive, unref, watch, type ComputedRef, type Ref } from 'vue';

export type DisplaySettingKind = 'modules' | 'fields' | 'actions';

export type DisplaySettingScope = string;

export type DisplaySettingItem = {
  key: string;
  label: string;
  group?: string;
  description?: string;
  defaultVisible?: boolean;
  locked?: boolean;
};

export type DisplaySettingState = Partial<Record<DisplaySettingKind, string[]>>;

type DisplaySettingDefinitions = Partial<Record<DisplaySettingKind, DisplaySettingItem[]>>;
type MaybeRef<T> = T | Ref<T> | ComputedRef<T>;

type StoredDisplaySettingState = DisplaySettingState & {
  knownKeys?: Partial<Record<DisplaySettingKind, string[]>>;
};

const DISPLAY_SETTING_PREFIX = 'rent-tdesign:display-settings';
const DISPLAY_SETTING_KINDS: DisplaySettingKind[] = ['modules', 'fields', 'actions'];

const unique = (keys: string[]) => Array.from(new Set(keys.filter(Boolean)));
const definitionKeys = (definitions: DisplaySettingDefinitions) =>
  DISPLAY_SETTING_KINDS.reduce((result, kind) => {
    result[kind] = (definitions[kind] || []).map((item) => item.key);
    return result;
  }, {} as Required<Record<DisplaySettingKind, string[]>>);

export const displaySettingStorageKey = (scope: DisplaySettingScope, version = 1) =>
  `${DISPLAY_SETTING_PREFIX}:${scope}:v${version}`;

export const defaultDisplayKeys = (items: DisplaySettingItem[] = []) =>
  items.filter((item) => item.locked || item.defaultVisible !== false).map((item) => item.key);

export function normalizeDisplayKeys(
  value: unknown,
  items: DisplaySettingItem[] = [],
  knownKeys: string[] = [],
) {
  const allowed = new Set(items.map((item) => item.key));
  const defaultKeys = defaultDisplayKeys(items);
  const lockedKeys = items.filter((item) => item.locked).map((item) => item.key);
  const source = Array.isArray(value) ? value.map(String) : defaultKeys;
  const next = unique([...source, ...lockedKeys]).filter((key) => allowed.has(key));
  const knownSet = new Set(knownKeys);

  defaultKeys.forEach((key) => {
    if (!knownSet.has(key) && !next.includes(key)) {
      next.push(key);
    }
  });

  return next;
}

export function orderDisplayItems<T extends { key: string }>(keys: string[] = [], items: T[] = []) {
  const itemMap = new Map(items.map((item) => [item.key, item]));
  const ordered = keys.map((key) => itemMap.get(key)).filter(Boolean) as T[];
  const orderedSet = new Set(ordered.map((item) => item.key));
  return [
    ...ordered,
    ...items.filter((item) => !orderedSet.has(item.key)),
  ];
}

export function visibleDisplayItems<T extends { key: string }>(keys: string[] = [], items: T[] = []) {
  const keySet = new Set(keys);
  return orderDisplayItems(keys, items).filter((item) => keySet.has(item.key));
}

export function normalizeDisplayState(
  raw: StoredDisplaySettingState | undefined,
  definitions: DisplaySettingDefinitions,
) {
  const state: Required<DisplaySettingState> = {
    modules: [],
    fields: [],
    actions: [],
  };

  DISPLAY_SETTING_KINDS.forEach((kind) => {
    state[kind] = normalizeDisplayKeys(raw?.[kind], definitions[kind] || [], raw?.knownKeys?.[kind] || []);
  });

  return state;
}

export function useLocalDisplaySettings(options: {
  scope: DisplaySettingScope;
  version?: number;
  definitions: MaybeRef<DisplaySettingDefinitions>;
}) {
  const storageKey = computed(() => displaySettingStorageKey(options.scope, options.version || 1));
  const currentDefinitions = computed(() => unref(options.definitions) || {});
  const state = reactive<Required<DisplaySettingState>>({
    modules: [],
    fields: [],
    actions: [],
  });

  const assignState = (next: DisplaySettingState) => {
    DISPLAY_SETTING_KINDS.forEach((kind) => {
      state[kind] = [...(next[kind] || [])];
    });
  };

  const normalizeCurrent = (raw?: StoredDisplaySettingState) => {
    const next = normalizeDisplayState(raw, currentDefinitions.value);
    assignState(next);
  };

  const load = () => {
    if (typeof window === 'undefined') {
      normalizeCurrent();
      return;
    }

    try {
      const stored = window.localStorage.getItem(storageKey.value);
      normalizeCurrent(stored ? JSON.parse(stored) : undefined);
    } catch {
      normalizeCurrent();
    }
  };

  const save = (next: DisplaySettingState = state) => {
    const knownKeys = definitionKeys(currentDefinitions.value);
    const normalized = normalizeDisplayState({ ...next, knownKeys } as StoredDisplaySettingState, currentDefinitions.value);
    assignState(normalized);

    if (typeof window === 'undefined') return;

    window.localStorage.setItem(storageKey.value, JSON.stringify({
      modules: state.modules,
      fields: state.fields,
      actions: state.actions,
      knownKeys,
    }));
  };

  const reset = (kind?: DisplaySettingKind) => {
    if (kind) {
      state[kind] = defaultDisplayKeys(currentDefinitions.value[kind] || []);
    } else {
      normalizeCurrent();
    }
  };

  const visibleItems = <T extends DisplaySettingItem>(kind: DisplaySettingKind, items: T[]) =>
    visibleDisplayItems(state[kind], items);

  onMounted(load);
  watch(currentDefinitions, (_, previousDefinitions) => normalizeCurrent({
    modules: state.modules,
    fields: state.fields,
    actions: state.actions,
    knownKeys: definitionKeys(previousDefinitions || {}),
  }), { deep: true });

  return {
    state,
    storageKey,
    load,
    save,
    reset,
    visibleItems,
  };
}
