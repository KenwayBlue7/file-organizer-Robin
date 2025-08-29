package com.kenway.robin

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.kenway.robin.data.AppDatabase
import com.kenway.robin.data.ImageRepository
import com.kenway.robin.ui.organizer.OrganizerScreen
import com.kenway.robin.ui.theme.RobinTheme
import com.kenway.robin.ui.trash.TrashScreen
import com.kenway.robin.viewmodel.DashboardViewModel
import com.kenway.robin.viewmodel.OrganizerViewModel
import com.kenway.robin.viewmodel.SharedViewModel
import com.kenway.robin.viewmodel.TrashViewModel

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

class TrashViewModelFactory(private val imageRepository: ImageRepository) : ViewModelProvider.Factory {
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = AppDatabase.getInstance(application)
        val repository = ImageRepository(application, database.tagDao())
        val organizerViewModelFactory = OrganizerViewModelFactory(application, repository)
        val dashboardViewModelFactory = DashboardViewModelFactory(application, repository)
        val trashViewModelFactory = TrashViewModelFactory(repository)

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

    NavHost(navController = navController, startDestination = "dashboard") {
        composable("dashboard") {
            val dashboardViewModel: DashboardViewModel = viewModel(factory = dashboardViewModelFactory)
            DashboardScreen(
                navController = navController,
                sharedViewModel = sharedViewModel,
                viewModel = dashboardViewModel
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

                OrganizerScreen(viewModel = organizerViewModel, navController = navController)
            } else {
                Text("Error: No folder selected.")
            }
        }
        composable("trash") {
            val trashViewModel: TrashViewModel = viewModel(factory = trashViewModelFactory)
            TrashScreen(viewModel = trashViewModel)
        }
    }
}

// --- Dashboard Screen UI --- //
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavHostController,
    sharedViewModel: SharedViewModel,
    viewModel: DashboardViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // --- Permission and Folder Picker Logic (same as before) --- //
    // ...

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            sharedViewModel.selectFolder(uri)
            navController.navigate("organizer")
        }
    }

    // Scaffold provides the basic app layout structure (like top bar, FAB, etc.)
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
            FloatingActionButton(onClick = { folderPickerLauncher.launch(null) }) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Monitor for New Screenshots",
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.isServiceEnabled,
                    onCheckedChange = { viewModel.toggleMonitoringService(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.taggedFolders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tagged folders yet. Tap the '+' button to start!")
                }
            } else {
                // LazyVerticalGrid displays items in a grid and is very efficient
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.taggedFolders) { folder ->
                        FolderItem(taggedFolder = folder)
                    }
                }
            }
        }
    }
}

@Composable
fun FolderItem(taggedFolder: com.kenway.robin.data.TaggedFolder) {
    Card(
        modifier = Modifier
            .aspectRatio(1f) // Makes the card a square
            .clickable { /* TODO: Navigate to a screen to view this folder's contents */ },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box {
            // Folder thumbnail
            AsyncImage(
                model = taggedFolder.thumbnailUri,
                contentDescription = "Thumbnail for ${taggedFolder.folderFile.name}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = {
                    Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                         Text("No thumbnail", style = MaterialTheme.typography.bodySmall)
                    }
                }
            )
            // Folder name overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text = taggedFolder.folderFile.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}