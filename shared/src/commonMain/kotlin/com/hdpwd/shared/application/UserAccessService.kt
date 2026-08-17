package com.hdpwd.shared.application

import com.hdpwd.shared.domain.EntityId
import com.hdpwd.shared.crypto.CryptoProvider
import com.hdpwd.shared.domain.VaultState
import kotlinx.serialization.Serializable

/**
 * 解锁前可显示的最小本地用户索引。
 */
@Serializable
data class LocalUserRecord(
    val id: EntityId,
    val username: String,
    val vaultLocation: String,
)

/**
 * 对本地用户索引执行唯一性和删除操作。
 */
class UserAccessService(
    private val crypto: CryptoProvider? = null,
) {
    /**
     * 创建不包含业务字段的随机用户记录。
     */
    fun createUserRecord(
        current: List<LocalUserRecord>,
        username: String,
    ): Pair<List<LocalUserRecord>, LocalUserRecord> {
        val provider = crypto ?: error("创建用户需要平台密码学提供者")
        val identifier = provider.randomBytes(16).toHex()
        val record = LocalUserRecord(
            id = EntityId(identifier),
            username = username,
            vaultLocation = "vault/$identifier.dat",
        )
        return addUser(current, record.id, record.username, record.vaultLocation) to record
    }

    /**
     * 校验用户名并追加本地用户记录。
     */
    fun addUser(
        current: List<LocalUserRecord>,
        id: EntityId,
        username: String,
        vaultLocation: String,
    ): List<LocalUserRecord> {
        require(username.isNotBlank()) { "用户名不能为空" }
        require(current.none { it.username == username }) { "用户名已存在" }
        require(vaultLocation.isNotBlank()) { "Vault 存储定位不能为空" }
        return current + LocalUserRecord(id, username, vaultLocation)
    }

    /**
     * 删除本地记录，不对远端执行任何删除操作。
     */
    fun removeUser(current: List<LocalUserRecord>, userId: EntityId): List<LocalUserRecord> =
        current.filterNot { it.id == userId }
}

/**
 * 将随机字节编码为路径安全的小写十六进制。
 */
private fun ByteArray.toHex(): String =
    joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

/**
 * 创建新用户所需的临时输入，不含本机主密码，不允许写入 VaultState。
 */
data class CreateUserInput(
    val username: String,
    val recoveryPassword: CharSequence,
    val importedVault: VaultState? = null,
)
