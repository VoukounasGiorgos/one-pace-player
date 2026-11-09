package gr.george.onepace

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import gr.george.onepace.data.AppSettings
import gr.george.onepace.data.Episode
import gr.george.onepace.data.Library
import gr.george.onepace.data.LibraryRepository
import gr.george.onepace.data.ProgressStore
import gr.george.onepace.ui.HomeActions
import gr.george.onepace.ui.HomeScreen
import gr.george.onepace.ui.OnePaceTheme
import gr.george.onepace.ui.SetupScreen
import gr.george.onepace.ui.StatusScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: LibraryViewModel by viewModels()

    private val folderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: SecurityException) {
                }
                vm.setFolder(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ProgressStore.init(this)

        val actions = HomeActions(
            onPlay = ::play,
            onToggleWatched = { ep ->
                if (ProgressStore.get(ep.id)?.watched == true) {
                    ProgressStore.markUnwatched(ep.id)
                } else {
                    ProgressStore.markWatched(listOf(ep.id))
                }
            },
            onMarkUntil = { ep ->
                val lib = vm.library.value
                if (lib != null) {
                    val idx = lib.indexOf(ep.id)
                    if (idx >= 0) ProgressStore.markWatched(lib.allEpisodes.take(idx + 1).map { it.id })
                }
            },
            onRescan = vm::rescan,
            onChangeFolder = ::pickFolder,
        )

        setContent {
            OnePaceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(Modifier.safeDrawingPadding()) {
                        App(actions)
                    }
                }
            }
        }
    }

    @Composable
    private fun App(actions: HomeActions) {
        val folder by vm.folder.collectAsState()
        val library by vm.library.collectAsState()
        val scanning by vm.scanning.collectAsState()
        val error by vm.error.collectAsState()
        val progress by ProgressStore.state.collectAsState()

        val lib = library
        when {
            folder == null -> SetupScreen(onPick = ::pickFolder)
            lib == null -> StatusScreen(scanning, error, onRetry = vm::rescan, onPick = ::pickFolder)
            else -> HomeScreen(lib, progress, scanning, actions)
        }
    }

    private fun pickFolder() {
        folderPicker.launch(null)
    }

    private fun play(ep: Episode, fromStart: Boolean) {
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_ID, ep.id)
                .putExtra(PlayerActivity.EXTRA_FROM_START, fromStart)
        )
    }
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val _folder = MutableStateFlow(AppSettings.treeUri(app))
    val folder: StateFlow<Uri?> = _folder

    val library: StateFlow<Library?> = LibraryRepository.library

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        if (_folder.value != null && library.value == null) rescan()
    }

    fun setFolder(uri: Uri) {
        AppSettings.setTreeUri(getApplication<Application>(), uri)
        _folder.value = uri
        LibraryRepository.clear()
        rescan()
    }

    fun rescan() {
        val tree = _folder.value ?: return
        if (_scanning.value) return
        viewModelScope.launch {
            _scanning.value = true
            _error.value = null
            try {
                LibraryRepository.scan(getApplication<Application>(), tree)
            } catch (e: Exception) {
                _error.value = e.message ?: e.javaClass.simpleName
            } finally {
                _scanning.value = false
            }
        }
    }
}
