import type { AnyRecord } from '@/pages/platform/utils';

export const toPermissionTreeNodes = (systems: AnyRecord[]) =>
  (systems || []).map((system) => ({
    value: `system:${system.systemCode}`,
    label: `${system.systemName} / ${system.systemCode}`,
    type: 'system',
    systemCode: system.systemCode,
    children: (system.pages || []).map((page: AnyRecord) => ({
      value: `page:${system.systemCode}:${page.pageCode}`,
      label: `${page.pageName} / ${page.pageCode}`,
      type: 'page',
      systemCode: system.systemCode,
      pageCode: page.pageCode,
      children: (page.buttons || []).map((button: AnyRecord) => ({
        value: `button:${button.id}`,
        label: `${button.buttonName}（${button.buttonCode}）`,
        type: 'button',
        systemCode: system.systemCode,
        pageCode: page.pageCode,
        buttonId: button.id,
        buttonCode: button.buttonCode,
      })),
    })),
  }));
