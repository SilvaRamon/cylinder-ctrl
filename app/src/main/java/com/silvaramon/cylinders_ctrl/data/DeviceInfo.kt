package com.silvaramon.cylinders_ctrl.data

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

object DeviceInfo {

    /**
     * Identificador do aparelho (`Settings.Secure.ANDROID_ID`).
     * Estável para este app/usuário no aparelho; muda apenas em reset de fábrica
     * ou se a chave de assinatura do app mudar.
     */
    @SuppressLint("HardwareIds")
    fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
}
