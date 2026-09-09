import {
  DashboardIcon,
  FileIcon,
  KeyIcon,
  NotificationIcon,
  PreciseMonitorIcon,
  RootListIcon,
  SettingIcon,
  TimeIcon,
  UserIcon,
} from 'tdesign-icons-vue-next';
import { shallowRef } from 'vue';

import Layout from '@/layouts/index.vue';

export default [
  {
    path: '/platform',
    component: Layout,
    redirect: '/platform/overview',
    name: 'platform',
    meta: {
      title: {
        zh_CN: 'HONESTTAI 产品中台',
        en_US: 'Platform',
      },
      icon: shallowRef(DashboardIcon),
      orderNo: 0,
    },
    children: [
      {
        path: 'overview',
        name: 'PlatformOverview',
        component: () => import('@/pages/platform/overview/index.vue'),
        meta: {
          title: {
            zh_CN: '系统总览',
            en_US: 'Overview',
          },
          icon: shallowRef(DashboardIcon),
          rbacPageCode: 'platform:overview',
        },
      },
      {
        path: 'config',
        name: 'PlatformConfig',
        component: () => import('@/pages/platform/config/index.vue'),
        meta: {
          title: {
            zh_CN: '配置中心',
            en_US: 'Config',
          },
          icon: shallowRef(SettingIcon),
          rbacPageCode: 'platform:config',
        },
      },
      {
        path: 'notify',
        name: 'PlatformNotify',
        component: () => import('@/pages/platform/notify/index.vue'),
        meta: {
          title: {
            zh_CN: '通知通道',
            en_US: 'Notify',
          },
          icon: shallowRef(NotificationIcon),
          rbacPageCode: 'platform:notify',
        },
      },
      {
        path: 'task',
        name: 'PlatformTask',
        component: () => import('@/pages/platform/task/index.vue'),
        meta: {
          title: {
            zh_CN: '任务中心',
            en_US: 'Tasks',
          },
          icon: shallowRef(TimeIcon),
          rbacPageCode: 'platform:task',
        },
      },
      {
        path: 'monitor',
        name: 'PlatformMonitor',
        component: () => import('@/pages/platform/monitor/index.vue'),
        meta: {
          title: {
            zh_CN: '系统监控',
            en_US: 'Monitor',
          },
          icon: shallowRef(PreciseMonitorIcon),
          rbacPageCode: 'platform:monitor',
        },
      },
      {
        path: 'logs',
        name: 'PlatformLogs',
        component: () => import('@/pages/platform/logs/index.vue'),
        meta: {
          title: {
            zh_CN: '系统日志',
            en_US: 'Logs',
          },
          icon: shallowRef(FileIcon),
          rbacPageCode: 'platform:systemLog',
        },
      },
      {
        path: 'rbac',
        name: 'PlatformRbac',
        redirect: '/platform/rbac/roles',
        meta: {
          title: {
            zh_CN: '权限中心',
            en_US: 'RBAC',
          },
          icon: shallowRef(KeyIcon),
          rbacPageCode: 'platform:rbac',
        },
        children: [
          {
            path: 'roles',
            name: 'PlatformRbacRoles',
            component: () => import('@/pages/platform/rbac/roles.vue'),
            meta: {
              title: {
                zh_CN: '角色授权',
                en_US: 'Roles',
              },
              icon: shallowRef(KeyIcon),
              rbacPageCode: 'platform:rbac',
            },
          },
          {
            path: 'user-roles',
            name: 'PlatformRbacUserRoles',
            component: () => import('@/pages/platform/rbac/user-roles.vue'),
            meta: {
              title: {
                zh_CN: '用户角色',
                en_US: 'User Roles',
              },
              icon: shallowRef(UserIcon),
              rbacPageCode: 'platform:rbac',
            },
          },
          {
            path: 'resources',
            name: 'PlatformRbacResources',
            component: () => import('@/pages/platform/rbac/resources.vue'),
            meta: {
              title: {
                zh_CN: '权限资源',
                en_US: 'Resources',
              },
              icon: shallowRef(RootListIcon),
              rbacPageCode: 'platform:rbac',
            },
          },
        ],
      },
      {
        path: 'user',
        name: 'PlatformUser',
        component: () => import('@/pages/platform/user/index.vue'),
        meta: {
          title: {
            zh_CN: '用户管理',
            en_US: 'User',
          },
          icon: shallowRef(UserIcon),
          rbacPageCode: 'platform:user',
        },
      },
      {
        path: 'profile',
        name: 'PlatformProfile',
        component: () => import('@/pages/platform/profile/index.vue'),
        meta: {
          title: {
            zh_CN: '个人中心',
            en_US: 'Profile',
          },
          icon: shallowRef(UserIcon),
          hidden: true,
        },
      },
    ],
  },
];
