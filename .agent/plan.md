# Project Plan

Refactor the existing PhotoEAISTO app to match the new requirements: Dashboard with Google Drive (account selection), Manual Coordinate toggle/dialog, and app Gallery. Implement real Google Drive upload, Photo Details screen, and specific photo/folder naming formats. Use XML exclusively. Use Material Design 3.

## Project Brief

# PhotoEAISTO - Project Brief

PhotoEAISTO is a professional Android utility tailored for vehicle technical inspection experts. It streamlines the process of capturing, verifying, and uploading mandatory inspection photos to ensure full compliance with regulatory standards.

### Features
*   **Startup Compliance Check**: Immediate request and verification of Camera, Location, and Storage permissions on app launch.
*   **Intelligent Dashboard (XML)**: 
    *   **Google Drive Integration**: Selection and display of the active Google account.
    *   **Coordinate Toggle**: Switch between real-time GPS and manual coordinate entry (with validation).
    *   **Local Inspection Gallery**: Quick access to app-generated photos.
*   **Automated Photo Processor**: 720p-1080p capture, 300KB-700KB file size enforcement, EXIF metadata injection.
*   **Naming Convention**: Filename: `DD-MM-YYYY_HH-mm-ss.jpg`. Google Drive Folder: `DD.MM.YYYY`.
*   **Photo Details Screen**: Shows photo, size, resolution, date/time, and coordinates with an 'Upload' button.

### High-Level Technical Stack
*   **Language**: Kotlin
*   **UI Framework**: Android View System (XML) with Material Design 3.
*   **Architecture**: MVVM with View Binding.
*   **Navigation**: Jetpack Navigation Component (XML).
*   **Cloud**: Google Sign-In and Google Drive API.

## Implementation Steps
**Total Duration:** 30m 34s

### Task_1_Infrastructure: Configure ViewBinding, Navigation Component, and M3 Theme. Implement permission handling for Camera and Location.
- **Status:** COMPLETED
- **Updates:** Enabled ViewBinding, added Navigation Component and Material 3 dependencies. Created navigation graph and main fragments (Camera, Session, Settings). Implemented permission handling for Camera and Location. Applied Material 3 theme and enabled Edge-to-Edge. Created adaptive icon. Removed Compose dependencies.
- **Acceptance Criteria:**
  - ViewBinding is enabled
  - Navigation graph is set up with placeholders
  - Permissions are requested and handled
  - Material 3 theme is applied

### Task_2_RegulatedCamera: Implement CameraX interface with XML-based guides and resolution enforcement (720p/1080p).
- **Status:** COMPLETED
- **Updates:** Fixed camera binding crash by using FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER. Improved session creation flow with event-driven navigation (SharedFlow) to ensure session ID availability. Cleaned up dialog XML (hint vs text). Optimized camera re-binding for resolution toggling.
- **Acceptance Criteria:**
  - Camera preview is functional
  - On-screen guides are visible
  - Captured images meet resolution requirements
  - UI follows M3 guidelines in XML
- **Duration:** 56s

### Task_3_MetadataAndProcessing: Integrate GPS coordinate retrieval and implement image processing logic for EXIF injection and target file size (300KB-700KB).
- **Status:** COMPLETED
- **Updates:** Integrated FusedLocationProviderClient for high-precision GPS. Implemented coordinate stabilization by selecting the most accurate recent location. Created an iterative JPEG compression logic to target 300KB-700KB file size. Integrated ExifInterface for embedding GPS and timestamp metadata into captured images. All processing is done asynchronously using Coroutines.
- **Acceptance Criteria:**
  - GPS coordinates are embedded in EXIF
  - Images are compressed to 300KB-700KB range
  - Coordinate stabilization logic is implemented
- **Duration:** 1m 39s

### Task_4_SessionAndSync: Build the inspection management UI (License Plate/VIN entry, Session list) and implement Cloud Sync stub/logic.
- **Status:** COMPLETED
- **Updates:** Fixed session creation flow and navigation issues reported by critic. Navigation now occurs after database confirmation.
- **Acceptance Criteria:**
  - Sessions can be created with VIN/Plate
  - Session list displays captured photos
  - Basic Cloud Sync integration via OkHttp
- **Duration:** 1m 47s

### Task_5_Final_Polish_Verify: Final UI/UX polish, Adaptive Icon creation, and comprehensive verification of application stability.
- **Status:** COMPLETED
- **Updates:** Verified build stability and fixes. Camera fallback strategy implemented. Session navigation is now reliable. All regulatory requirements (GPS, compression, resolution) are met. UI is M3 compliant with Edge-to-Edge. Adaptive icon created.
- **Acceptance Criteria:**
  - Adaptive app icon is present
  - Full Edge-to-Edge display implemented
  - App builds and runs without crashes
  - All functional requirements from brief met
- **Duration:** 9m 52s

### Task_6_RefactorDashboardAndGallery: Refactor Dashboard to include Google account selection, coordinate toggle with manual entry dialog, and implement local Gallery with Photo Details screen.
- **Status:** COMPLETED
- **Updates:** Refactored Dashboard with Material 3 cards for Google Drive and Coordinate toggle. Implemented Google Sign-In and permission handling. Added a Manual Coordinate dialog with Regex validation. Created a local Gallery using RecyclerView/Coil and a Photo Details screen that extracts metadata from EXIF.
- **Acceptance Criteria:**
  - Dashboard displays selected Google account and coordinate toggle
  - Manual coordinate entry dialog validates input
  - Gallery displays app-generated photos
  - Photo Details screen shows image and metadata (resolution, size, GPS)
- **Duration:** 4m 48s

### Task_7_DriveSyncAndNaming: Implement real Google Drive upload with specific naming formats and perform final app-wide verification.
- **Status:** COMPLETED
- **Updates:** Google Drive API integrated for real uploads. Folder naming follows 'DD.MM.YYYY' and file follows 'DD-MM-YYYY_HH-mm-ss.jpg'. Verified capture, compression, and EXIF logic. Return to dashboard implemented. Build successful.
- **Acceptance Criteria:**
  - Google Drive API integrated for real uploads
  - Folder naming follows 'DD.MM.YYYY' and file follows 'DD-MM-YYYY_HH-mm-ss.jpg'
  - Build pass and app does not crash
  - Run and Verify: critic_agent verifies stability and alignment with requirements
- **Duration:** 11m 32s

