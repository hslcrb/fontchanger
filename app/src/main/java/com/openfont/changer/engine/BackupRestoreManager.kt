package com.openfont.changer.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 폰트 설정 백업 및 순정 상태 원상복구 관리자
 */
object BackupRestoreManager {

    suspend fun removeMagiskFontModule(): Result<String> = withContext(Dispatchers.IO) {
        val cmds = arrayOf(
            "rm -rf /data/adb/modules/fontcraft_*",
            "rm -rf /data/adb/modules_update/fontcraft_*"
        )
        RootShellExecutor.executeSu(*cmds)
    }

    suspend fun clearFontCache(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 앱 내부 폰트 캐시 및 렌더링 캐시 삭제
            context.cacheDir.deleteRecursively()
            context.cacheDir.mkdirs()

            // 루트 권한이 있을 경우 안드로이드 폰트 캐시 삭제
            if (RootShellExecutor.isRootAvailable()) {
                RootShellExecutor.executeSu("rm -rf /data/system/font_cache/*")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
