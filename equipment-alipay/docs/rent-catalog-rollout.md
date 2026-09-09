# Rent Miniapp Catalog rollout

## Existing ownership

- Public controller: `miniapp/catalog/MiniappCatalogController`
- Public application service: `miniapp/catalog/MiniappCatalogService`
- Persistence: MyBatis-Plus `GoodMapper`, `AttrMapper`, `CatalogCategoryMapper`
- Tables: `goods`, `goods_sku`, `category`
- Envelope and errors: `Result`, `ResultUtil`, `GlobalExceptionHandler`, `RentApiException`
- Trace and safe request timing: `TraceIdFilter`; logs never include request bodies or credentials
- Admin security: all management endpoints remain under `/api/web/**` and use the existing gateway interceptor

The legacy `category` model only had numeric ID/name/status and no stable code or hierarchy. It was not a
formal cross-client category-code contract. The migration therefore reuses exact same-name rows where possible,
then assigns the requested stable codes; it never classifies a product by its title.

## Migration and release order

1. Back up the database using the normal operations procedure.
2. Run `sql/rent_catalog_categories.sql` in a reviewed maintenance change. This also creates the independent catalog admin audit table.
3. Export the unclassified inventory by primary/business ID and have product/operations approve each leaf code.
4. Insert approved rows into `rent_catalog_goods_category_mapping`; do not mark uncertain goods approved.
5. Run `sql/rent_catalog_goods_mapping_apply.sql`. Require `unbound_public_count=0`, `conflict_count=0`,
   `invalid_public_cover_count=0`, `invalid_product_fact_count=0`, and `gate_status=PASS`.
6. Run `sql/rent_catalog_explain.sql` on the target database and retain the JSON plans with the change ticket.
7. Run `equipment-platform/src/main/resources/sql/platform_rbac_alipay_menu_delta.sql` so the rental admin receives the independent `catalog` page/buttons/API rules. Gateway catalog paths fail closed when a concrete rule is missing.

## Ownership boundary

- Rental goods own local product/SKU/inventory and optional Alipay publication state.
- Miniapp catalog owns the internal category tree, goods mapping, ordering, and miniapp visibility.
- Catalog create/update/disable/bind actions never invoke Alipay goods APIs and must not be presented as Alipay product classification.
- Category `icon_name` stores only a whitelisted antd-mini Icon name. It is rendered by the miniapp `ant-icon` component and is independent of image upload and `cover_image`.

## Category Icon migration

`sql/rent_catalog_categories.sql` adds `category.icon_name` through an `information_schema` guard, fills only blank formal-category values, and finally enforces `varchar(64) NOT NULL`. Re-running the migration does not add the column twice and does not overwrite a later valid icon selected in the rental admin.

Enabled categories must use one of the backend whitelist values. A missing or unknown icon causes an explicit business error during admin writes and public category-tree reads; there is no silent fallback. `cover_image` remains optional for compatibility only.
7. Deploy the backend and verify the old goods request plus the new category and category-filtered requests.
8. Release the miniapp so its home/category/search screens use the server category tree and `categoryCode`.
9. Monitor business error codes 4001-4004/5001, latency, empty-result rate, and database load before full traffic.

If rollback is needed, first roll the miniapp back to the old unfiltered list, roll back the backend artifact, then
run `sql/rent_catalog_goods_mapping_rollback.sql`. Structural columns and seed rows intentionally remain because
the older application ignores them; dropping columns during an incident would add avoidable risk.

## Mapping input example

The following is illustrative only. Replace values with manually verified real product IDs; never derive them
from product-title keywords.

```sql
INSERT INTO rent_catalog_goods_category_mapping
  (goods_id, category_code, review_status, reviewer, reviewed_at, note)
VALUES
  (123, 'drone-portable', 'APPROVED', 'reviewer-name', NOW(), 'checked against product master data')
ON DUPLICATE KEY UPDATE
  category_code=VALUES(category_code),
  review_status=VALUES(review_status),
  reviewer=VALUES(reviewer),
  reviewed_at=VALUES(reviewed_at),
  note=VALUES(note),
  applied_at=NULL;
```

## Public contract decisions

- `page` starts at 1; `pageSize` is 1-50. Invalid values are errors, not silently clamped.
- The formal catalog request contains no legacy `city`, `rentUnit`, or `pricingUnit` fields; clients must use only the packaged OpenAPI contract.
- `supportedRentUnits` and `defaultRentUnit` describe product facts derived from SKU/product data.
- Zero-stock listed products remain in ordinary lists and category counts. `onlyAvailable=true` excludes them.
- `goodsCount=null` when `includeGoodsCount=false`; `children` is always an array.
- `goodLabel`, `location`, and other absent nullable fields consistently serialize as `null`.
- Product/category images are complete HTTPS URLs. Invalid operational data produces an explicit error.
- `featured` is a real stored flag; the default is `false`. No random order or fabricated sales are used.
- Default sorting uses business order (`sort_order DESC`) with `goods_id ASC` as a stable tie-breaker.
- `POST /api/rent/v1/miniapp/catalog/goods/query` returns `sales` on every good. The backend performs one grouped order query for the current page, then maps each result by `goods_id`; it sums `rent_order.quantity` (missing or invalid historical quantity counts as 1) only for `PAID`, delivery/receipt/return fulfilment, and `FINISHED` orders. Created, signed, approval-pending, closed, cancellation, and refund orders are not sales.
- `sortBy=SALES` uses the same order-status and quantity expression as the returned `sales` field. Run `rent_catalog_categories.sql` before deploying the service; it idempotently creates `idx_rent_order_goods_id` for both aggregation paths.

## Frontend coordination

- Call `POST /api/rent/v1/miniapp/catalog/categories/query` before rendering home/category navigation.
- Send `categoryCode` and `includeDescendants=true` for a parent tab; send only the leaf code for leaf tabs.
- Replace any client title-keyword category inference with returned category codes/names.
- Treat nonzero envelope `code` as failure and surface/retry appropriately; never substitute demo data.
- Keep ordinary goods query for home recommendations unless product explicitly enables `featured` management.

The packaged OpenAPI contract is `src/main/resources/openapi/catalog-v1.yaml`.
