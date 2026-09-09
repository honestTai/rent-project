import {
  ChartBarIcon,
  DashboardIcon,
  FileIcon,
  ChatMessageIcon,
  CheckCircleIcon,
  PreciseMonitorIcon,
  RootListIcon,
  ShopIcon,
  UserIcon,
} from 'tdesign-icons-vue-next';
import { shallowRef } from 'vue';
import type { RouteRecordRaw } from 'vue-router';

import Layout from '@/layouts/index.vue';

type PageDefinition = {
  path: string;
  alias?: string | string[];
  name: string;
  pageCode: string;
  title: string;
  icon: any;
  component?: RouteRecordRaw['component'];
  children?: PageDefinition[];
  redirect?: string;
  subjectType?: string;
};

export const alipayPages: PageDefinition[] = [
  {
    path: 'delivery-showcase',
    name: 'AlipayDeliveryShowcase',
    pageCode: 'deliveryShowcase',
    title: '功能导览',
    icon: shallowRef(CheckCircleIcon),
    component: () => import('@/pages/alipay/delivery-showcase/index.vue'),
  },
  {
    path: 'dashboard',
    alias: '/home',
    name: 'AlipayDashboard',
    pageCode: 'dashboard',
    title: '经营看板',
    icon: shallowRef(DashboardIcon),
    component: () => import('@/pages/alipay/dashboard/index.vue'),
  },
  {
    path: 'analysis',
    alias: '/analysis',
    name: 'AlipayAnalysis',
    pageCode: 'analysis',
    title: '数据分析',
    icon: shallowRef(ChartBarIcon),
    redirect: '/alipay/analysis/user-portrait',
    children: [
      { path: 'user-portrait', alias: '/analysisUserPortrait', name: 'AlipayAnalysisUserPortrait', pageCode: 'analysis', title: '用户画像分析', icon: shallowRef(UserIcon), subjectType: 'USER_PORTRAIT', component: () => import('@/pages/alipay/analysis/topic.vue') },
      { path: 'region', alias: '/analysisRegion', name: 'AlipayAnalysisRegion', pageCode: 'analysis', title: '地域分析', icon: shallowRef(ChartBarIcon), subjectType: 'REGION', component: () => import('@/pages/alipay/analysis/topic.vue') },
      { path: 'order', alias: '/analysisOrder', name: 'AlipayAnalysisOrder', pageCode: 'analysis', title: '订单分析', icon: shallowRef(RootListIcon), subjectType: 'ORDER', component: () => import('@/pages/alipay/analysis/topic.vue') },
      { path: 'device', alias: '/analysisDevice', name: 'AlipayAnalysisDevice', pageCode: 'analysis', title: '设备分析', icon: shallowRef(ShopIcon), subjectType: 'DEVICE', component: () => import('@/pages/alipay/analysis/topic.vue') },
      { path: 'revenue', alias: '/analysisRevenue', name: 'AlipayAnalysisRevenue', pageCode: 'analysis', title: '营收分析', icon: shallowRef(ChartBarIcon), subjectType: 'REVENUE', component: () => import('@/pages/alipay/analysis/topic.vue') },
      { path: 'rental', alias: '/analysisRental', name: 'AlipayAnalysisRental', pageCode: 'analysis', title: '租期与履约分析', icon: shallowRef(FileIcon), subjectType: 'RENTAL', component: () => import('@/pages/alipay/analysis/topic.vue') },
    ],
  },
  {
    path: 'reports',
    alias: ['/analysisReportCenter', '/reportCenter'],
    name: 'AlipayReportCenter',
    pageCode: 'report',
    title: '报表中心',
    icon: shallowRef(FileIcon),
    component: () => import('@/pages/alipay/analysis/report-center.vue'),
  },
  {
    path: 'orders',
    alias: '/orderList',
    name: 'AlipayOrders',
    pageCode: 'order',
    title: '订单列表',
    icon: shallowRef(RootListIcon),
    component: () => import('@/pages/alipay/orders/index.vue'),
  },
  {
    path: 'aftersales',
    alias: '/aftersales',
    name: 'AlipayAftersales',
    pageCode: 'aftersale',
    title: '售后同步',
    icon: shallowRef(FileIcon),
    component: () => import('@/pages/alipay/aftersale/index.vue'),
  },
  {
    path: 'goods',
    alias: ['/rentOderDeviceList', '/goods'],
    name: 'AlipayGoods',
    pageCode: 'goods',
    title: '租赁商品',
    icon: shallowRef(ShopIcon),
    component: () => import('@/pages/alipay/goods/index.vue'),
  },
  {
    path: 'banners',
    alias: '/banners',
    name: 'AlipayBanners',
    pageCode: 'banner',
    title: '轮播图设置',
    icon: shallowRef(FileIcon),
    component: () => import('@/pages/alipay/banners/index.vue'),
  },
  {
    path: 'catalog',
    name: 'AlipayCatalog',
    pageCode: 'catalog',
    title: '小程序目录',
    icon: shallowRef(RootListIcon),
    component: () => import('@/pages/alipay/catalog/index.vue'),
  },
  {
    path: 'users',
    alias: '/rentUserList',
    name: 'AlipayUsers',
    pageCode: 'user',
    title: '小程序用户',
    icon: shallowRef(UserIcon),
    component: () => import('@/pages/alipay/users/index.vue'),
  },
  {
    path: 'ledger',
    alias: '/operLog',
    name: 'AlipayLedger',
    pageCode: 'ledger',
    title: '台账管理',
    icon: shallowRef(FileIcon),
    component: () => import('@/pages/alipay/logs/index.vue'),
  },
  {
    path: 'monitor',
    name: 'AlipayMonitor',
    pageCode: 'monitor',
    title: '系统监控',
    icon: shallowRef(PreciseMonitorIcon),
    component: () => import('@/pages/alipay/monitor/index.vue'),
  },
  {
    path: 'system-logs',
    name: 'AlipaySystemLogs',
    pageCode: 'systemLog',
    title: '系统日志',
    icon: shallowRef(FileIcon),
    component: () => import('@/pages/alipay/system-logs/index.vue'),
  },
  {
    path: 'agent',
    name: 'AlipayAgent',
    pageCode: 'agent',
    title: 'AI 助手',
    icon: shallowRef(ChatMessageIcon),
    component: () => import('@/pages/alipay/agent/index.vue'),
  },
];

const hasPage = (pageCodes: string[], pageCode: string) => pageCodes.includes(`alipay:${pageCode}`);

const buildRoute = (page: PageDefinition, pageCodes: string[]): RouteRecordRaw | null => {
  if (!hasPage(pageCodes, page.pageCode)) return null;
  const children = (page.children || [])
    .map((child) => buildRoute(child, pageCodes))
    .filter(Boolean) as RouteRecordRaw[];
  const route: RouteRecordRaw = {
    path: page.path,
    name: page.name,
    component: page.component,
    meta: {
      title: { zh_CN: page.title, en_US: page.title },
      icon: page.icon,
      pageCode: page.pageCode,
      subjectType: page.subjectType,
    },
    children,
  };
  if (page.alias) {
    route.alias = page.alias;
  }
  if (page.redirect) {
    route.redirect = page.redirect;
  }
  return route;
};

export const buildAuthorizedAlipayRoute = (pageCodes: string[]): RouteRecordRaw | null => {
  const children = alipayPages.map((page) => buildRoute(page, pageCodes)).filter(Boolean) as RouteRecordRaw[];
  const firstChild = children[0];
  const redirect = firstChild ? `/alipay/${firstChild.path}` : '/alipay/no-permission';
  return {
    path: '/alipay',
    name: 'alipay',
    component: Layout,
    redirect,
    meta: {
      title: { zh_CN: 'HONESTTAI 租赁管理', en_US: 'Alipay Rent' },
      icon: shallowRef(DashboardIcon),
      orderNo: 0,
    },
    children: children.length
      ? children
      : [
          {
            path: 'no-permission',
            name: 'AlipayNoPermission',
            component: () => import('@/pages/alipay/no-permission.vue'),
            meta: { title: { zh_CN: '无权限', en_US: 'No Permission' }, icon: shallowRef(FileIcon) },
          },
        ],
  };
};

export const flattenAlipayRoutes = (routes: RouteRecordRaw[]) => {
  const result: RouteRecordRaw[] = [];
  const walk = (items: RouteRecordRaw[]) => {
    items.forEach((item) => {
      result.push(item);
      if (item.children) walk(item.children);
    });
  };
  walk(routes);
  return result;
};
