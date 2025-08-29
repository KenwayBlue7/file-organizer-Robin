package com.kenway.robin

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kenway.robin.data.AppDatabase
import com.kenway.robin.data.ImageRepository
import com.kenway.robin.ui.organizer.OrganizerScreen
import com.kenway.robin.ui.theme.RobinTheme
import com.kenway.robin.viewmodel.OrganizerViewModel

class ViewModelFactory(private val application: Application, private val imageRepository: ImageRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OrganizerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OrganizerViewModel(imageRepository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = AppDatabase.getInstance(application)
        val repository = ImageRepository(application, database.tagDao())
        val viewModelFactory = ViewModelFactory(application, repository)

        setContent {
            RobinTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModelFactory)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModelFactory: ViewModelFactory) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "dashboard") {
        composable("dashboard") {
            DashboardScreen(navController = navController)
        }
        composable(
            route = "organizer/{folderUri}",
            arguments = listOf(navArgument("folderUri") { type = NavType.StringType })
        ) { backStackEntry ->
            val folderUriString = backStackEntry.arguments?.getString("folderUri") ?: return@composable
            val folderUri = Uri.parse(Uri.decode(folderUriString))
            val viewModel: OrganizerViewModel = viewModel(factory = viewModelFactory)
            
            LaunchedEffect(folderUri) {
                viewModel.loadImages(folderUri)
            }

            OrganizerScreen(viewModel = viewModel)
        }
    }
}

@Composable
fun DashboardScreen(navController: NavHostController) {
    val context = LocalContext.current

    // Determine the correct permission based on the Android version
    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasPermission = isGranted
        }
    )

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val encodedUri = Uri.encode(uri.toString())
                navController.navigate("organizer/$encodedUri")
            }
        }
    )

    // Request permission automatically when the screen is first shown
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(storagePermission)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = {
            if (hasPermission) {
                folderPickerLauncher.launch(null)
            } else {
                permissionLauncher.launch(storagePermission)
            }
        }) {
            Text("Pick a Folder to Organize")
        }
    }
}