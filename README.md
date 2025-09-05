# Robin - Smart Photo Organization App

Robin is an intelligent Android application designed to help users organize their photos efficiently. The app provides automated screenshot detection, folder organization, and a comprehensive trash management system.

## Features

### 📁 Smart Photo Organization
- **Folder-based Organization**: Select any folder and organize photos within it
- **Tagged Folders**: Create and manage categorized photo collections
- **Visual Grid Layout**: View folders in an intuitive 3-column grid with thumbnails
- **File Management**: Move, organize, and manage photos across different folders

### 📸 Screenshot Monitoring
- **Automatic Detection**: Real-time monitoring for new screenshots
- **Background Service**: Continuous monitoring service that runs in the background
- **User Control**: Toggle screenshot monitoring on/off from the dashboard
- **Smart Handling**: Automatic handling of detected screenshots

### 🗑️ Advanced Trash System
- **Secure Deletion**: Move files to trash before permanent deletion
- **Restore Functionality**: Easily restore accidentally deleted photos
- **Bulk Operations**: Delete all trashed items at once with confirmation
- **Visual Trash Management**: Grid view of trashed photos with thumbnails

### 🎨 Modern UI/UX
- **Material 3 Design**: Clean, modern interface following Material Design principles
- **Dark/Light Theme Support**: Adaptive theming based on system preferences
- **Intuitive Navigation**: Easy-to-use navigation between different sections
- **Responsive Layout**: Optimized for different screen sizes

## Technology Stack

### Frontend
- **Kotlin** - Primary programming language
- **Jetpack Compose** - Modern UI toolkit for building native Android UI
- **Material 3** - Google's latest design system implementation

### Architecture
- **MVVM Pattern** - Model-View-ViewModel architecture
- **Repository Pattern** - Clean separation of data sources
- **StateFlow/Coroutines** - Reactive programming and asynchronous operations

### Database
- **Room Database** - Local database for storing app data
- **SQLite** - Underlying database engine

### Image Loading
- **Coil** - Image loading library for efficient image display

### Navigation
- **Navigation Component** - Type-safe navigation between screens

## Permissions

The app requires the following permissions:

### Android 11+ (API 30+)
- **All Files Access** (`MANAGE_EXTERNAL_STORAGE`) - Required to organize files across the device

### Android 13+ (API 33+)
- **Read Media Images** (`READ_MEDIA_IMAGES`) - Required to access and display photos

### Legacy Android (Below API 30)
- **Read External Storage** (`READ_EXTERNAL_STORAGE`) - Required to access device storage

## Installation

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK 24 or higher
- Kotlin 1.8.0 or later

### Setup Steps

1. **Clone the repository**
   ```bash
   git clone https://github.com/KenwayBlue7/file-organizer-Robin.git
   cd robin
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory and select it

3. **Sync project**
   - Android Studio will automatically sync the project
   - Wait for all dependencies to download

4. **Run the app**
   - Connect an Android device or start an emulator
   - Click the "Run" button or press `Shift + F10`

## Project Structure

```
app/
├── src/main/java/com/kenway/robin/
│   ├── data/                    # Data layer
│   │   ├── AppDatabase.kt       # Room database configuration
│   │   ├── ImageRepository.kt   # Data repository
│   │   └── TaggedFolder.kt      # Data models
│   ├── services/                # Background services
│   │   └── ScreenshotMonitorService.kt
│   ├── ui/                      # UI layer
│   │   ├── organizer/          # Photo organization screens
│   │   ├── tagged/             # Tagged folder screens
│   │   ├── trash/              # Trash management screens
│   │   └── theme/              # App theming
│   ├── viewmodel/              # ViewModels
│   │   ├── DashboardViewModel.kt
│   │   ├── OrganizerViewModel.kt
│   │   ├── SharedViewModel.kt
│   │   └── TrashViewModel.kt
│   └── MainActivity.kt         # Main activity
```

## Key Components

### ViewModels
- **DashboardViewModel**: Manages main dashboard state and tagged folders
- **OrganizerViewModel**: Handles photo organization logic
- **TrashViewModel**: Manages trash operations and file recovery
- **SharedViewModel**: Handles shared state between screens

### Services
- **ScreenshotMonitorService**: Background service for detecting new screenshots

### UI Screens
- **Dashboard**: Main screen showing tagged folders and monitoring controls
- **Organizer**: Photo organization interface
- **Trash**: Trash management with restore/delete options
- **Tagged Folder**: View and manage photos in specific folders

## Usage

### Organizing Photos
1. Tap the "Organize" button on the dashboard
2. Select a folder containing photos
3. Use the organizer to categorize and move photos
4. Save your organization session

### Managing Trash
1. Navigate to the trash section via the delete icon
2. View all trashed photos in a grid layout
3. Restore individual photos or delete permanently
4. Use "Delete All" for bulk permanent deletion

### Screenshot Monitoring
1. Toggle the screenshot monitoring switch on the dashboard
2. The app will automatically detect new screenshots
3. Screenshots can be automatically processed based on your settings

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Development Guidelines

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Maintain consistent indentation

### Architecture Guidelines
- Follow MVVM pattern
- Keep ViewModels free of Android dependencies
- Use Repository pattern for data access
- Implement proper error handling

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

If you encounter any issues or have questions:
- Create an issue on GitHub
- Check existing issues for solutions
- Review the documentation

### Version History
- **v1.0.0** - Initial release with core functionality
  - Basic photo organization
  - Trash management
  - Screenshot monitoring
  - Material 3 UI

## Acknowledgments

- Google for Material Design and Jetpack Compose
- Android development community for best practices
- Contributors and testers who helped improve the app

---

**Robin** - Organize your photos with intelligence 📸✨