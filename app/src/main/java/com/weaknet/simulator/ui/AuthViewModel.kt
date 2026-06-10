package com.weaknet.simulator.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.weaknet.simulator.auth.AuthAccount
import com.weaknet.simulator.auth.AuthStorage
import com.weaknet.simulator.auth.TotpGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = AuthStorage(application)

    private val _accounts = MutableStateFlow<List<AuthAccount>>(emptyList())
    val accounts: StateFlow<List<AuthAccount>> = _accounts

    private val _codes = MutableStateFlow<Map<String, String>>(emptyMap())
    val codes: StateFlow<Map<String, String>> = _codes

    private val _remainingSeconds = MutableStateFlow(30)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds

    init {
        _accounts.value = storage.load()
        refreshCodes()
        startTimer()
    }

    private fun startTimer() {
        viewModelScope.launch {
            while (true) {
                _remainingSeconds.value = TotpGenerator.getRemainingSeconds()
                if (_remainingSeconds.value == 30 || _codes.value.isEmpty()) {
                    refreshCodes()
                }
                delay(1000)
            }
        }
    }

    private fun refreshCodes() {
        val now = System.currentTimeMillis()
        _codes.value = _accounts.value.associate { account ->
            account.id to TotpGenerator.generateCode(account.secret, now, account.digits, account.period)
        }
    }

    fun addAccount(issuer: String, account: String, secret: String) {
        val cleaned = secret.uppercase().replace(" ", "").replace("-", "")
        val newAccount = AuthAccount(issuer = issuer, account = account, secret = cleaned)
        val updated = _accounts.value + newAccount
        _accounts.value = updated
        storage.save(updated)
        refreshCodes()
    }

    fun addFromUri(uri: String): Boolean {
        val account = AuthAccount.fromOtpauthUri(uri) ?: return false
        val updated = _accounts.value + account
        _accounts.value = updated
        storage.save(updated)
        refreshCodes()
        return true
    }

    fun deleteAccount(id: String) {
        val updated = _accounts.value.filter { it.id != id }
        _accounts.value = updated
        storage.save(updated)
        _codes.value = _codes.value - id
    }

    fun copyCode(code: String) {
        val ctx = getApplication<Application>()
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("验证码", code))
        Toast.makeText(ctx, "已复制验证码", Toast.LENGTH_SHORT).show()
    }
}
