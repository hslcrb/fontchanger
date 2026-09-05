package com.openfont.changer.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Root Shell (su) 및 Shizuku 바인더를 통한 특권 명령 실행기
 */
object RootShellExecutor {

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = BufferedReader(InputStreamReader(process.inputStream)).readLine()
            output?.contains("uid=0") == true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun executeSu(vararg commands: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            process.outputStream.bufferedWriter().use { writer ->
                for (cmd in commands) {
                    writer.write(cmd)
                    writer.newLine()
                }
                writer.write("exit")
                writer.newLine()
                writer.flush()
            }

            val exitCode = process.waitFor()
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()

            if (exitCode == 0) {
                Result.success(stdout)
            } else {
                Result.failure(Exception("Shell Error ($exitCode): $stderr"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun flashMagiskModule(zipFilePath: String): Result<String> {
        return executeSu(
            "magisk --install-module \"$zipFilePath\" || /data/adb/ksud module install \"$zipFilePath\""
        )
    }

    suspend fun softReboot(): Result<String> {
        return executeSu("killall zygote || killall zygote64 || setprop ctl.restart zygote")
    }
}
