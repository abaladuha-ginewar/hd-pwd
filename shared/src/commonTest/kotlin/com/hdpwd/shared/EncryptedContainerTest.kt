package com.hdpwd.shared

import com.hdpwd.shared.crypto.CryptoProvider
import com.hdpwd.shared.crypto.EncryptedContainerCodec
import com.hdpwd.shared.crypto.KdfParameters
import com.hdpwd.shared.crypto.platformCryptoProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails

/**
 * 验证加密容器的版本头、附加认证数据和载荷边界。
 */
class EncryptedContainerTest {
    /**
     * 容器应能使用密码学门面往返载荷。
     */
    @Test
    fun containerRoundTrip() = runTest {
        val codec = EncryptedContainerCodec(FakeCryptoProvider())
        val payload = "vault-payload".encodeToByteArray()
        val encoded = codec.seal(
            key = ByteArray(32),
            plaintext = payload,
            kdfParameters = KdfParameters(65_536, 3, 1),
            associatedData = "vault-id".encodeToByteArray(),
        )
        assertContentEquals(payload, codec.open(ByteArray(32), encoded))
    }

    /**
     * 未知格式版本必须拒绝读取。
     */
    @Test
    fun unknownVersionIsRejected() {
        runTest {
        val codec = EncryptedContainerCodec(FakeCryptoProvider())
        val encoded = codec.seal(ByteArray(32), byteArrayOf(1), KdfParameters(1, 1, 1))
        encoded[4] = 2
        assertFails { codec.open(ByteArray(32), encoded) }
        }
    }

    /**
     * 从安卓拷到 Windows 时可能出现的 BOM、前置字节、尾部填充和 UTF-16 误转码仍应能解密。
     */
    @Test
    fun transferArtifactsFromAndroidToWindowsAreAccepted() = runTest {
        val codec = EncryptedContainerCodec(platformCryptoProvider())
        val key = ByteArray(32) { 7 }
        val payload = "android-backup-payload".encodeToByteArray()
        val encoded = codec.seal(
            key = key,
            plaintext = payload,
            kdfParameters = KdfParameters(16, 1, 1),
            associatedData = "vault-id".encodeToByteArray(),
        )
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        assertContentEquals(payload, codec.open(key, bom + encoded))
        assertContentEquals(payload, codec.open(key, byteArrayOf(0, 1, 2) + encoded))
        assertContentEquals(payload, codec.open(key, encoded + ByteArray(200)))
        val sectorPad = (512 - encoded.size % 512) % 512
        if (sectorPad != 0) {
            assertContentEquals(payload, codec.open(key, encoded + ByteArray(sectorPad) { 0x5A }))
        }
        val utf16 = ByteArray(2 + encoded.size * 2)
        utf16[0] = 0xFF.toByte()
        utf16[1] = 0xFE.toByte()
        encoded.forEachIndexed { index, byte ->
            utf16[2 + index * 2] = byte
            utf16[3 + index * 2] = 0
        }
        assertContentEquals(payload, codec.open(key, utf16))
    }
}

/**
 * 只用于容器结构测试的确定性密码学门面，不参与生产构建。
 */
private class FakeCryptoProvider : CryptoProvider {
    override fun randomBytes(size: Int): ByteArray = ByteArray(size) { it.toByte() }

    override suspend fun argon2id(
        password: ByteArray,
        salt: ByteArray,
        parameters: KdfParameters,
    ): ByteArray = password.copyOf()

    override fun hkdfSha256(
        keyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray = ByteArray(length)

    override suspend fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray =
        aad + plaintext

    override suspend fun open(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray =
        ciphertext.copyOfRange(aad.size, ciphertext.size)
}
