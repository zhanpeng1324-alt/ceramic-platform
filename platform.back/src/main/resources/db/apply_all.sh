#!/usr/bin/env bash
# =====================================================================
# apply_all.sh —— 按正确顺序将数据库结构应用到 MySQL
# ---------------------------------------------------------------------
# 用法：
#   # 已存在的老库（默认）：只按顺序执行 migration/V*.sql 增量脚本
#   ./apply_all.sh
#
#   # 全新空库：先建全量基线结构 schema.sql，再执行增量脚本
#   ./apply_all.sh --fresh
#
#   # 全新空库 + 演示数据（推荐给想开箱体验的人）：建库建表后灌入 demo-data.sql
#   ./apply_all.sh --fresh --demo
#
# 数据库连接从环境变量读取，未设置时使用本地开发默认值：
#   DB_NAME     (默认 ceramic)
#   DB_USER     (默认 root)
#   DB_PASSWORD (默认 123456)
#   DB_HOST     (默认 127.0.0.1)
#   DB_PORT     (默认 3306)
#
# 行为：执行前打印每个文件名；任一脚本出错立即停止（set -e）。
# =====================================================================
set -euo pipefail

DB_NAME="${DB_NAME:-ceramic}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-123456}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"

# 脚本所在目录（db/），保证从任意工作目录调用都能定位到文件
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MIGRATION_DIR="${SCRIPT_DIR}/migration"

FRESH=0
DEMO=0
for arg in "$@"; do
  case "$arg" in
    --fresh) FRESH=1 ;;
    --demo)  DEMO=1 ;;
    *) echo "未知参数：$arg（可用：--fresh、--demo）" >&2; exit 2 ;;
  esac
done

# migration 脚本的显式执行顺序。
# 注意：V3__audit_logs 必须在 V3_1__customization_quote 之前，
# 而按文件名字典序/版本序 V3_1 反而会排在 V3__ 之前，因此这里手工固定顺序。
MIGRATIONS=(
  "V2__ai_customer_service.sql"
  "V3__audit_logs.sql"
  "V3_1__customization_quote.sql"
  "V4__normalize_business_statuses.sql"
  "V5__admin_stats_indexes.sql"
  "V6__customization_flow.sql"
  "V7__after_sales_flow.sql"
  "V8__chat_ai_summary.sql"
  "V9__order_shipping.sql"
  "V10__review_reply.sql"
  "V11__payment_gateway.sql"
  "V12__customization_balance.sql"
  "V13__notifications.sql"
  "V14__shop_settings.sql"
  "V15__product_images.sql"
  "V16__customization_shipping_address.sql"
)

run_sql() {
  local file="$1"
  echo ">>> applying: ${file}"
  MYSQL_PWD="${DB_PASSWORD}" mysql --default-character-set=utf8mb4 \
    -u"${DB_USER}" -h"${DB_HOST}" -P"${DB_PORT}" "${DB_NAME}" < "${file}"
}

echo "==== target: ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME} ===="

if [ "${FRESH}" -eq 1 ]; then
  echo "==== --fresh: 先确保数据库存在 ===="
  MYSQL_PWD="${DB_PASSWORD}" mysql -u"${DB_USER}" -h"${DB_HOST}" -P"${DB_PORT}" \
    -e "CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;"
  echo "==== --fresh: 应用全量基线结构 schema.sql ===="
  run_sql "${SCRIPT_DIR}/schema.sql"
  echo "==== schema.sql 已建立完整结构（新库无需再叠加 V*.sql）===="
else
  echo "==== 老库增量升级：按顺序执行 migration/V*.sql ===="
  for m in "${MIGRATIONS[@]}"; do
    run_sql "${MIGRATION_DIR}/${m}"
  done
  echo "==== 增量脚本全部完成 ===="
fi

if [ "${DEMO}" -eq 1 ]; then
  echo "==== --demo: 灌入演示种子数据 demo-data.sql（账号密码均为 admin）===="
  run_sql "${SCRIPT_DIR}/demo-data.sql"
  echo "==== 演示数据就绪：管理员 13800000000 / 顾客 13800000001 / 客服 13800000003 ===="
fi

echo "==== 完成 ===="
