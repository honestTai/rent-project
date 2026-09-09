/* eslint-disable simple-import-sort/imports */
import { createApp } from 'vue';
import TDesign from 'tdesign-vue-next';

import App from './App.vue';
import { loadRuntimeImageConfig } from './api/rent';
import router from './router';
import { store } from './store';
import i18n from './locales';

import 'tdesign-vue-next/es/style/index.css';
import '@/style/index.less';
import './permission';

document.title = 'HONESTTAI 租赁管理';

const app = createApp(App);

app.use(TDesign);
app.use(store);
app.use(router);
app.use(i18n);

void loadRuntimeImageConfig().catch(() => undefined);

app.mount('#app');
