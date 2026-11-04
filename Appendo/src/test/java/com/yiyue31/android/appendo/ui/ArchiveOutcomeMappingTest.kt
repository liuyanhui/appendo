package com.yiyue31.android.appendo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 归档二选一决策映射全组合测试（修订二 §17.1 第三行 ①~⑤；M1 可测边界——编排层无 JVM 测试，
 * 语义保障＝本决策测试＋写盘段等价＋T-17 git diff 核对＋94(d) 真机四件套）。
 * 纯 JVM：被测对象为 MainScreen.kt 顶层纯函数，无 Android 依赖。
 */
class ArchiveOutcomeMappingTest {

    private val archiveName = "Appendo_20261104_120000.md"

    // ---------- ① Archived × 归档并清空 ----------

    @Test
    fun archived_archiveAndClear_toastsArchivedAndCleared_clearsAndCancelsAllReminders() {
        val action = archiveActionFor(
            ArchiveOutcome.Archived(File(archiveName)),
            ArchivePath.ARCHIVE_AND_CLEAR,
            archiveName
        )
        assertEquals(Microcopy.toastArchivedAndCleared(archiveName), action.toast)
        assertTrue("归档并清空路径应执行清空", action.shouldClear)
        assertTrue("归档并清空路径应取消全部提醒（需求 63 联动）", action.shouldCancelAllReminders)
    }

    // ---------- ② Archived × 只归档 ----------

    @Test
    fun archived_archiveOnly_toastsArchivedTo_keepsListAndReminders() {
        val action = archiveActionFor(
            ArchiveOutcome.Archived(File(archiveName)),
            ArchivePath.ARCHIVE_ONLY,
            archiveName
        )
        assertEquals(Microcopy.toastArchivedTo(archiveName), action.toast)
        assertFalse("只归档路径不得清空（需求 12 快照语义）", action.shouldClear)
        assertFalse("只归档路径不得动提醒", action.shouldCancelAllReminders)
    }

    // ---------- ③ Failed × 两路径（TD-017：均不 clear） ----------

    @Test
    fun failed_archiveAndClear_toastClearKept_neverClears() {
        val action = archiveActionFor(ArchiveOutcome.Failed, ArchivePath.ARCHIVE_AND_CLEAR, archiveName)
        assertEquals(Microcopy.TOAST_ARCHIVE_FAILED_CLEAR_KEPT, action.toast)
        assertFalse("TD-017：归档失败不得清空", action.shouldClear)
        assertFalse(action.shouldCancelAllReminders)
    }

    @Test
    fun failed_archiveOnly_toastArchiveFailed_neverClears() {
        val action = archiveActionFor(ArchiveOutcome.Failed, ArchivePath.ARCHIVE_ONLY, archiveName)
        assertEquals(Microcopy.TOAST_ARCHIVE_FAILED, action.toast)
        assertFalse(action.shouldClear)
        assertFalse(action.shouldCancelAllReminders)
    }

    // ---------- ④ NothingToArchive × 两路径（行为变更钉死：不清空＋守卫字面＋无其他动作） ----------

    @Test
    fun nothingToArchive_bothPaths_guardToastNoAction() {
        for (path in ArchivePath.entries) {
            val action = archiveActionFor(ArchiveOutcome.NothingToArchive, path, archiveName)
            assertEquals(
                "空内容＝守卫字面（$path）",
                Microcopy.TOAST_NOTHING_TO_ARCHIVE,
                action.toast
            )
            assertFalse("行为变更（A-3/T-3）：空内容不清空（$path）", action.shouldClear)
            assertFalse(action.shouldCancelAllReminders)
        }
    }

    // ---------- ⑤ 清空段失败映射 ----------

    @Test
    fun clearFailure_mapsToClearFailedArchived() {
        val action = clearFailedAction()
        assertEquals(Microcopy.TOAST_CLEAR_FAILED_ARCHIVED, action.toast)
        assertFalse("清空失败后无后续清空动作", action.shouldClear)
        assertFalse(action.shouldCancelAllReminders)
    }

    // ---------- 补充：成功字面句式族（§16.4：已动词＋对象＋逗号附注链式） ----------

    @Test
    fun successToasts_followCommaSuffixFamily() {
        assertTrue(Microcopy.toastArchivedAndCleared(archiveName).endsWith("，已清空"))
        assertTrue(Microcopy.toastArchivedTo(archiveName).startsWith("已归档到 "))
    }
}
