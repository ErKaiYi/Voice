package voice.features.folderPicker.localpicker

import android.content.Context
import android.os.StorageManager
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import voice.core.common.DispatcherProvider
import voice.core.common.MainScope
import voice.navigation.Destination
import voice.navigation.Navigator
import voice.navigation.Origin

/**
 * In‑app folder browser for devices without a system file picker (e.g. modified car ROMs).
 * Browses local directories directly and hands the picked folder to the existing type selection.
 */
@AssistedInject
class LocalFolderPickerViewModel(
    private val navigator: Navigator,
    private val dispatcherProvider: DispatcherProvider,
    private val context: Context,
    @Assisted private val origin: Origin,
) {
    private val scope = MainScope(dispatcherProvider)
    /** Null means the top level with the available storage mounts. */
    var currentDir: File? by mutableStateOf(null)
        private set
    var entries: List<File> by mutableStateOf(emptyList())
        private set
    var loading: Boolean by mutableStateOf(false)
        private set

    fun loadRoots() {
        scope.launch { load(null) }
    }

    fun open(dir: File) {
        scope.launch { load(dir) }
    }

    fun goUp() {
        val parent = currentDir?.parentFile
        // stay within the known mounts; going above them returns to the root list
        scope.launch { load(parent?.takeIf { it.isReadableMount() }) }
    }

    fun pick() {
        val dir = currentDir ?: return
        navigator.goTo(Destination.SelectFolderType(uri = dir.toUri(), origin = origin))
    }

    fun close() {
        navigator.goBack()
    }

    private suspend fun load(dir: File?) {
        loading = true
        val list =
            withContext(dispatcherProvider.io) {
                if (dir == null) {
                    mountPoints()
                } else {
                    dir.listFiles()
                        ?.filter { it.isDirectory && it.canRead() && !it.name.startsWith(".") }
                        ?.sortedBy { it.name.lowercase() }
                        ?: emptyList()
                }
            }
        currentDir = dir
        entries = list
        loading = false
    }

    private fun mountPoints(): List<File> {
        val roots = mutableListOf<File>()
        // 1. 内置模拟存储
        val internalStorage = File("/storage/emulated/0")
        if (internalStorage.exists() && internalStorage.canRead()) {
            roots.add(internalStorage)
        }

        // 2. 反射 StorageManager getVolumePaths 获取真实挂载卷（Android9）
        try {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val getVolumePathsMethod = StorageManager::class.java.getDeclaredMethod("getVolumePaths")
            val volumePaths = getVolumePathsMethod.invoke(sm) as Array<String>
            for (path in volumePaths) {
                val f = File(path)
                // 排除父目录 /storage /mnt，只保留实际挂载点；排除 emulated（已添加）
                if (!path.equals("/storage") && !path.equals("/mnt")
                    && !path.contains("emulated")
                    && f.exists() && f.canRead()
                ) {
                    roots.add(f)
                }
            }
        } catch (_: Exception) {
            // 反射失败：兜底扫描 /storage 匹配 XXXX‑XXXX U盘命名格式
            val storageDir = File("/storage")
            storageDir.listFiles()?.forEach { f ->
                if (f.name.matches(Regex("[0‑9A‑F]{4}\\-[0‑9A‑F]{4}"))
                    && f.exists() && f.canRead()
                ) {
                    roots.add(f)
                }
            }
        }

        return roots
            .distinctBy { it.absolutePath }
            .sortedBy { it.name.lowercase() }
    }

    private fun File.isReadableMount(): Boolean =
        canRead() && (absolutePath.startsWith("/storage/") || absolutePath.startsWith("/mnt/"))

    @AssistedFactory
    interface Factory {
        fun create(origin: Origin): LocalFolderPickerViewModel
    }
}
