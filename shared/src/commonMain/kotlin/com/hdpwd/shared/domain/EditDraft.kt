package com.hdpwd.shared.domain

/**
 * 编辑界面的临时草稿，取消时不会产生持久化状态或同步事件。
 */
data class EditDraft<T>(
    val original: T,
    val current: T = original,
) {
    /**
     * 返回替换当前草稿后的新实例。
     */
    fun update(value: T): EditDraft<T> = copy(current = value)

    /**
     * 丢弃修改并恢复原始值。
     */
    fun cancel(): T = original

    /**
     * 提交当前草稿。
     */
    fun commit(): T = current
}
