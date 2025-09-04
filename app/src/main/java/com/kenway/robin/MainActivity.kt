package com.kenway.robin

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.kenway.robin.data.AppDatabase
import com.kenway.robin.data.ImageRepository
import com.kenway.robin.data.TaggedFolder
import com.kenway.robin.ui.organizer.OrganizerScreen
import com.kenway.robin.ui.tagged.TaggedFolderScreen
import com.kenway.robin.ui.trash.TrashScreen
import com.kenway.robin.ui.theme.RobinTheme
import com.kenway.robin.viewmodel.DashboardViewModel
import com.kenway.robin.viewmodel.OrganizerViewModel
import com.kenway.robin.viewmodel.SharedViewModel
import com.kenway.robin.viewmodel.TrashViewModel
import com.kenway.robin.services.ScreenshotMonitorService
import android.os.Environment
import android.provider.Settings
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

// --- ViewModel Factories --- //
class OrganizerViewModelFactory(private val application: Application, private val imageRepository: ImageRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OrganizerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OrganizerViewModel(application, imageRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class DashboardViewModelFactory(private val application: Application, private val imageRepository: ImageRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(application, imageRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class TrashViewModelFactory(private val application: Application, private val imageRepository: ImageRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TrashViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TrashViewModel(imageRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


// --- Main Activity --- //
class MainActivity : ComponentActivity() {
    // Add this property
    private val deleteRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d("MainActivity", "Delete request approved")
        } else {
            Log.d("MainActivity", "Delete request denied")
        }
    }

    private lateinit var imageRepository: ImageRepository

    // Add this to MainActivity.kt
    private val screenshotReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.kenway.robin.SCREENSHOT_DETECTED") {
                val uriString = intent.getStringExtra("screenshot_uri")
                uriString?.let {
                    val uri = Uri.parse(it)
                    // Show notification or handle screenshot detection
                    handleScreenshotDetected(uri)
                }
            }
        }
    }

    private fun handleScreenshotDetected(uri: Uri) {
        // You can either:
        // 1. Automatically request deletion
        requestScreenshotDeletion(uri)

        // 2. Or show a notification asking user what to do
        // showScreenshotNotification(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize repository (add this)
        val database = AppDatabase.getInstance(application)
        imageRepository = ImageRepository(application, database.tagDao())

        val organizerViewModelFactory = OrganizerViewModelFactory(application, imageRepository)
        val dashboardViewModelFactory = DashboardViewModelFactory(application, imageRepository)
        val trashViewModelFactory = TrashViewModelFactory(application, imageRepository)

        setContent {
            RobinTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    AppNavigation(
                        navController = navController,
                        organizerViewModelFactory = organizerViewModelFactory,
                        dashboardViewModelFactory = dashboardViewModelFactory,
                        trashViewModelFactory = trashViewModelFactory
                    )
                }
            }
        }
    }

    // Add method to handle screenshot deletion requests
    fun requestScreenshotDeletion(uri: Uri) {
        lifecycleScope.launch {
            try {
                val deleteRequest = MediaStore.createDeleteRequest(
                    contentResolver,
                    listOf(uri)
                )

                // Handle the PendingIntent - you'll need to start it for result
                deleteRequestLauncher.launch(
                    IntentSenderRequest.Builder(deleteRequest.intentSender).build()
                )
            } catch (e: Exception) {
                Log.e("MainActivity", "Error creating delete request", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("com.kenway.robin.SCREENSHOT_DETECTED")
        registerReceiver(screenshotReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(screenshotReceiver)
    }
}

// --- Navigation --- //
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    navController: NavHostController,
    organizerViewModelFactory: OrganizerViewModelFactory,
    dashboardViewModelFactory: DashboardViewModelFactory,
    trashViewModelFactory: TrashViewModelFactory
) {
    val sharedViewModel: SharedViewModel = viewModel()

    // Create folder picker launcher at the navigation level
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                sharedViewModel.setSelectedFolderUri(uri)
                navController.navigate("organizer")
            }
        }
    )

    NavHost(navController = navController, startDestination = "dashboard") {
        composable("dashboard") {
            val dashboardViewModel: DashboardViewModel = viewModel(factory = dashboardViewModelFactory)
            DashboardScreen(
                navController = navController,
                sharedViewModel = sharedViewModel,
                viewModel = dashboardViewModel,
                folderPickerLauncher = folderPickerLauncher // Pass the launcher
            )
        }
        composable("organizer") { backStackEntry ->
            val folderUri by sharedViewModel.selectedFolderUri.collectAsState()
            if (folderUri != null) {
                val organizerViewModel: OrganizerViewModel = viewModel(
                    viewModelStoreOwner = backStackEntry,
                    factory = organizerViewModelFactory
                )

                LaunchedEffect(folderUri) {
                    organizerViewModel.loadImages(folderUri!!)
                }

                OrganizerScreen(
                    viewModel = organizerViewModel,
                    navController = navController,
                    sharedViewModel = sharedViewModel,
                    onOpenFolderPicker = {
                        folderPickerLauncher.launch(null) // Trigger folder picker
                    }
                )
            } else {
                Text("Error: No folder selected.")
            }
        }
        composable("trash") {
            val trashViewModel: TrashViewModel = viewModel(factory = trashViewModelFactory)
            TrashScreen(viewModel = trashViewModel)
        }
        composable("tagged_folder/{folderName}") { backStackEntry ->
            val folderName = backStackEntry.arguments?.getString("folderName") ?: ""
            TaggedFolderScreen(folderName = folderName, navController = navController)
        }
    }
}

// --- Dashboard Screen UI --- //
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavHostController,
    sharedViewModel: SharedViewModel,
    viewModel: DashboardViewModel,
    folderPickerLauncher: ActivityResultLauncher<Uri?>
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Enhanced permission state management
    var hasPermission by remember {
        mutableStateOf(checkStoragePermissions(context))
    }
    var permissionError by remember { mutableStateOf<String?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permissionDenialCount by remember { mutableStateOf(0) }

    // Load service state on startup
    LaunchedEffect(Unit) {
        viewModel.loadServiceState()
        // Refresh permission status when screen loads
        hasPermission = checkStoragePermissions(context)
    }

    // Observe navigation result for session saved
    LaunchedEffect(navController) {
        val currentBackStackEntry = navController.currentBackStackEntry
        currentBackStackEntry?.savedStateHandle?.getLiveData<Boolean>("session_saved")?.observeForever { sessionSaved ->
            if (sessionSaved == true) {
                viewModel.refreshFolders()
                currentBackStackEntry.savedStateHandle.remove<Boolean>("session_saved")
            }
        }
    }

    // Enhanced permission launchers
    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasPermission = isGranted || checkStoragePermissions(context)
            if (!hasPermission) {
                permissionDenialCount++
                permissionError = getPermissionErrorMessage(permissionDenialCount)
                showPermissionDialog = true
            } else {
                permissionDenialCount = 0
                permissionError = null
                // Refresh folders when permission is granted
                viewModel.refreshFolders()
            }
        }
    )

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            val newPermissionState = checkStoragePermissions(context)
            hasPermission = newPermissionState
            if (!newPermissionState) {
                permissionDenialCount++
                permissionError = "All Files Access permission is required to organize folders. Please enable it in Settings."
                showPermissionDialog = true
            } else {
                permissionDenialCount = 0
                permissionError = null
                // Refresh folders when permission is granted
                viewModel.refreshFolders()
            }
        }
    )

    // Enhanced permission dialog
    if (showPermissionDialog) {
        EnhancedPermissionDialog(
            permissionError = permissionError,
            denialCount = permissionDenialCount,
            onDismiss = {
                showPermissionDialog = false
                permissionError = null
            },
            onRetry = {
                showPermissionDialog = false
                requestStoragePermission(context, legacyPermissionLauncher, manageStorageLauncher)
            },
            onSettings = {
                showPermissionDialog = false
                openAppSettings(context)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                actions = {
                    IconButton(onClick = { navController.navigate("trash") }) {
                        Icon(Icons.Default.Delete, contentDescription = "Open Trash")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (hasPermission) {
                        folderPickerLauncher.launch(null)
                    } else {
                        requestStoragePermission(context, legacyPermissionLauncher, manageStorageLauncher)
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Organize a new folder")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
            Text(
                "Tagged Folders",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Enhanced permission status banner
            if (!hasPermission) {
                EnhancedPermissionBanner(
                    onGrantPermission = {
                        requestStoragePermission(context, legacyPermissionLauncher, manageStorageLauncher)
                    },
                    onOpenSettings = {
                        openAppSettings(context)
                    },
                    showSettingsOption = permissionDenialCount >= 2
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Screenshot monitoring section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Monitor for New Screenshots",
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.isMonitoringEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (hasPermission) {
                                startScreenshotService(context)
                                viewModel.setMonitoringEnabled(true)
                            } else {
                                // Show permission dialog for screenshot monitoring
                                permissionError = "Storage permission required for screenshot monitoring"
                                showPermissionDialog = true
                            }
                        } else {
                            stopScreenshotService(context)
                            viewModel.setMonitoringEnabled(false)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Content section
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                !hasPermission -> {
                    PermissionRequiredContent()
                }
                uiState.taggedFolders.isEmpty() -> {
                    EmptyFoldersContent()
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.taggedFolders) { folder ->
                            LocalFolderItem(taggedFolder = folder, navController = navController)
                        }
                    }
                }
            }
        }
    }
}

// LocalFolderItem composable
@Composable
private fun LocalFolderItem(
    taggedFolder: TaggedFolder,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable { 
                navController.navigate("tagged_folder/${taggedFolder.folderFile.name}")
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box {
            // Folder thumbnail
            AsyncImage(
                model = taggedFolder.thumbnailUri ?: android.R.drawable.ic_menu_gallery,
                contentDescription = "Folder: ${taggedFolder.folderFile.name}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Gradient overlay for better text visibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            ),
                            startY = 0.6f,
                            endY = 1.0f
                        )
                    ),
                contentAlignment = Alignment.BottomStart
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = taggedFolder.folderFile.name,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    
                    // Optional: Show image count
                    Text(
                        text = "${taggedFolder.folderFile.listFiles()?.size ?: 0} images",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// Enhanced permission banner
@Composable
fun EnhancedPermissionBanner(
    onGrantPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    showSettingsOption: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    "All Files Access Required"
                } else {
                    "Storage Permission Required"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    "Robin needs All Files Access to organize folders. This allows the app to move and organize files across your device."
                } else {
                    "Robin needs storage access to organize your files and folders."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(
                    onClick = onGrantPermission,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Grant Permission")
                }

                if (showSettingsOption) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onOpenSettings
                    ) {
                        Text("Open Settings")
                    }
                }
            }
        }
    }
}

@Composable
fun EnhancedPermissionDialog(
    permissionError: String?,
    denialCount: Int,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onSettings: () -> Unit
) {
    val title = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        "All Files Access Required"
    } else {
        "Storage Permission Required"
    }

    val message = permissionError ?: if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        "Robin needs All Files Access to organize your folders. Please enable this permission in the next screen."
    } else {
        "Robin needs storage access to organize your files. Please grant the permission."
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            if (denialCount >= 2) {
                androidx.compose.material3.TextButton(onClick = onSettings) {
                    Text("Open Settings")
                }
            } else {
                androidx.compose.material3.TextButton(onClick = onRetry) {
                    Text("Grant Permission")
                }
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PermissionRequiredContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Storage Access Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Grant storage permission to organize your folders",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyFoldersContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No tagged folders yet.")
            Text("Tap the '+' button to start organizing!")
        }
    }
}

// Enhanced helper functions
private fun getPermissionErrorMessage(denialCount: Int): String {
    return when {
        denialCount >= 3 -> "Permission denied multiple times. Please enable it manually in Settings > Apps > Robin > Permissions."
        denialCount >= 2 -> "Permission is required for the app to function. Please grant it in the settings."
        else -> "Storage permission required to access files."
    }
}

private fun openAppSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to open app settings", e)
        // Fallback to general settings
        try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to open settings", ex)
        }
    }
}

private fun checkStoragePermissions(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private fun requestStoragePermission(
    context: Context,
    legacyLauncher: ActivityResultLauncher<String>,
    manageStorageLauncher: ActivityResultLauncher<Intent>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Request All Files Access for Android 11+
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        manageStorageLauncher.launch(intent)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Request READ_MEDIA_IMAGES for Android 13+
        legacyLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        // Request READ_EXTERNAL_STORAGE for older versions
        legacyLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun startScreenshotService(context: Context) {
    ScreenshotMonitorService.startService(context)
}

private fun stopScreenshotService(context: Context) {
    ScreenshotMonitorService.stopService(context)
}