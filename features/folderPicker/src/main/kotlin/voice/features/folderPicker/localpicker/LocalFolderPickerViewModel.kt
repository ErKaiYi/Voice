package voice.features.folderPicker.localpicker

import android.content.Context
import android.os.storage.StorageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    // UI手动输入路径调用，不做exists前置校验，适配车机ROM特殊行为
    fun openRawPath(pathText: String) {
        val trimmed = pathText.trim()
        if (trimmed.isNotEmpty()) {
            val target = File(trimmed)
            scope.launch { load(target) }
        }
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
        // 内置存储保底，永远要有
        val internalStorage = File("/storage/emulated/0")
        if (internalStorage.exists() && internalStorage.canRead()) {
            roots.add(internalStorage)
        }

        try {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val getVolumePathsMethod = StorageManager::class.java.getDeclaredMethod("getVolumePaths")
            @Suppress("UNCHECKED_CAST")
            val volumePaths = getVolumePathsMethod.invoke(sm) as Array<String>
            for (path in volumePaths) {
                val f = File(path)
                if (!path.equals("/storage") && !path.equals("/mnt")
                    && !path.contains("emulated")
                    && f.exists() && f.canRead()
                ) {
                    roots.add(f)
                }
            }
        } catch (_: Exception) {
            // 安卓9车机ROM限制，/storage无法listFiles，放弃自动枚举U盘，依靠手动输入路径入口
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
