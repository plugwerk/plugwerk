-- =============================================================================
-- Plugwerk — Development Test Data Seed
-- =============================================================================
-- Run against a local Plugwerk database that has Liquibase migrations applied:
--   PGPASSWORD=plugwerk psql -h localhost -U plugwerk -d plugwerk -f docs/dev/seed-testdata.sql
--
-- Creates:
--   3 namespaces (default, acme-corp, community)
--   30 plugins per namespace (90 total)
--   1 PUBLISHED release per plugin (90 total)
-- =============================================================================

-- ============================================================
-- Namespaces
-- ============================================================
INSERT INTO namespace (id, slug, owner_org) VALUES
  ('00000000-0000-0000-0000-000000000002', 'acme-corp',  'ACME Corporation'),
  ('00000000-0000-0000-0000-000000000003', 'community',  'Community Contributors')
ON CONFLICT (slug) DO NOTHING;

-- ============================================================
-- Plugins — default namespace
-- ============================================================
INSERT INTO plugin (id, namespace_id, plugin_id, name, description, author, license, categories, tags, status) VALUES
  ('10000000-0000-0000-0000-000000000001',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.auth-sso','Auth SSO','Single sign-on integration for enterprise systems','devtank42','MIT','{Security,Auth}','{sso,oauth2,enterprise}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000002',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.audit-log','Audit Log','Persistent audit trail for all user actions','devtank42','MIT','{Compliance,Security}','{audit,logging,compliance}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000003',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.backup-manager','Backup Manager','Automated backup scheduling and restore','devtank42','Apache-2.0','{Operations}','{backup,restore,scheduling}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000004',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.cache-redis','Cache Redis','Redis-backed caching layer with TTL support','devtank42','MIT','{Performance}','{cache,redis,performance}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000005',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.csv-export','CSV Export','Bulk data export in CSV and Excel formats','devtank42','MIT','{Data,Export}','{csv,excel,export}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000006',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.dashboard-widgets','Dashboard Widgets','Customizable dashboard widget framework','devtank42','MIT','{UI}','{dashboard,widgets,ui}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000007',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.email-templates','Email Templates','HTML email template engine with preview','devtank42','MIT','{Notifications}','{email,templates,notifications}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000008',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.feature-flags','Feature Flags','Runtime feature flag management','devtank42','Apache-2.0','{Operations}','{feature-flags,runtime,config}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000009',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.geo-ip','Geo IP','IP geolocation lookup with MaxMind GeoIP2','devtank42','MIT','{Analytics}','{geo,ip,location}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000010',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.health-checks','Health Checks','Extended health check endpoints for monitoring','devtank42','MIT','{Operations,Monitoring}','{health,monitoring,actuator}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000011',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.i18n-manager','I18n Manager','Translation management with hot-reload','devtank42','MIT','{UI,Localization}','{i18n,l10n,translations}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000012',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.job-scheduler','Job Scheduler','Quartz-based distributed job scheduler','devtank42','Apache-2.0','{Operations}','{scheduler,quartz,cron}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000013',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.kafka-bridge','Kafka Bridge','Kafka event streaming integration','devtank42','Apache-2.0','{Messaging}','{kafka,streaming,events}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000014',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.ldap-sync','LDAP Sync','Active Directory / LDAP user synchronisation','devtank42','MIT','{Auth,Security}','{ldap,active-directory,sync}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000015',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.metrics-collector','Metrics Collector','Prometheus metrics collection and aggregation','devtank42','MIT','{Monitoring}','{prometheus,metrics,monitoring}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000016',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.notification-hub','Notification Hub','Multi-channel notification dispatcher','devtank42','MIT','{Notifications}','{notifications,push,webhook}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000017',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.oauth2-provider','OAuth2 Provider','Built-in OAuth2 authorization server','devtank42','MIT','{Auth}','{oauth2,jwt,auth}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000018',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.pdf-generator','PDF Generator','Wkhtmltopdf-based PDF generation service','devtank42','MIT','{Export,Documents}','{pdf,documents,export}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000019',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.rate-limiter','Rate Limiter','Token-bucket rate limiting per user/IP','devtank42','MIT','{Security,Performance}','{rate-limiting,throttling,security}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000020',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.search-elasticsearch','Search Elasticsearch','Full-text search powered by Elasticsearch','devtank42','Apache-2.0','{Search,Data}','{elasticsearch,search,fulltext}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000021',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.session-manager','Session Manager','Distributed session storage with Redis','devtank42','MIT','{Auth,Performance}','{session,redis,auth}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000022',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.tenant-isolation','Tenant Isolation','Multi-tenancy schema isolation layer','devtank42','MIT','{Security,Multi-tenancy}','{multi-tenancy,isolation,saas}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000023',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.ui-theme-engine','UI Theme Engine','Dynamic theming with CSS variable injection','devtank42','MIT','{UI}','{theming,css,ui}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000024',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.version-check','Version Check','Automated dependency vulnerability scanning','devtank42','MIT','{Security,Operations}','{vulnerability,security,deps}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000025',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.webhook-router','Webhook Router','Configurable webhook fanout with retry','devtank42','MIT','{Integrations}','{webhook,integrations,http}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000026',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.xml-transformer','XML Transformer','XSLT-based XML transformation pipeline','devtank42','MIT','{Data,Transform}','{xml,xslt,transform}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000027',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.yaml-config','YAML Config','Hot-reloadable YAML configuration store','devtank42','MIT','{Operations}','{config,yaml,hot-reload}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000028',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.zip-archiver','ZIP Archiver','On-the-fly ZIP archive creation and streaming','devtank42','MIT','{Data,Export}','{zip,archive,streaming}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000029',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.db-migrator','DB Migrator','Schema migration automation for plugin databases','devtank42','MIT','{Database}','{migration,schema,database}','ACTIVE'),
  ('10000000-0000-0000-0000-000000000030',(SELECT id FROM namespace WHERE slug = 'default'),'io.plugwerk.analytics-tracker','Analytics Tracker','Event tracking and funnel analytics','devtank42','MIT','{Analytics}','{analytics,events,tracking}','ARCHIVED')
ON CONFLICT (namespace_id, plugin_id) DO NOTHING;

-- ============================================================
-- Plugins — acme-corp namespace
-- ============================================================
INSERT INTO plugin (id, namespace_id, plugin_id, name, description, author, license, categories, tags, status) VALUES
  ('20000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002','com.acme.crm-connector','CRM Connector','Bidirectional sync with Salesforce CRM','ACME Corp','AGPL-3.0','{Integrations,CRM}','{crm,salesforce,sync}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000002','com.acme.invoice-engine','Invoice Engine','Automated invoice generation and PDF delivery','ACME Corp','AGPL-3.0','{Finance,Documents}','{invoice,pdf,billing}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000002','com.acme.hr-onboarding','HR Onboarding','Employee onboarding workflow automation','ACME Corp','AGPL-3.0','{HR,Workflow}','{hr,onboarding,automation}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000002','com.acme.asset-tracker','Asset Tracker','IT asset inventory and lifecycle management','ACME Corp','AGPL-3.0','{IT,Operations}','{assets,inventory,lifecycle}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000005','00000000-0000-0000-0000-000000000002','com.acme.contract-vault','Contract Vault','Secure contract storage with digital signatures','ACME Corp','AGPL-3.0','{Legal,Documents}','{contracts,signatures,legal}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000006','00000000-0000-0000-0000-000000000002','com.acme.data-masking','Data Masking','PII masking and tokenisation for GDPR','ACME Corp','AGPL-3.0','{Security,Compliance}','{gdpr,pii,masking}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000007','00000000-0000-0000-0000-000000000002','com.acme.erp-bridge','ERP Bridge','SAP ERP integration via RFC and IDoc','ACME Corp','AGPL-3.0','{Integrations,ERP}','{sap,erp,integration}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000008','00000000-0000-0000-0000-000000000002','com.acme.fleet-monitor','Fleet Monitor','Real-time vehicle fleet GPS tracking','ACME Corp','AGPL-3.0','{IoT,Operations}','{gps,fleet,iot}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000009','00000000-0000-0000-0000-000000000002','com.acme.gdpr-console','GDPR Console','Data subject request management portal','ACME Corp','AGPL-3.0','{Compliance,Legal}','{gdpr,privacy,dsr}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000010','00000000-0000-0000-0000-000000000002','com.acme.helpdesk-ai','Helpdesk AI','AI-powered ticket routing and auto-response','ACME Corp','AGPL-3.0','{Support,AI}','{helpdesk,ai,tickets}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000011','00000000-0000-0000-0000-000000000002','com.acme.inventory-planner','Inventory Planner','Demand forecasting with reorder automation','ACME Corp','AGPL-3.0','{Operations,Supply Chain}','{inventory,forecast,supply-chain}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000012','00000000-0000-0000-0000-000000000002','com.acme.jira-sync','Jira Sync','Bidirectional Jira issue synchronisation','ACME Corp','AGPL-3.0','{Integrations,Dev}','{jira,sync,issues}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000013','00000000-0000-0000-0000-000000000002','com.acme.kpi-dashboard','KPI Dashboard','Executive KPI visualisation with drill-down','ACME Corp','AGPL-3.0','{Analytics,BI}','{kpi,dashboard,bi}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000014','00000000-0000-0000-0000-000000000002','com.acme.leave-manager','Leave Manager','Employee leave requests and approval workflow','ACME Corp','AGPL-3.0','{HR,Workflow}','{leave,hr,approval}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000015','00000000-0000-0000-0000-000000000002','com.acme.mfa-enforcer','MFA Enforcer','Enforce MFA policies across user groups','ACME Corp','AGPL-3.0','{Security,Auth}','{mfa,totp,security}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000016','00000000-0000-0000-0000-000000000002','com.acme.nda-generator','NDA Generator','One-click NDA generation and e-signature','ACME Corp','AGPL-3.0','{Legal,Documents}','{nda,legal,signature}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000017','00000000-0000-0000-0000-000000000002','com.acme.order-fulfillment','Order Fulfillment','End-to-end order processing automation','ACME Corp','AGPL-3.0','{Operations,Commerce}','{orders,fulfillment,automation}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000018','00000000-0000-0000-0000-000000000002','com.acme.payroll-bridge','Payroll Bridge','DATEV payroll export and bank transfer init','ACME Corp','AGPL-3.0','{Finance,HR}','{payroll,datev,finance}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000019','00000000-0000-0000-0000-000000000002','com.acme.quality-gate','Quality Gate','Automated QA checklist enforcement','ACME Corp','AGPL-3.0','{Quality,Operations}','{qa,quality,checklist}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000020','00000000-0000-0000-0000-000000000002','com.acme.risk-matrix','Risk Matrix','Risk assessment and mitigation tracking','ACME Corp','AGPL-3.0','{Compliance,Risk}','{risk,matrix,compliance}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000002','com.acme.sla-monitor','SLA Monitor','Service level agreement monitoring and alerts','ACME Corp','AGPL-3.0','{Operations,Monitoring}','{sla,alerts,monitoring}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000022','00000000-0000-0000-0000-000000000002','com.acme.ticket-priority','Ticket Priority','ML-based support ticket priority classifier','ACME Corp','AGPL-3.0','{Support,AI}','{ml,priority,tickets}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000023','00000000-0000-0000-0000-000000000002','com.acme.user-provisioning','User Provisioning','SCIM 2.0 automated user provisioning','ACME Corp','AGPL-3.0','{Auth,HR}','{scim,provisioning,idm}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000024','00000000-0000-0000-0000-000000000002','com.acme.vendor-portal','Vendor Portal','Self-service supplier onboarding portal','ACME Corp','AGPL-3.0','{Supply Chain,Operations}','{vendor,supplier,portal}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000025','00000000-0000-0000-0000-000000000002','com.acme.warehouse-wms','Warehouse WMS','Warehouse management with pick/pack/ship','ACME Corp','AGPL-3.0','{Operations,Supply Chain}','{wms,warehouse,logistics}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000026','00000000-0000-0000-0000-000000000002','com.acme.xero-connector','Xero Connector','Real-time sync with Xero accounting','ACME Corp','AGPL-3.0','{Finance,Integrations}','{xero,accounting,sync}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000027','00000000-0000-0000-0000-000000000002','com.acme.yield-optimizer','Yield Optimizer','Revenue yield optimisation with A/B testing','ACME Corp','AGPL-3.0','{Analytics,Finance}','{yield,ab-testing,revenue}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000028','00000000-0000-0000-0000-000000000002','com.acme.zero-trust-net','Zero Trust Network','Zero-trust network access policy enforcement','ACME Corp','AGPL-3.0','{Security}','{zero-trust,network,security}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000029','00000000-0000-0000-0000-000000000002','com.acme.approval-workflow','Approval Workflow','Configurable multi-step approval chains','ACME Corp','AGPL-3.0','{Workflow,Operations}','{approval,workflow,bpm}','ACTIVE'),
  ('20000000-0000-0000-0000-000000000030','00000000-0000-0000-0000-000000000002','com.acme.budget-tracker','Budget Tracker','Department budget tracking with forecast','ACME Corp','AGPL-3.0','{Finance,Operations}','{budget,forecast,finance}','ACTIVE')
ON CONFLICT (namespace_id, plugin_id) DO NOTHING;

-- ============================================================
-- Plugins — community namespace
-- ============================================================
INSERT INTO plugin (id, namespace_id, plugin_id, name, description, author, license, categories, tags, status) VALUES
  ('30000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000003','org.community.markdown-renderer','Markdown Renderer','Fast CommonMark renderer with syntax highlighting','community','MIT','{UI,Documents}','{markdown,rendering,syntax}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000003','org.community.chart-builder','Chart Builder','D3.js-powered chart and graph builder','community','MIT','{UI,Analytics}','{charts,d3,visualization}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000003','org.community.dark-mode','Dark Mode','System-aware dark/light theme toggle','community','MIT','{UI}','{dark-mode,theme,accessibility}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000003','org.community.form-builder','Form Builder','Drag-and-drop form designer with validation','community','MIT','{UI,Workflow}','{forms,drag-drop,validation}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000005','00000000-0000-0000-0000-000000000003','org.community.git-webhook','Git Webhook','Receive and process GitHub/GitLab webhooks','community','MIT','{DevOps,Integrations}','{git,webhook,cicd}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000006','00000000-0000-0000-0000-000000000003','org.community.image-optimizer','Image Optimizer','WebP conversion and lazy-load image optimizer','community','MIT','{Performance,Media}','{images,webp,performance}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000007','00000000-0000-0000-0000-000000000003','org.community.json-editor','JSON Editor','Tree-view JSON editor with schema validation','community','MIT','{UI,Developer Tools}','{json,schema,editor}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000008','00000000-0000-0000-0000-000000000003','org.community.keyboard-shortcuts','Keyboard Shortcuts','Global keyboard shortcut registry','community','MIT','{UI,Accessibility}','{keyboard,shortcuts,accessibility}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000009','00000000-0000-0000-0000-000000000003','org.community.log-viewer','Log Viewer','Real-time log stream viewer with search','community','MIT','{Developer Tools,Operations}','{logs,streaming,debug}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000010','00000000-0000-0000-0000-000000000003','org.community.modal-manager','Modal Manager','Accessible modal and dialog manager','community','MIT','{UI,Accessibility}','{modals,dialogs,aria}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000011','00000000-0000-0000-0000-000000000003','org.community.notification-toasts','Notification Toasts','Non-blocking toast notification system','community','MIT','{UI,Notifications}','{toasts,notifications,ui}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000012','00000000-0000-0000-0000-000000000003','org.community.offline-mode','Offline Mode','Service-worker-based offline capability','community','MIT','{Performance,PWA}','{offline,pwa,service-worker}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000013','00000000-0000-0000-0000-000000000003','org.community.print-adapter','Print Adapter','Print-friendly page renderer','community','MIT','{UI,Documents}','{print,css,documents}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000014','00000000-0000-0000-0000-000000000003','org.community.quick-search','Quick Search','Fuzzy global search with keyboard navigation','community','MIT','{UI,Search}','{search,fuzzy,keyboard}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000015','00000000-0000-0000-0000-000000000003','org.community.rich-text-editor','Rich Text Editor','ProseMirror-based collaborative text editor','community','MIT','{UI,Documents}','{editor,prosemirror,rich-text}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000016','00000000-0000-0000-0000-000000000003','org.community.split-view','Split View','Resizable split-pane layout manager','community','MIT','{UI}','{layout,split,resize}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000017','00000000-0000-0000-0000-000000000003','org.community.table-export','Table Export','One-click table data export to CSV/XLSX','community','MIT','{Data,Export}','{table,csv,xlsx}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000018','00000000-0000-0000-0000-000000000003','org.community.undo-redo','Undo/Redo','Application-wide undo/redo command stack','community','MIT','{UI,Developer Tools}','{undo,redo,history}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000019','00000000-0000-0000-0000-000000000003','org.community.virtualized-list','Virtualized List','High-performance virtual scrolling list','community','MIT','{Performance,UI}','{virtualization,scroll,performance}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000020','00000000-0000-0000-0000-000000000003','org.community.websocket-client','WebSocket Client','Managed WebSocket client with auto-reconnect','community','MIT','{Integrations,Realtime}','{websocket,realtime,reconnect}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000021','00000000-0000-0000-0000-000000000003','org.community.xss-sanitizer','XSS Sanitizer','DOMPurify-based XSS input sanitisation','community','MIT','{Security}','{xss,sanitize,security}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000022','00000000-0000-0000-0000-000000000003','org.community.yaml-viewer','YAML Viewer','Syntax-highlighted YAML viewer/editor','community','MIT','{Developer Tools,UI}','{yaml,viewer,editor}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000023','00000000-0000-0000-0000-000000000003','org.community.zip-drop','ZIP Drop','Drag-and-drop ZIP upload and extraction','community','MIT','{UI,Data}','{zip,upload,drag-drop}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000024','00000000-0000-0000-0000-000000000003','org.community.api-tester','API Tester','In-app REST API testing tool','community','MIT','{Developer Tools}','{api,rest,testing}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000025','00000000-0000-0000-0000-000000000003','org.community.breadcrumb-nav','Breadcrumb Nav','Auto-generated breadcrumb navigation','community','MIT','{UI,Navigation}','{breadcrumb,navigation,routing}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000026','00000000-0000-0000-0000-000000000003','org.community.color-picker','Color Picker','Accessible color picker with palette memory','community','MIT','{UI}','{color,picker,accessibility}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000027','00000000-0000-0000-0000-000000000003','org.community.drag-drop-upload','Drag & Drop Upload','Multi-file drag-and-drop upload with progress','community','MIT','{UI,Data}','{upload,drag-drop,progress}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000028','00000000-0000-0000-0000-000000000003','org.community.embed-map','Embed Map','OpenStreetMap tile embedding','community','MIT','{UI,Maps}','{map,osm,embed}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000029','00000000-0000-0000-0000-000000000003','org.community.file-browser','File Browser','Tree-based file system browser','community','MIT','{UI,Files}','{files,browser,tree}','ACTIVE'),
  ('30000000-0000-0000-0000-000000000030','00000000-0000-0000-0000-000000000003','org.community.gantt-chart','Gantt Chart','Interactive project timeline Gantt chart','community','MIT','{UI,Project Management}','{gantt,timeline,project}','ARCHIVED')
ON CONFLICT (namespace_id, plugin_id) DO NOTHING;

-- ============================================================
-- Releases — one PUBLISHED release per plugin
-- ============================================================
INSERT INTO plugin_release (id, plugin_id, version, artifact_sha256, artifact_key, requires_system_version, status, created_at, updated_at)
SELECT gen_random_uuid(), p.id, rel.version, rel.sha, (rel.ns_id || ':' || rel.plugin_id_str || ':' || rel.version || ':jar'), rel.req, 'PUBLISHED'::varchar, now()-rel.ago::interval, now()-rel.ago::interval
FROM plugin p
JOIN (VALUES
  -- default namespace
  ('io.plugwerk.auth-sso',           (SELECT id FROM namespace WHERE slug = 'default'),'1.3.2','a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2','artifacts/default/auth-sso/1.3.2.jar',           '>=2.0.0','5 days'),
  ('io.plugwerk.audit-log',           (SELECT id FROM namespace WHERE slug = 'default'),'2.1.0','b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3','artifacts/default/audit-log/2.1.0.jar',           '>=2.0.0','10 days'),
  ('io.plugwerk.backup-manager',      (SELECT id FROM namespace WHERE slug = 'default'),'1.0.5','c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4','artifacts/default/backup-manager/1.0.5.jar',      NULL,      '15 days'),
  ('io.plugwerk.cache-redis',         (SELECT id FROM namespace WHERE slug = 'default'),'3.0.1','d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5','artifacts/default/cache-redis/3.0.1.jar',         '>=3.0.0','3 days'),
  ('io.plugwerk.csv-export',          (SELECT id FROM namespace WHERE slug = 'default'),'1.4.0','e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6','artifacts/default/csv-export/1.4.0.jar',          NULL,      '20 days'),
  ('io.plugwerk.dashboard-widgets',   (SELECT id FROM namespace WHERE slug = 'default'),'2.3.1','f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7','artifacts/default/dashboard-widgets/2.3.1.jar',   NULL,      '8 days'),
  ('io.plugwerk.email-templates',     (SELECT id FROM namespace WHERE slug = 'default'),'1.1.3','a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8','artifacts/default/email-templates/1.1.3.jar',     NULL,      '25 days'),
  ('io.plugwerk.feature-flags',       (SELECT id FROM namespace WHERE slug = 'default'),'4.0.0','b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9','artifacts/default/feature-flags/4.0.0.jar',       '>=2.0.0','2 days'),
  ('io.plugwerk.geo-ip',              (SELECT id FROM namespace WHERE slug = 'default'),'1.0.2','c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0','artifacts/default/geo-ip/1.0.2.jar',              NULL,      '45 days'),
  ('io.plugwerk.health-checks',       (SELECT id FROM namespace WHERE slug = 'default'),'2.0.0','d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1','artifacts/default/health-checks/2.0.0.jar',       NULL,      '30 days'),
  ('io.plugwerk.i18n-manager',        (SELECT id FROM namespace WHERE slug = 'default'),'1.5.1','e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2','artifacts/default/i18n-manager/1.5.1.jar',        NULL,      '12 days'),
  ('io.plugwerk.job-scheduler',       (SELECT id FROM namespace WHERE slug = 'default'),'3.2.0','f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3','artifacts/default/job-scheduler/3.2.0.jar',       '>=2.5.0','7 days'),
  ('io.plugwerk.kafka-bridge',        (SELECT id FROM namespace WHERE slug = 'default'),'1.0.0','a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4','artifacts/default/kafka-bridge/1.0.0.jar',        '>=3.0.0','1 day'),
  ('io.plugwerk.ldap-sync',           (SELECT id FROM namespace WHERE slug = 'default'),'2.2.4','b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5','artifacts/default/ldap-sync/2.2.4.jar',           NULL,      '18 days'),
  ('io.plugwerk.metrics-collector',   (SELECT id FROM namespace WHERE slug = 'default'),'1.3.0','c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6','artifacts/default/metrics-collector/1.3.0.jar',   NULL,      '14 days'),
  ('io.plugwerk.notification-hub',    (SELECT id FROM namespace WHERE slug = 'default'),'2.0.3','d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7','artifacts/default/notification-hub/2.0.3.jar',    '>=2.0.0','6 days'),
  ('io.plugwerk.oauth2-provider',     (SELECT id FROM namespace WHERE slug = 'default'),'1.8.2','e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8','artifacts/default/oauth2-provider/1.8.2.jar',     '>=2.0.0','9 days'),
  ('io.plugwerk.pdf-generator',       (SELECT id FROM namespace WHERE slug = 'default'),'1.0.7','f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9','artifacts/default/pdf-generator/1.0.7.jar',       NULL,      '35 days'),
  ('io.plugwerk.rate-limiter',        (SELECT id FROM namespace WHERE slug = 'default'),'2.5.0','a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0','artifacts/default/rate-limiter/2.5.0.jar',        NULL,      '4 days'),
  ('io.plugwerk.search-elasticsearch',(SELECT id FROM namespace WHERE slug = 'default'),'0.9.1','b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1','artifacts/default/search-elasticsearch/0.9.1.jar','>=3.0.0','50 days'),
  ('io.plugwerk.session-manager',     (SELECT id FROM namespace WHERE slug = 'default'),'1.1.0','c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2','artifacts/default/session-manager/1.1.0.jar',     NULL,      '22 days'),
  ('io.plugwerk.tenant-isolation',    (SELECT id FROM namespace WHERE slug = 'default'),'1.0.1','d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3','artifacts/default/tenant-isolation/1.0.1.jar',    '>=2.0.0','40 days'),
  ('io.plugwerk.ui-theme-engine',     (SELECT id FROM namespace WHERE slug = 'default'),'3.1.0','e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4','artifacts/default/ui-theme-engine/3.1.0.jar',     NULL,      '11 days'),
  ('io.plugwerk.version-check',       (SELECT id FROM namespace WHERE slug = 'default'),'1.2.3','f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5','artifacts/default/version-check/1.2.3.jar',       NULL,      '17 days'),
  ('io.plugwerk.webhook-router',      (SELECT id FROM namespace WHERE slug = 'default'),'2.4.1','a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6','artifacts/default/webhook-router/2.4.1.jar',      NULL,      '13 days'),
  ('io.plugwerk.xml-transformer',     (SELECT id FROM namespace WHERE slug = 'default'),'1.0.0','b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7','artifacts/default/xml-transformer/1.0.0.jar',     NULL,      '90 days'),
  ('io.plugwerk.yaml-config',         (SELECT id FROM namespace WHERE slug = 'default'),'2.0.0','c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8','artifacts/default/yaml-config/2.0.0.jar',         NULL,      '28 days'),
  ('io.plugwerk.zip-archiver',        (SELECT id FROM namespace WHERE slug = 'default'),'1.3.5','d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9','artifacts/default/zip-archiver/1.3.5.jar',        NULL,      '16 days'),
  ('io.plugwerk.db-migrator',         (SELECT id FROM namespace WHERE slug = 'default'),'1.0.0','e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0','artifacts/default/db-migrator/1.0.0.jar',         NULL,      '55 days'),
  ('io.plugwerk.analytics-tracker',   (SELECT id FROM namespace WHERE slug = 'default'),'0.8.0','f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1','artifacts/default/analytics-tracker/0.8.0.jar',   NULL,      '120 days'),
  -- acme-corp namespace
  ('com.acme.crm-connector',         '00000000-0000-0000-0000-000000000002','2.1.0','a1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2','artifacts/acme-corp/crm-connector/2.1.0.jar',     '>=2.0.0','4 days'),
  ('com.acme.invoice-engine',        '00000000-0000-0000-0000-000000000002','3.0.1','b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3','artifacts/acme-corp/invoice-engine/3.0.1.jar',    NULL,      '7 days'),
  ('com.acme.hr-onboarding',         '00000000-0000-0000-0000-000000000002','1.4.2','c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4','artifacts/acme-corp/hr-onboarding/1.4.2.jar',     NULL,      '12 days'),
  ('com.acme.asset-tracker',         '00000000-0000-0000-0000-000000000002','1.1.0','d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5','artifacts/acme-corp/asset-tracker/1.1.0.jar',     NULL,      '20 days'),
  ('com.acme.contract-vault',        '00000000-0000-0000-0000-000000000002','2.0.0','e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6','artifacts/acme-corp/contract-vault/2.0.0.jar',    '>=2.0.0','3 days'),
  ('com.acme.data-masking',          '00000000-0000-0000-0000-000000000002','1.3.0','f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7','artifacts/acme-corp/data-masking/1.3.0.jar',      '>=2.0.0','9 days'),
  ('com.acme.erp-bridge',            '00000000-0000-0000-0000-000000000002','4.2.1','a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8','artifacts/acme-corp/erp-bridge/4.2.1.jar',        '>=3.0.0','2 days'),
  ('com.acme.fleet-monitor',         '00000000-0000-0000-0000-000000000002','1.0.3','b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9','artifacts/acme-corp/fleet-monitor/1.0.3.jar',     NULL,      '30 days'),
  ('com.acme.gdpr-console',          '00000000-0000-0000-0000-000000000002','2.5.0','c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0','artifacts/acme-corp/gdpr-console/2.5.0.jar',      '>=2.0.0','6 days'),
  ('com.acme.helpdesk-ai',           '00000000-0000-0000-0000-000000000002','1.2.0','d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1','artifacts/acme-corp/helpdesk-ai/1.2.0.jar',       '>=2.0.0','14 days'),
  ('com.acme.inventory-planner',     '00000000-0000-0000-0000-000000000002','2.0.0','e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2','artifacts/acme-corp/inventory-planner/2.0.0.jar',  NULL,      '18 days'),
  ('com.acme.jira-sync',             '00000000-0000-0000-0000-000000000002','1.5.3','f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3','artifacts/acme-corp/jira-sync/1.5.3.jar',         NULL,      '5 days'),
  ('com.acme.kpi-dashboard',         '00000000-0000-0000-0000-000000000002','3.1.0','a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4','artifacts/acme-corp/kpi-dashboard/3.1.0.jar',     NULL,      '11 days'),
  ('com.acme.leave-manager',         '00000000-0000-0000-0000-000000000002','1.0.8','b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5','artifacts/acme-corp/leave-manager/1.0.8.jar',     NULL,      '25 days'),
  ('com.acme.mfa-enforcer',          '00000000-0000-0000-0000-000000000002','2.2.0','c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6','artifacts/acme-corp/mfa-enforcer/2.2.0.jar',      '>=2.0.0','1 day'),
  ('com.acme.nda-generator',         '00000000-0000-0000-0000-000000000002','1.1.0','d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7','artifacts/acme-corp/nda-generator/1.1.0.jar',     NULL,      '40 days'),
  ('com.acme.order-fulfillment',     '00000000-0000-0000-0000-000000000002','2.3.4','e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8','artifacts/acme-corp/order-fulfillment/2.3.4.jar',  NULL,      '8 days'),
  ('com.acme.payroll-bridge',        '00000000-0000-0000-0000-000000000002','1.0.5','f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9','artifacts/acme-corp/payroll-bridge/1.0.5.jar',    NULL,      '35 days'),
  ('com.acme.quality-gate',          '00000000-0000-0000-0000-000000000002','1.7.0','a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0','artifacts/acme-corp/quality-gate/1.7.0.jar',      NULL,      '16 days'),
  ('com.acme.risk-matrix',           '00000000-0000-0000-0000-000000000002','2.0.1','b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1','artifacts/acme-corp/risk-matrix/2.0.1.jar',       NULL,      '22 days'),
  ('com.acme.sla-monitor',           '00000000-0000-0000-0000-000000000002','1.4.0','c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2','artifacts/acme-corp/sla-monitor/1.4.0.jar',       NULL,      '13 days'),
  ('com.acme.ticket-priority',       '00000000-0000-0000-0000-000000000002','0.9.0','d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3','artifacts/acme-corp/ticket-priority/0.9.0.jar',   '>=2.0.0','60 days'),
  ('com.acme.user-provisioning',     '00000000-0000-0000-0000-000000000002','1.3.1','e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4','artifacts/acme-corp/user-provisioning/1.3.1.jar',  '>=2.0.0','10 days'),
  ('com.acme.vendor-portal',         '00000000-0000-0000-0000-000000000002','2.1.3','f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5','artifacts/acme-corp/vendor-portal/2.1.3.jar',     NULL,      '27 days'),
  ('com.acme.warehouse-wms',         '00000000-0000-0000-0000-000000000002','3.0.0','a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6','artifacts/acme-corp/warehouse-wms/3.0.0.jar',     '>=2.5.0','15 days'),
  ('com.acme.xero-connector',        '00000000-0000-0000-0000-000000000002','1.2.0','b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7','artifacts/acme-corp/xero-connector/1.2.0.jar',    NULL,      '32 days'),
  ('com.acme.yield-optimizer',       '00000000-0000-0000-0000-000000000002','1.0.2','c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8','artifacts/acme-corp/yield-optimizer/1.0.2.jar',   NULL,      '45 days'),
  ('com.acme.zero-trust-net',        '00000000-0000-0000-0000-000000000002','2.4.0','d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9','artifacts/acme-corp/zero-trust-net/2.4.0.jar',    '>=3.0.0','3 days'),
  ('com.acme.approval-workflow',     '00000000-0000-0000-0000-000000000002','1.8.0','e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0','artifacts/acme-corp/approval-workflow/1.8.0.jar',  NULL,      '19 days'),
  ('com.acme.budget-tracker',        '00000000-0000-0000-0000-000000000002','1.0.0','f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1','artifacts/acme-corp/budget-tracker/1.0.0.jar',    NULL,      '70 days'),
  -- community namespace
  ('org.community.markdown-renderer',  '00000000-0000-0000-0000-000000000003','2.0.4','a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2','artifacts/community/markdown-renderer/2.0.4.jar',  NULL,'5 days'),
  ('org.community.chart-builder',      '00000000-0000-0000-0000-000000000003','1.3.0','b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3','artifacts/community/chart-builder/1.3.0.jar',      NULL,'11 days'),
  ('org.community.dark-mode',          '00000000-0000-0000-0000-000000000003','1.0.2','c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4','artifacts/community/dark-mode/1.0.2.jar',          NULL,'8 days'),
  ('org.community.form-builder',       '00000000-0000-0000-0000-000000000003','2.2.1','d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5','artifacts/community/form-builder/2.2.1.jar',       NULL,'3 days'),
  ('org.community.git-webhook',        '00000000-0000-0000-0000-000000000003','1.1.0','e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6','artifacts/community/git-webhook/1.1.0.jar',        NULL,'20 days'),
  ('org.community.image-optimizer',    '00000000-0000-0000-0000-000000000003','3.0.0','f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7','artifacts/community/image-optimizer/3.0.0.jar',    NULL,'14 days'),
  ('org.community.json-editor',        '00000000-0000-0000-0000-000000000003','1.5.2','a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8','artifacts/community/json-editor/1.5.2.jar',        NULL,'28 days'),
  ('org.community.keyboard-shortcuts', '00000000-0000-0000-0000-000000000003','1.0.0','b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9','artifacts/community/keyboard-shortcuts/1.0.0.jar', NULL,'60 days'),
  ('org.community.log-viewer',         '00000000-0000-0000-0000-000000000003','2.1.3','c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0','artifacts/community/log-viewer/2.1.3.jar',         NULL,'7 days'),
  ('org.community.modal-manager',      '00000000-0000-0000-0000-000000000003','1.2.0','d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1','artifacts/community/modal-manager/1.2.0.jar',      NULL,'16 days'),
  ('org.community.notification-toasts','00000000-0000-0000-0000-000000000003','1.4.1','e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2','artifacts/community/notification-toasts/1.4.1.jar',NULL,'9 days'),
  ('org.community.offline-mode',       '00000000-0000-0000-0000-000000000003','0.9.5','f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3','artifacts/community/offline-mode/0.9.5.jar',       NULL,'45 days'),
  ('org.community.print-adapter',      '00000000-0000-0000-0000-000000000003','1.0.1','a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4','artifacts/community/print-adapter/1.0.1.jar',      NULL,'35 days'),
  ('org.community.quick-search',       '00000000-0000-0000-0000-000000000003','2.3.0','b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5','artifacts/community/quick-search/2.3.0.jar',       NULL,'4 days'),
  ('org.community.rich-text-editor',   '00000000-0000-0000-0000-000000000003','1.8.0','c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6','artifacts/community/rich-text-editor/1.8.0.jar',   NULL,'12 days'),
  ('org.community.split-view',         '00000000-0000-0000-0000-000000000003','1.1.0','d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7','artifacts/community/split-view/1.1.0.jar',         NULL,'50 days'),
  ('org.community.table-export',       '00000000-0000-0000-0000-000000000003','2.0.2','e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8','artifacts/community/table-export/2.0.2.jar',       NULL,'6 days'),
  ('org.community.undo-redo',          '00000000-0000-0000-0000-000000000003','1.0.0','f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9','artifacts/community/undo-redo/1.0.0.jar',          NULL,'80 days'),
  ('org.community.virtualized-list',   '00000000-0000-0000-0000-000000000003','3.1.0','a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0','artifacts/community/virtualized-list/3.1.0.jar',   NULL,'2 days'),
  ('org.community.websocket-client',   '00000000-0000-0000-0000-000000000003','1.6.0','b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1','artifacts/community/websocket-client/1.6.0.jar',   NULL,'19 days'),
  ('org.community.xss-sanitizer',      '00000000-0000-0000-0000-000000000003','2.0.0','c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2','artifacts/community/xss-sanitizer/2.0.0.jar',      '>=2.0.0','10 days'),
  ('org.community.yaml-viewer',        '00000000-0000-0000-0000-000000000003','1.2.1','d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3','artifacts/community/yaml-viewer/1.2.1.jar',        NULL,'23 days'),
  ('org.community.zip-drop',           '00000000-0000-0000-0000-000000000003','1.0.4','e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4','artifacts/community/zip-drop/1.0.4.jar',           NULL,'17 days'),
  ('org.community.api-tester',         '00000000-0000-0000-0000-000000000003','2.1.0','f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5','artifacts/community/api-tester/2.1.0.jar',         NULL,'1 day'),
  ('org.community.breadcrumb-nav',     '00000000-0000-0000-0000-000000000003','1.0.0','a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6','artifacts/community/breadcrumb-nav/1.0.0.jar',     NULL,'90 days'),
  ('org.community.color-picker',       '00000000-0000-0000-0000-000000000003','1.3.5','b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7','artifacts/community/color-picker/1.3.5.jar',       NULL,'30 days'),
  ('org.community.drag-drop-upload',   '00000000-0000-0000-0000-000000000003','2.0.0','c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8','artifacts/community/drag-drop-upload/2.0.0.jar',   NULL,'40 days'),
  ('org.community.embed-map',          '00000000-0000-0000-0000-000000000003','1.1.2','d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9','artifacts/community/embed-map/1.1.2.jar',          NULL,'55 days'),
  ('org.community.file-browser',       '00000000-0000-0000-0000-000000000003','1.4.0','e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0','artifacts/community/file-browser/1.4.0.jar',       NULL,'25 days'),
  ('org.community.gantt-chart',        '00000000-0000-0000-0000-000000000003','0.7.0','f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1','artifacts/community/gantt-chart/0.7.0.jar',        NULL,'100 days')
) AS rel(plugin_id_str, ns_id, version, sha, key, req, ago)
ON p.plugin_id = rel.plugin_id_str AND p.namespace_id = rel.ns_id::uuid
ON CONFLICT (plugin_id, version) DO NOTHING;
