package com.hdpwd.shared

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.domain.EditDraft
import com.hdpwd.shared.domain.PasswordEntry
import com.hdpwd.shared.domain.VaultEditor
import com.hdpwd.shared.domain.VaultQueries
import com.hdpwd.shared.domain.VaultState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * 验证目录层级、key 唯一性和递归搜索约束。
 */
class VaultEditorTest {
    /**
     * 根目录起算的三级目录不能继续创建子目录。
     */
    @Test
    fun folderDepthIsLimitedToThree() {
        val editor = VaultEditor()
        val root = VaultState(EntityId("vault"))
        val second = editor.addFolder(root, EntityId("f2"), null, "二级", "#EF4444")
        val third = editor.addFolder(second, EntityId("f3"), EntityId("f2"), "三级", "#EF4444")
        assertFails {
            editor.addFolder(third, EntityId("f4"), EntityId("f3"), "非法", "#EF4444")
        }
    }

    /**
     * 当前用户内 key 必须区分大小写且不可重复。
     */
    @Test
    fun duplicateKeyIsRejected() {
        val editor = VaultEditor()
        val id = EntityId("entry")
        val entry = PasswordEntry(id, null, "Example", "标题")
        val vault = editor.addEntry(VaultState(EntityId("vault")), entry)
        assertFails {
            editor.addEntry(vault, entry.copy(id = EntityId("other")))
        }
    }

    /**
     * 搜索应递归进入当前目录的后代文件夹。
     */
    @Test
    fun searchIncludesDescendants() {
        val editor = VaultEditor()
        val vault = editor.addFolder(VaultState(EntityId("vault")), EntityId("folder"), null, "工作", "#EF4444")
        val withEntry = editor.addEntry(
            vault,
            PasswordEntry(EntityId("entry"), EntityId("folder"), "GitHub.Work", "代码托管"),
        )
        val result = VaultQueries().search(withEntry, null, "github")
        assertEquals(1, result.size)
        assertEquals("工作", result.single().folderPath)
    }

    /**
     * 文件夹移动不能形成环，级联删除必须保留墓碑。
     */
    @Test
    fun moveAndCascadeDeletePreserveSyncIdentity() {
        val editor = VaultEditor()
        val root = VaultState(EntityId("vault"))
        val parent = editor.addFolder(root, EntityId("parent"), null, "父", "#EF4444")
        val child = editor.addFolder(parent, EntityId("child"), EntityId("parent"), "子", "#EF4444")
        val withEntry = editor.addEntry(
            child,
            PasswordEntry(EntityId("entry"), EntityId("child"), "Example", "示例"),
        )
        val deleted = editor.deleteFolder(
            withEntry,
            EntityId("parent"),
            timestamp = 10,
            transactionId = EntityId("tx"),
        )
        assertTrue(deleted.folders.isEmpty())
        assertTrue(deleted.entries.isEmpty())
        assertEquals(3, deleted.tombstones.size)
    }

    /**
     * 移动对象应保留身份，并拒绝目录环。
     */
    @Test
    fun movePreservesIdentityAndRejectsCycles() {
        val editor = VaultEditor()
        val root = editor.addFolder(VaultState(EntityId("vault")), EntityId("a"), null, "A", "#EF4444")
        val nested = editor.addFolder(root, EntityId("b"), EntityId("a"), "B", "#EF4444")
        val moved = editor.moveFolder(nested, EntityId("b"), null)
        assertEquals(null, moved.folders.single { it.id == EntityId("b") }.parentId)
        assertFails {
            editor.moveFolder(nested, EntityId("a"), EntityId("b"))
        }
    }

    /**
     * 取消编辑草稿时必须恢复原值。
     */
    @Test
    fun cancelDraftDoesNotCommit() {
        val draft = EditDraft("original").update("changed")
        assertEquals("original", draft.cancel())
        assertEquals("changed", draft.commit())
    }
}
