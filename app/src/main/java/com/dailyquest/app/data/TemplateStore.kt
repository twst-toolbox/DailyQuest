package com.dailyquest.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.dailyquest.app.core.script.GameScript
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 模板库 + 脚本存储
 *
 * 目录结构（应用私有目录，不需要存储权限）：
 * files/
 *   games/
 *     twst-jp/
 *       script.json          # 任务脚本
 *       meta.json            # 录入时分辨率等元数据
 *       templates/
 *         lesson_button.png
 *         rank_anchor.png
 */
class TemplateStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val gamesDir: File
        get() = File(context.filesDir, "games").apply { mkdirs() }

    fun gameDir(gameId: String): File =
        File(gamesDir, gameId).apply { mkdirs() }

    fun templatesDir(gameId: String): File =
        File(gameDir(gameId), "templates").apply { mkdirs() }

    /** 列出所有游戏 ID */
    fun listGames(): List<String> =
        gamesDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()

    /** 加载游戏脚本 */
    fun loadScript(gameId: String): GameScript? {
        val file = File(gameDir(gameId), "script.json")
        if (!file.exists()) return null
        return try {
            json.decodeFromString(GameScript.serializer(), file.readText())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** 保存游戏脚本 */
    fun saveScript(script: GameScript) {
        val file = File(gameDir(script.gameId), "script.json")
        file.writeText(json.encodeToString(GameScript.serializer(), script))
    }

    /** 加载模板位图 */
    fun loadTemplate(gameId: String, templateName: String): Bitmap? {
        val file = File(templatesDir(gameId), templateName)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    /** 保存模板位图（返回文件名） */
    fun saveTemplate(gameId: String, templateName: String, bitmap: Bitmap) {
        val file = File(templatesDir(gameId), templateName)
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    /** 模板元数据（录入时的分辨率） */
    data class TemplateMeta(
        val screenWidth: Int,
        val screenHeight: Int
    )

    fun saveMeta(gameId: String, meta: TemplateMeta) {
        File(gameDir(gameId), "meta.json").writeText(
            """{"screenWidth":${meta.screenWidth},"screenHeight":${meta.screenHeight}}"""
        )
    }

    fun loadMeta(gameId: String): TemplateMeta? {
        val file = File(gameDir(gameId), "meta.json")
        if (!file.exists()) return null
        return try {
            val text = file.readText()
            val w = Regex(""""screenWidth":(\d+)""").find(text)?.groupValues?.get(1)?.toInt()
            val h = Regex(""""screenHeight":(\d+)""").find(text)?.groupValues?.get(1)?.toInt()
            if (w != null && h != null) TemplateMeta(w, h) else null
        } catch (e: Exception) {
            null
        }
    }

    /** 列出模板文件 */
    fun listTemplates(gameId: String): List<String> =
        templatesDir(gameId).listFiles()?.map { it.name } ?: emptyList()
}
