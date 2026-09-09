import { reactive } from 'vue';

import type { TdSelectProps } from 'tdesign-vue-next';

type OptionValue = string | number | boolean | bigint | null | undefined;

export interface SearchableOption {
  label?: unknown;
  value?: OptionValue;
  text?: unknown;
  title?: unknown;
  name?: unknown;
  children?: unknown[];
}

export interface FlatSearchOption {
  label: string;
  value: OptionValue;
  pathLabel: string;
  searchText: string;
  raw?: unknown;
}

const TOKEN_SPLIT_RE = /[\s/\\|,，.。:：;；\-_#]+/;
const flattenedOptionCache = new WeakMap<unknown[], FlatSearchOption[]>();

const searchableSelectPopupProps: NonNullable<TdSelectProps['popupProps']> = {
  attach: 'body',
  destroyOnClose: false,
  delay: 0,
  overlayClassName: 'ems-searchable-select-popup',
  overlayStyle: (triggerElement) => ({
    width: `${triggerElement.getBoundingClientRect().width}px`,
    maxWidth: 'calc(100vw - 24px)',
  }),
  placement: 'bottom-left',
  popperOptions: {
    modifiers: [
      { name: 'offset', options: { offset: [0, 4] } },
      { name: 'flip', options: { fallbackPlacements: ['top-left'] } },
      { name: 'preventOverflow', options: { padding: 12 } },
    ],
  },
};

const searchableSelectScroll: NonNullable<TdSelectProps['scroll']> = {
  type: 'virtual',
  threshold: 80,
  rowHeight: 32,
  bufferSize: 20,
};

export function normalizeSearchText(value: unknown): string {
  return String(value ?? '')
    .trim()
    .toLowerCase();
}

export function compactSearchText(value: unknown): string {
  return normalizeSearchText(value).replace(/[\s/\\|,，.。:：;；\-_#]+/g, '');
}

export function optionSearchText(option: unknown): string {
  if (!option || typeof option !== 'object') return normalizeSearchText(option);
  const item = option as Record<string, unknown>;
  return [
    item.label,
    item.value,
    item.text,
    item.title,
    item.name,
    item.pathLabel,
    item.searchText,
    item.devName,
    item.devNumber,
    item.deviceName,
    item.deviceCode,
    item.orderString,
    item.buyOrderString,
  ]
    .filter((value) => value !== null && value !== undefined && value !== '')
    .map((value) => String(value))
    .join(' ');
}

export function matchesSearchText(option: unknown, keyword: string): boolean {
  const query = normalizeSearchText(keyword);
  if (!query) return true;
  const text = normalizeSearchText(optionSearchText(option));
  if (text.includes(query)) return true;
  return compactSearchText(text).includes(compactSearchText(query));
}

export function rankSearchOptions<T>(options: T[] | undefined, keyword: string): T[] {
  const source = Array.isArray(options) ? options : [];
  const query = normalizeSearchText(keyword);
  if (!query) return source;
  return source
    .map((option, index) => ({
      option: rankChildren(option, query),
      index,
      score: treeSearchScore(option, query),
    }))
    .filter((item) => item.score > 0 || hasMatchedChildren(item.option))
    .sort((left, right) => right.score - left.score || left.index - right.index)
    .map((item) => item.option);
}

export function optionSearchScore(option: unknown, keyword: string): number {
  const query = normalizeSearchText(keyword);
  if (!query) return 1;

  const text = normalizeSearchText(optionSearchText(option));
  const compactText = compactSearchText(text);
  const compactQuery = compactSearchText(query);
  if (!text && !compactText) return 0;

  const tokens = text.split(TOKEN_SPLIT_RE).filter(Boolean);
  if (text === query) return 10000;
  if (compactText === compactQuery) return 9600;
  if (tokens.some((token) => token === query)) return 9300;
  if (tokens.some((token) => compactSearchText(token) === compactQuery)) return 9000;
  if (text.startsWith(query)) return 8500;
  if (compactText.startsWith(compactQuery)) return 8200;

  const tokenPrefixIndex = tokens.findIndex((token) => token.startsWith(query) || compactSearchText(token).startsWith(compactQuery));
  if (tokenPrefixIndex >= 0) return 7800 - tokenPrefixIndex;

  const index = text.indexOf(query);
  if (index >= 0) return 6000 - Math.min(index, 200);

  const compactIndex = compactText.indexOf(compactQuery);
  if (compactIndex >= 0) return 5600 - Math.min(compactIndex, 200);

  return 0;
}

export function flattenCascaderOptions(options: unknown[] | undefined): FlatSearchOption[] {
  const source = Array.isArray(options) ? options : [];
  const cached = flattenedOptionCache.get(source);
  if (cached) return cached;

  const result: FlatSearchOption[] = [];

  const visit = (nodes: unknown[], parents: string[]) => {
    nodes.forEach((node) => {
      if (!node || typeof node !== 'object') return;
      const item = node as Record<string, unknown>;
      const label = readableOptionLabel(item);
      const value = readableOptionValue(item);
      const path = [...parents, label].filter(Boolean);
      const children = Array.isArray(item.children) ? item.children : [];

      if (children.length) {
        visit(children, path);
        return;
      }

      const pathLabel = path.join('/');
      result.push({
        label: pathLabel || String(value ?? ''),
        value,
        pathLabel,
        searchText: [pathLabel, optionSearchText(item), optionSearchText(item.raw)].filter(Boolean).join(' '),
        raw: item.raw || item,
      });
    });
  };

  visit(source, []);
  flattenedOptionCache.set(source, result);
  return result;
}

export function createSearchableOptions() {
  const keywords = reactive<Record<string, string>>({});

  const rememberSearch = (key: string, value: unknown) => {
    keywords[key] = String(value ?? '');
  };

  const searchableSelectProps = <T>(key: string, options: T[] | undefined) => ({
    options: rankSearchOptions(options, keywords[key] || ''),
    popupProps: searchableSelectPopupProps,
    scroll: searchableSelectScroll,
    filter: (filterWords: string, option: T) => matchesSearchText(option, filterWords),
    onSearch: (value: string) => rememberSearch(key, value),
    onInputChange: (value: string) => rememberSearch(key, value),
    onPopupVisibleChange: (visible: boolean) => {
      if (!visible) rememberSearch(key, '');
    },
  });

  const searchableCascaderProps = <T>(key: string, options: T[] | undefined) => ({
    options: rankSearchOptions(options, keywords[key] || ''),
    filter: (filterWords: string, node: { data?: T; label?: unknown; value?: unknown }) => {
      const data = node?.data || ({ label: node?.label, value: node?.value } as T);
      rememberSearch(key, filterWords);
      return matchesSearchText(data, filterWords);
    },
  });

  const searchableCascaderSelectProps = (key: string, options: unknown[] | undefined) =>
    searchableSelectProps(key, flattenCascaderOptions(options));

  return {
    searchableSelectProps,
    searchableCascaderProps,
    searchableCascaderSelectProps,
    rememberSearch,
  };
}

function readableOptionLabel(item: Record<string, unknown>): string {
  return String(item.label ?? item.name ?? item.devName ?? item.deviceName ?? item.value ?? '');
}

function readableOptionValue(item: Record<string, unknown>): OptionValue {
  const value = item.value ?? item.id ?? item.devNumber ?? item.deviceCode ?? item.name ?? item.devName ?? item.label;
  return value as OptionValue;
}

function treeSearchScore(option: unknown, query: string): number {
  const ownScore = optionSearchScore(option, query);
  if (!option || typeof option !== 'object') return ownScore;
  const children = (option as SearchableOption).children;
  if (!Array.isArray(children) || !children.length) return ownScore;
  return Math.max(ownScore, ...children.map((child) => treeSearchScore(child, query) - 1));
}

function rankChildren<T>(option: T, query: string): T {
  if (!option || typeof option !== 'object') return option;
  const children = (option as SearchableOption).children;
  if (!Array.isArray(children) || !children.length) return option;
  return {
    ...(option as object),
    children: rankSearchOptions(children, query),
  } as T;
}

function hasMatchedChildren(option: unknown): boolean {
  if (!option || typeof option !== 'object') return false;
  const children = (option as SearchableOption).children;
  return Array.isArray(children) && children.length > 0;
}
