#!/usr/bin/env bash
# T-27 (t) 造数脚本（可重放资产——101(t) 性能门槛：≥10 主题×数十条）
#
# 背景（TD-024/计划 §4）：realme RMX3560 的 FUSE 拒绝 run-as 访问
# /storage/emulated/0/Android/data/<pkg>/（外部应用目录）——「run-as 直写 topics/」配方 A
# 在本机不可达（probe 在案）。降级路径：TopicRepository.candidateTopicDirs 双目录合并扫描
# （外部优先＋displayName 去重）天然支持内部 files/topics 候选——本脚本经**内部目录**注入，
# listTopics/allTopicTimestampsLocked/EntryLocator 均会合并消费（分家场景同款语义）。
#
# 注意：注入的主题文件无 created_* SP 键——首见以 file.lastModified() 回写一次（§5.2，
# 排序稳定不漂移）。清理＝脚本尾部 clean 模式。
#
# 用法：
#   ./t27-seed-topics.sh seed [主题数=10] [每主题条数=30]
#   ./t27-seed-topics.sh clean
set -euo pipefail

ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
DEVICE="${DEVICE:-}"            # 多设备时指定 -s 序列号
PKG=com.yiyue31.android.appendo
TMP=/data/local/tmp/appendo-seed

adb_cmd() { if [ -n "$DEVICE" ]; then "$ADB" -s "$DEVICE" "$@"; else "$ADB" "$@"; fi; }

case "${1:-seed}" in
  clean)
    adb_cmd shell "rm -rf $TMP"
    adb_cmd shell "run-as $PKG rm -rf files/topics-seed-marker files/topics" || true
    echo "cleaned（内部 files/topics 整目录移除——若外部目录有同名主题不受影响）"
    exit 0
    ;;
  seed) ;;
  *) echo "usage: $0 [seed|clean] [topics=10] [entries=30]"; exit 1 ;;
esac

TOPICS="${2:-10}"; PER="${3:-30}"

# 1) 本地生成主题文件（FILE_HEADER＋条目块，§5.1 同构格式；ts 秒级旧格式合法——宽松正则兼容）
# Windows Git Bash：mktemp 的 /tmp 形态对 adb.exe 不可读——转 Windows 绝对路径（MSYS_NO_PATHCONV 下直传）
WORK="$(mktemp -d)"
WORK="$(cygpath -w "$WORK" 2>/dev/null || echo "$WORK")"
trap 'rm -rf "$WORK"' EXIT
trap 'rm -rf "$WORK"' EXIT
for t in $(seq 1 "$TOPICS"); do
  f="$WORK/seed$t.md"
  printf '# Appendo\n' > "$f"
  for e in $(seq 1 "$PER"); do
    ts=$(printf '2026-01-%02d %02d:00:00' "$t" "$e")
    printf -- '\n---\n\n## %s\n\n性能造数主题 %d 条目 %d（内部目录注入）\n\n---\n' "$ts" "$t" "$e" >> "$f"
  done
done

# 2) 推到设备中转目录（world-readable），再经 run-as 收进内部 files/topics
adb_cmd shell "rm -rf $TMP && mkdir -p $TMP"
for t in $(seq 1 "$TOPICS"); do
  adb_cmd push "$WORK/seed$t.md" "$TMP/seed$t.md" > /dev/null
  adb_cmd shell "chmod 644 $TMP/seed$t.md"
done
adb_cmd shell "run-as $PKG sh -c 'mkdir -p files/topics'"
for t in $(seq 1 "$TOPICS"); do
  adb_cmd shell "run-as $PKG sh -c 'cp $TMP/seed$t.md files/topics/seed$t.md'"
done
adb_cmd shell "rm -rf $TMP"

echo "seeded: $TOPICS topics × $PER entries → run-as $PKG files/topics/（内部候选目录）"
echo "验证：adb shell run-as $PKG ls files/topics | wc -l"
