package com.hdpwd.shared.security

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.User32
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通过 WinRT `UserConsentVerifier` 弹出 Windows Hello。
 *
 * Desktop 是 Win32 窗口，优先 `IUserConsentVerifierInterop.RequestVerificationForWindowAsync`
 *（需 Windows 11）；失败则回退 `RequestVerificationAsync`。
 * 必须在能出系统 UI 的进程内调用；后台 IO 线程等待异步完成，避免卡住 Compose 消息循环。
 */
class WinRtWindowsHelloConsent : WindowsHelloConsent {
    @Volatile
    private var cachedAvailability: BiometricAvailability? = null

    /**
     * 非 Windows 直接不可用；Windows 上查询并缓存 Hello 能力。
     */
    override fun availability(): BiometricAvailability {
        if (!isWindowsOs()) return BiometricAvailability.UNAVAILABLE
        cachedAvailability?.let { return it }
        return synchronized(this) {
            cachedAvailability ?: queryAvailability().also { cachedAvailability = it }
        }
    }

    /**
     * 在 IO 线程请求 Hello，成功才返回。
     */
    override suspend fun requestVerification(message: String) {
        if (!isWindowsOs()) {
            error("当前系统不支持 Windows Hello")
        }
        withContext(Dispatchers.IO) {
            requestVerificationBlocking(message)
        }
    }

    private fun queryAvailability(): BiometricAvailability =
        runCatching {
            withFactory { factory ->
                val asyncOp = PointerByReference()
                checkHr(
                    factory.invoke(VTABLE_CHECK_AVAILABILITY, arrayOf(factory.pointer, asyncOp)),
                    "CheckAvailabilityAsync",
                )
                val value = waitAsyncInt(asyncOp.value, IID_ASYNC_AVAILABILITY)
                when (value) {
                    AVAIL_AVAILABLE -> BiometricAvailability.AVAILABLE
                    AVAIL_NOT_CONFIGURED -> BiometricAvailability.NOT_ENROLLED
                    else -> BiometricAvailability.UNAVAILABLE
                }
            }
        }.getOrElse { BiometricAvailability.UNAVAILABLE }

    private fun requestVerificationBlocking(message: String) {
        withFactory { factory ->
            val result = requestForWindow(factory, message) ?: requestWithoutWindow(factory, message)
            when (result) {
                RESULT_VERIFIED -> Unit
                RESULT_CANCELED -> error("已取消 Windows Hello 验证")
                else -> error("Windows Hello 验证失败（code=$result）")
            }
        }
    }

    /**
     * Win11+ 用前台窗口作为 Hello 对话框 owner，避免弹窗没有归属。
     */
    private fun requestForWindow(factory: ComObject, message: String): Int? {
        val hwnd = User32.INSTANCE.GetForegroundWindow() ?: return null
        if (Pointer.nativeValue(hwnd.pointer) == 0L) return null
        val interopRef = PointerByReference()
        val qi = factory.QueryInterface(Guid.REFIID(IID_INTEROP.pointer), interopRef)
        if (qi.toInt() < 0 || interopRef.value == null) return null
        val interop = ComObject(interopRef.value)
        return try {
            val hstring = createHString(message)
            try {
                val asyncOp = PointerByReference()
                val hr = interop.invoke(
                    VTABLE_REQUEST_FOR_WINDOW,
                    arrayOf(
                        interop.pointer,
                        hwnd,
                        hstring,
                        Guid.REFIID(IID_ASYNC_VERIFICATION.pointer),
                        asyncOp,
                    ),
                )
                if (hr < 0) return null
                waitAsyncInt(asyncOp.value, IID_ASYNC_VERIFICATION)
            } finally {
                deleteHString(hstring)
            }
        } finally {
            interop.Release()
        }
    }

    private fun requestWithoutWindow(factory: ComObject, message: String): Int {
        val hstring = createHString(message)
        try {
            val asyncOp = PointerByReference()
            checkHr(
                factory.invoke(
                    VTABLE_REQUEST_VERIFICATION,
                    arrayOf(factory.pointer, hstring, asyncOp),
                ),
                "RequestVerificationAsync",
            )
            return waitAsyncInt(asyncOp.value, IID_ASYNC_VERIFICATION)
        } finally {
            deleteHString(hstring)
        }
    }

    private fun <T> withFactory(block: (ComObject) -> T): T {
        ensureRoInitialized()
        val classId = createHString(CLASS_NAME)
        val factoryRef = PointerByReference()
        try {
            checkHr(
                Combase.INSTANCE.RoGetActivationFactory(
                    classId,
                    Guid.REFIID(IID_STATICS.pointer),
                    factoryRef,
                ),
                "RoGetActivationFactory",
            )
            val factory = ComObject(factoryRef.value ?: error("UserConsentVerifier 工厂为空"))
            try {
                return block(factory)
            } finally {
                factory.Release()
            }
        } finally {
            deleteHString(classId)
        }
    }

    /**
     * 轮询 IAsyncInfo，完成后从 IAsyncOperation.GetResults 读取枚举整型。
     */
    private fun waitAsyncInt(asyncPointer: Pointer?, asyncIid: Guid.GUID): Int {
        require(asyncPointer != null) { "WinRT 异步操作指针为空" }
        val asyncOp = ComObject(asyncPointer)
        try {
            val infoRef = PointerByReference()
            checkHr(asyncOp.QueryInterface(Guid.REFIID(IID_ASYNC_INFO.pointer), infoRef).toInt(), "QueryInterface IAsyncInfo")
            val info = ComObject(infoRef.value)
            try {
                val deadline = System.nanoTime() + ASYNC_TIMEOUT_NS
                while (true) {
                    val status = IntByReference()
                    checkHr(
                        info.invoke(VTABLE_ASYNC_STATUS, arrayOf(info.pointer, status)),
                        "IAsyncInfo.get_Status",
                    )
                    when (status.value) {
                        ASYNC_COMPLETED -> break
                        ASYNC_CANCELED -> error("Windows Hello 异步操作已取消")
                        ASYNC_ERROR -> error("Windows Hello 异步操作失败")
                        else -> {
                            if (System.nanoTime() > deadline) {
                                error("等待 Windows Hello 超时")
                            }
                            Thread.sleep(50)
                        }
                    }
                }
            } finally {
                info.Release()
            }
            val typedRef = PointerByReference()
            checkHr(asyncOp.QueryInterface(Guid.REFIID(asyncIid.pointer), typedRef).toInt(), "QueryInterface IAsyncOperation")
            val typed = ComObject(typedRef.value)
            try {
                val result = IntByReference()
                checkHr(
                    typed.invoke(VTABLE_GET_RESULTS, arrayOf(typed.pointer, result)),
                    "IAsyncOperation.GetResults",
                )
                return result.value
            } finally {
                typed.Release()
            }
        } finally {
            asyncOp.Release()
        }
    }

    private companion object {
        const val CLASS_NAME = "Windows.Security.Credentials.UI.UserConsentVerifier"
        const val RO_INIT_MULTITHREADED = 1
        const val S_OK = 0
        const val S_FALSE = 1
        const val RPC_E_CHANGED_MODE = 0x80010106.toInt()
        const val ASYNC_TIMEOUT_NS = 120_000_000_000L
        const val VTABLE_CHECK_AVAILABILITY = 6
        const val VTABLE_REQUEST_VERIFICATION = 7
        const val VTABLE_REQUEST_FOR_WINDOW = 6
        const val VTABLE_ASYNC_STATUS = 7
        const val VTABLE_GET_RESULTS = 8
        const val ASYNC_COMPLETED = 1
        const val ASYNC_CANCELED = 2
        const val ASYNC_ERROR = 3
        const val AVAIL_AVAILABLE = 0
        const val AVAIL_NOT_CONFIGURED = 2
        const val RESULT_VERIFIED = 0
        const val RESULT_CANCELED = 6

        val IID_STATICS: Guid.GUID = guid("AF4F3F91-564C-4DDC-B8B5-973447627C65")
        val IID_INTEROP: Guid.GUID = guid("39E050C3-4E74-441A-8DC0-B81104DF949C")
        val IID_ASYNC_INFO: Guid.GUID = guid("00000036-0000-0000-C000-000000000046")
        val IID_ASYNC_AVAILABILITY: Guid.GUID = guid("DDD384F3-D818-5D83-AB4B-32119C28587C")
        val IID_ASYNC_VERIFICATION: Guid.GUID = guid("FD596FFD-2318-558F-9DBE-D21DF43764A5")

        fun guid(value: String): Guid.GUID = Guid.GUID.fromString("{$value}")

        fun ensureRoInitialized() {
            val hr = Combase.INSTANCE.RoInitialize(RO_INIT_MULTITHREADED)
            if (hr != S_OK && hr != S_FALSE && hr != RPC_E_CHANGED_MODE) {
                error("RoInitialize 失败 HRESULT=0x${hr.toUInt().toString(16)}")
            }
        }

        fun createHString(text: String): Pointer {
            val out = PointerByReference()
            checkHr(
                Combase.INSTANCE.WindowsCreateString(WString(text), text.length, out),
                "WindowsCreateString",
            )
            return out.value ?: error("HSTRING 为空")
        }

        fun deleteHString(hstring: Pointer) {
            Combase.INSTANCE.WindowsDeleteString(hstring)
        }

        fun checkHr(hr: Int, action: String) {
            if (hr < 0) {
                error("$action 失败 HRESULT=0x${hr.toUInt().toString(16)}")
            }
        }
    }
}

/**
 * combase WinRT 入口：初始化、激活工厂和 HSTRING。
 */
private interface Combase : StdCallLibrary {
    fun RoInitialize(initType: Int): Int
    fun RoGetActivationFactory(
        activatableClassId: Pointer,
        iid: Guid.REFIID,
        factory: PointerByReference,
    ): Int
    fun WindowsCreateString(sourceString: WString, length: Int, string: PointerByReference): Int
    fun WindowsDeleteString(string: Pointer): Int

    companion object {
        val INSTANCE: Combase = Native.load("combase", Combase::class.java)
    }
}

/**
 * 按 vtable 槽调用 IInspectable/IUnknown 方法。
 */
private class ComObject(pointer: Pointer) : Unknown(pointer) {
    /**
     * 调用指定 vtable 槽并返回 HRESULT 整型。
     */
    fun invoke(vtableIndex: Int, args: Array<Any?>): Int =
        _invokeNativeInt(vtableIndex, args)
}
