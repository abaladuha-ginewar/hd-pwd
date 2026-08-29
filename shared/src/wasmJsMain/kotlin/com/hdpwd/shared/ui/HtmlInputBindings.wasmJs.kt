package com.hdpwd.shared.ui

/**
 * 在页面上创建或更新一个真实 DOM 输入框，供中文输入法使用。
 */
@JsFun(
    """
    (id, left, top, width, height, value, placeholder, enabled, password, multiline, isError, visible) => {
      var el = document.getElementById(id);
      var tag = multiline ? 'textarea' : 'input';
      if (el && el.tagName.toLowerCase() !== tag) {
        el.parentNode && el.parentNode.removeChild(el);
        el = null;
      }
      if (!el) {
        el = document.createElement(tag);
        el.id = id;
        el.className = 'hdpwd-html-input';
        el.autocomplete = 'off';
        el.autocapitalize = 'off';
        el.spellcheck = false;
        if (multiline) {
          el.style.resize = 'none';
        }
        document.body.appendChild(el);
      }
      if (!multiline) {
        el.type = password ? 'password' : 'text';
      }
      if (document.activeElement !== el && el.value !== value) {
        el.value = value;
      }
      el.placeholder = placeholder || '';
      el.disabled = !enabled;
      el.classList.toggle('hdpwd-html-input-error', !!isError);
      el.style.left = left + 'px';
      el.style.top = top + 'px';
      el.style.width = Math.max(0, width) + 'px';
      el.style.height = Math.max(0, height) + 'px';
      el.style.display = (visible && width > 2 && height > 2) ? 'block' : 'none';
    }
    """,
)
internal external fun htmlInputUpsert(
    id: String,
    left: Double,
    top: Double,
    width: Double,
    height: Double,
    value: String,
    placeholder: String,
    enabled: Boolean,
    password: Boolean,
    multiline: Boolean,
    isError: Boolean,
    visible: Boolean,
)

@JsFun("(id) => { var el = document.getElementById(id); return el ? el.value : null; }")
internal external fun htmlInputValue(id: String): String?

@JsFun("(id) => { var el = document.getElementById(id); return !!(el && document.activeElement === el); }")
internal external fun htmlInputFocused(id: String): Boolean

@JsFun("(id, value) => { var el = document.getElementById(id); if (el) el.value = value; }")
internal external fun htmlInputSetValue(id: String, value: String)

@JsFun("(id) => { var el = document.getElementById(id); if (el && el.parentNode) el.parentNode.removeChild(el); }")
internal external fun htmlInputRemove(id: String)

@JsFun("() => window.innerHeight")
internal external fun htmlWindowInnerHeight(): Double

/**
 * 按 placeholder（即字段 label）读取真实 DOM 输入值。
 */
@JsFun(
    """
    (label) => {
      var els = document.querySelectorAll('.hdpwd-html-input');
      for (var i = 0; i < els.length; i++) {
        if ((els[i].placeholder || '') === label) return els[i].value;
      }
      return null;
    }
    """,
)
private external fun snapshotDomTextFieldJs(label: String): String?

internal actual fun snapshotDomTextField(label: String): String? = snapshotDomTextFieldJs(label)
