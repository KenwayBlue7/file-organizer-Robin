package com.kenway.robin

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kenway.robin.data.AppDatabase
import com.kenway.robin.data.ImageRepository
import com.kenway.robin.ui.organizer.OrganizerScreen
import com.kenway.robin.ui.theme.RobinTheme
import com.kenway.robin.viewmodel.OrganizerViewModel
import com.kenway.robin.viewmodel.SharedViewModel

private const val TAG = "MainActivity"

// This factory is now specific to OrganizerViewModel and will be created on-the-fly
class OrganizerViewModelFactory(
    private val application: Application,
    private val imageRepository: ImageRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OrganizerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OrganizerViewModel(application, imageRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Activity starting.")

        val database = AppDatabase.getInstance(application)
        val repository = ImageRepository(application, database.tagDao())
        Log.d(TAG, "onCreate: Database and Repository initialized.")
        val viewModelFactory = OrganizerViewModelFactory(application, repository)

        setContent {
            RobinTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavigation(navController = navController, viewModelFactory = viewModelFactory)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    viewModelFactory: OrganizerViewModelFactory // Assuming you still need this for OrganizerViewModel
) {
    val TAG = "AppNavigation"
    // Create the SharedViewModel, scoped to the whole NavHost
    val sharedViewModel: SharedViewModel = viewModel()

    NavHost(navController = navController, startDestination = "dashboard") {
        composable("dashboard") {
            Log.d(TAG, "AppNavigation: Navigated to dashboard.")
            // Pass the SharedViewModel to the Dashboard
            DashboardScreen(navController = navController, sharedViewModel = sharedViewModel)
        }
        composable(
            route = "organizer" // The route is now simpler, no more arguments
        ) { backStackEntry ->
            Log.d(TAG, "Navigated to organizer route")
            
            // Get the URI safely from the SharedViewModel
            val folderUri by sharedViewModel.selectedFolderUri.collectAsState()

            if (folderUri != null) {
                val organizerViewModel: OrganizerViewModel = viewModel(
                    viewModelStoreOwner = backStackEntry,
                    factory = viewModelFactory
                )
                
                LaunchedEffect(folderUri) {
                    Log.d(TAG, "LaunchedEffect triggered. Calling viewModel.loadImages.")
                    organizerViewModel.loadImages(folderUri!!)
                }

                OrganizerScreen(viewModel = organizerViewModel, navController = navController)
            } else {
                // This can happen if the user navigates here directly, handle gracefully
                Log.e(TAG, "OrganizerScreen was opened without a selected folder URI.")
                Text("Error: No folder selected.")
            }
        }
    }
}

@Composable
fun DashboardScreen(navController: NavHostController, sharedViewModel: SharedViewModel) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasPermission = isGranted
        Log.d(TAG, "PermissionLauncher result: isGranted = $isGranted")
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // 1. Take persistable permission
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            // 2. Put the URI in the SharedViewModel
            sharedViewModel.selectFolder(uri)
            // 3. Navigate with the simple route
            navController.navigate("organizer")
        } else {
            Log.d(TAG, "FolderPickerLauncher result: No folder selected.")
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (hasPermission) {
            Button(onClick = {
                Log.d(TAG, "Dashboard: 'Select Folder' button clicked.")
                folderPickerLauncher.launch(null)
            }) {
                Text("Select Folder to Organize")
            }
        } else {
            Button(onClick = {
                Log.d(TAG, "Dashboard: 'Request Permission' button clicked.")
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }) {
                Text("Request Permission")
            }
        }
    }
}