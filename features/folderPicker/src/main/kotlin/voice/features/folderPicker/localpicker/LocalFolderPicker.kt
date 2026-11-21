package voice.features.folderPicker.localpicker
import androidx.compose.runtime.Composable
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation3.runtime.NavEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import voice.core.common.rootGraphAs
import voice.core.strings.R as StringsR
import voice.core.ui.AuroraBackground
import voice.core.ui.MaxContentWidth
import voice.core.ui.OnboardingButton
import voice.core.ui.OnboardingTopBar
import voice.core.ui.R as UiR
import voice.navigation.Destination
import voice.navigation.NavEntryProvider
import voice.navigation.Origin

@ContributesTo(AppScope::class)
interface LocalFolderPickerGraph {
    val localFolderPickerFactory: LocalFolderPickerViewModel.Factory
}

@BindingContainer
@ContributesTo(AppScope::class)
object LocalFolderPickerProvider {
    @Provides
    @IntoSet
    fun localFolderPickerNavEntryProvider(): NavEntryProvider<*> =
        NavEntryProvider<Destination.LocalFolderPicker> { key ->
            NavEntry(key) {
                LocalFolderPicker(origin = key.origin)
            }
        }
}

@Composable
fun LocalFolderPicker(origin: Origin) {
    val viewModel: LocalFolderPickerViewModel =
        retain(origin.name) {
            rootGraphAs<LocalFolderPickerGraph>().localFolderPickerFactory.create(origin)
        }
    val context = LocalContext.current
    val permission = Manifest.permission.READ_EXTERNAL_STORAGE
    var hasPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasPermission = granted
        }
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.loadRoots()
        } else {
            permissionLauncher.launch(permission)
        }
    }
    Box(Modifier.fillMaxSize()) {
        AuroraBackground(clock = { 0F }, showStars = false, modifier = Modifier.fillMaxSize())
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = {
                OnboardingTopBar(step = null, onBack = viewModel::close)
            },
        ) { contentPadding ->
            when {
                !hasPermission ->
                    PermissionDenied(
                        onRequest = { permissionLauncher.launch(permission) },
                        modifier = Modifier.fillMaxSize().padding(contentPadding),
                    )
                else ->
                    FolderList(
                        viewModel = viewModel,
                        contentPadding = contentPadding,
                        modifier = Modifier.fillMaxSize(),
                    )
            }
        }
    }
}

@Composable
private fun FolderList(
    viewModel: LocalFolderPickerViewModel,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var showManualPathDialog by rememberSaveable { mutableStateOf(false) }
    var manualPathInput by rememberSaveable { mutableStateOf("") }

    if (showManualPathDialog) {
        AlertDialog(
            onDismissRequest = { showManualPathDialog = false },
            title = { Text("手动输入目录路径") },
            text = {
                OutlinedTextField(
                    value = manualPathInput,
                    onValueChange = { manualPathInput = it },
                    placeholder = { Text("/storage/287C-8A31") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.openRawPath(manualPathInput)
                    showManualPathDialog = false
                    manualPathInput = ""
                }) {
                    Text("跳转")
                }
            },
            dismissButton = {
                Button(onClick = {
                    showManualPathDialog = false
                    manualPathInput = ""
                }) {
                    Text("取消")
                }
            }
        )
    }

    val currentDir = viewModel.currentDir
    LazyColumn(
        modifier = modifier,
        contentPadding =
        contentPadding + PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "title") {
            Column(
                modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = currentDir?.name ?: stringResource(StringsR.string.folder_local_title),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                currentDir?.absolutePath?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (currentDir != null) {
            item(key = "up") {
                FolderRow(
                    name = stringResource(StringsR.string.folder_local_up),
                    onClick = viewModel::goUp,
                    modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth(),
                )
            }
        }
        itemsIndexed(viewModel.entries, key = { _, file -> file.absolutePath }) { _, file ->
            FolderRow(
                name = file.name,
                onClick = { viewModel.open(file) },
                modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth(),
            )
        }
        if (viewModel.entries.isEmpty() && !viewModel.loading) {
            item(key = "empty") {
                Text(
                    text = stringResource(StringsR.string.folder_local_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().padding(24.dp),
                )
            }
        }
        item(key = "pick") {
            Column(
                modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OnboardingButton(
                    text = stringResource(StringsR.string.folder_local_pick),
                    onClick = viewModel::pick,
                    enabled = currentDir != null,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OnboardingButton(
                    text = "手动输入路径(OTG-U盘)",
                    onClick = { showManualPathDialog = true }
                )
            }
        }
    }
}

@Composable
private fun FolderRow(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_folder),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1F),
            )
            Icon(
                painter = painterResource(UiR.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionDenied(
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(StringsR.string.folder_local_permission_title),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(StringsR.string.folder_local_permission_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OnboardingButton(
            text = stringResource(StringsR.string.folder_local_permission_request),
            onClick = onRequest,
        )
    }
}
