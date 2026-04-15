================================================================================
  Smart Geo-Tagged Landmarks — README
  CSE 489: Mobile Application Development Lab Exam
================================================================================

1. PROJECT OVERVIEW
-------------------
Smart Geo-Tagged Landmarks is an Android application that lets users discover,
visit, add, and manage geo-tagged landmarks on an interactive map. It integrates
with a faculty-provided REST API, stores data locally for offline use, and
syncs pending actions when connectivity is restored.

2. FEATURES IMPLEMENTED
------------------------
[✓] Landmark Display  — Fetch and show landmarks with title, image, score
[✓] Map View          — Google Maps with color-coded markers (red/orange/green)
[✓] Visit Feature     — GPS location capture + visit API call + distance display
[✓] Landmarks List    — RecyclerView with sort (asc/desc) and filter by min score
[✓] Activity Screen   — Local Room DB visit history with name, time, distance
[✓] Add Landmark      — Camera/gallery image picker + GPS auto-fill + multipart upload
[✓] Soft Delete       — Deleted landmarks hidden from list; no crash on data change
[✓] Offline Support   — Room DB cache, queued offline visits, WorkManager auto-sync
[✓] Error Handling    — Snackbar/Toast for success, AlertDialog for errors

3. API USAGE
------------
Base URL : https://labs.anontech.info/cse489/exm3/api.php
API Key  : 24341233

Endpoints:
  GET  ?action=get_landmarks&key=24341233
       Returns list of landmarks (id, title, lat, lon, image, score,
       visit_count, avg_distance)

  POST ?action=visit_landmark&key=24341233
       Body (JSON): { "landmark_id": Int, "user_lat": Double, "user_lon": Double }
       Returns: distance, score, status

  POST ?action=create_landmark&key=24341233
       Body (multipart/form-data): title, lat, lon, image (FILE)
       ⚠️  MUST use form-data — raw JSON will fail for file uploads

  POST ?action=delete_landmark&key=24341233
       Body (form-data): id

  POST ?action=restore_landmark&key=24341233
       Body (form-data): id

4. OFFLINE STRATEGY
--------------------
• On successful API fetch, all landmarks are persisted to Room DB (landmarks table).
• When offline, the app reads from Room DB and displays cached data.
• Visit requests made offline are stored in the pending_visits table.
• WorkManager schedules a periodic sync worker (every 15 minutes) that:
    - Checks for internet connectivity before running
    - Replays all pending visit requests to the API
    - Removes each pending record upon successful sync
• Visit history is always stored locally in visit_history table regardless of
  connectivity, with a "synced" flag indicating sync status.

5. ARCHITECTURE USED
---------------------
Pattern   : MVVM (Model-View-ViewModel) + Repository
API Layer : Retrofit 2 + OkHttp + Gson converter
Local DB  : Room (3 tables: landmarks, visit_history, pending_visits)
Async     : Kotlin Coroutines + ViewModelScope
Background: WorkManager (CoroutineWorker)
Images    : Glide
Maps      : Google Maps Android SDK
Location  : FusedLocationProviderClient (Google Play Services)
UI        : Material3, BottomNavigationView, RecyclerView, ViewBinding

Project Structure:
  app/src/main/java/com/smartlandmarks/
  ├── data/
  │   ├── api/          (LandmarkApiService, RetrofitClient)
  │   ├── model/        (Landmark, VisitRequest, VisitResponse, GenericResponse)
  │   ├── local/        (AppDatabase, LandmarkEntity, VisitHistoryEntity,
  │   │                  PendingVisitEntity, DAOs)
  │   └── repository/   (LandmarkRepository, Result sealed class)
  ├── ui/
  │   ├── map/          (MapFragment, LandmarkDetailDialog)
  │   ├── landmarks/    (LandmarksFragment, LandmarkAdapter)
  │   ├── activity/     (ActivityFragment, VisitHistoryAdapter)
  │   └── add/          (AddLandmarkFragment)
  ├── viewmodel/        (LandmarkViewModel)
  ├── worker/           (SyncWorker)
  ├── utils/            (Constants, NetworkUtils)
  └── SmartLandmarksApp.kt

6. CHALLENGES FACED
--------------------
• Multipart file upload: The API requires form-data (not raw JSON) for image
  upload. Using raw JSON causes $_FILES to be empty on the server. This was
  handled by using Retrofit's @Multipart + MultipartBody.Part correctly.

• Offline queueing: Ensuring visit requests made offline are reliably replayed
  required a combination of Room (for persistence across restarts) and
  WorkManager (for constraint-based execution when network returns).

• Dynamic API responses: The API response structure may vary (landmarks key vs
  data key). The LandmarksResponse model handles both cases with a getLandmarkList()
  helper method and also filters out soft-deleted entries (deleted != 0).

• Marker colors on map: Score thresholds (0-30 red, 30-70 orange, 70+ green)
  map directly to Google Maps BitmapDescriptorFactory hue values.

• GPS location for visits: FusedLocationProviderClient.lastLocation can return
  null if no recent fix exists. Handled with a clear user message to try again.

================================================================================
