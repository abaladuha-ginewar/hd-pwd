package com.hdpwd.shared.platform

import com.hdpwd.shared.crypto.toWasmHex
import com.hdpwd.shared.crypto.wasmHexToBytes
import kotlin.js.JsString
import kotlin.js.Promise
import kotlinx.coroutines.await

/**
 * Web 备份导入/导出：导入走浏览器文件选择器，导出触发浏览器下载。
 */
class WasmBackupFilePort : BackupFilePort {
    override suspend fun openBackup(): ByteArray? {
        val picked = pickBackupFileHex().await<JsString?>() ?: return null
        val hex = picked.toString()
        if (hex.isEmpty()) return null
        return hex.wasmHexToBytes()
    }

    override suspend fun saveBackup(fileName: String, bytes: ByteArray): String {
        val safeName = fileName.replace(Regex("""[\\/:*?"<>|]"""), "_")
        triggerBackupDownloadHex(safeName, bytes.toWasmHex())
        return "浏览器下载：$safeName"
    }
}

/**
 * 打开系统文件选择器读取 `.dat`；取消时 Promise 解析为 null。
 * 在 JS 侧把文件编成 hex，避免 Kotlin/Wasm 读取 Uint8Array 丢字节。
 */
@JsFun(
    """
    () => new Promise((resolve) => {
      var settled = false;
      var chosen = false;
      var finish = function(value) {
        if (settled) return;
        settled = true;
        resolve(value);
      };
      var u8ToHex = function(u8) {
        var hex = '';
        for (var i = 0; i < u8.length; i++) {
          hex += ('0' + u8[i].toString(16)).slice(-2);
        }
        return hex;
      };
      var input = document.createElement('input');
      input.type = 'file';
      input.accept = '.dat,.bin,application/octet-stream';
      input.style.display = 'none';
      document.body.appendChild(input);
      var cleanup = function() {
        if (input.parentNode) input.parentNode.removeChild(input);
      };
      input.addEventListener('change', function() {
        var file = input.files && input.files[0];
        if (!file) {
          cleanup();
          finish(null);
          return;
        }
        chosen = true;
        file.arrayBuffer().then(function(buf) {
          cleanup();
          finish(u8ToHex(new Uint8Array(buf)));
        }, function() {
          cleanup();
          finish(null);
        });
      });
      input.addEventListener('cancel', function() {
        cleanup();
        finish(null);
      });
      window.setTimeout(function() {
        window.addEventListener('focus', function() {
          window.setTimeout(function() {
            if (!chosen) {
              cleanup();
              finish(null);
            }
          }, 500);
        }, { once: true });
      }, 400);
      input.click();
    })
    """,
)
private external fun pickBackupFileHex(): Promise<JsString?>

@JsFun(
    """
    (fileName, hex) => {
      var data = new Uint8Array(hex.length / 2);
      for (var i = 0; i < data.length; i++) {
        data[i] = parseInt(hex.substr(i * 2, 2), 16);
      }
      var blob = new Blob([data], { type: 'application/octet-stream' });
      var url = URL.createObjectURL(blob);
      var link = document.createElement('a');
      link.href = url;
      link.download = fileName;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    }
    """,
)
private external fun triggerBackupDownloadHex(fileName: String, hex: String)
