package com.campusflow.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.campusflow.app.ui.components.EmptyState
import com.campusflow.app.ui.components.PageHeading
import com.campusflow.app.ui.theme.CampusTheme
import com.campusflow.app.data.*
import com.campusflow.app.ui.schedule.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import com.campusflow.app.ui.home.DashboardScreen
import com.campusflow.app.ui.study.StudyScreen
import com.campusflow.app.ui.workout.WorkoutScreen
import com.campusflow.app.ui.settings.NotificationControls
import com.campusflow.app.ui.settings.SettingsScreen
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campusflow.app.ocr.ImportViewModel
import com.campusflow.app.ui.importing.ImportScreen
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.core.app.NotificationManagerCompat
import com.campusflow.app.ui.academic.AcademicScreen

private data class Destination(val route: String, val label: String, val icon: ImageVector)
private val destinations = listOf(
    Destination("home", "Inicio", Icons.Outlined.Home),
    Destination("schedule", "Horario", Icons.Outlined.CalendarMonth),
    Destination("study", "Estudio", Icons.AutoMirrored.Outlined.MenuBook),
    Destination("workout", "Entreno", Icons.Outlined.FitnessCenter),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusRoot(vm: AppViewModel) {
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val activities by vm.activities.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val evaluations by vm.evaluations.collectAsStateWithLifecycle()
    val routines by vm.routines.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val saveError by vm.saveError.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    var addMenu by remember { mutableStateOf(false) }
    var editor by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(editor) { if (editor) vm.saveError.value = null }
    var initial by rememberSaveable { mutableStateOf<Schedule?>(null) }
    var editOccurrenceId by rememberSaveable { mutableStateOf<Long?>(null) }
    var newCategory by rememberSaveable { mutableStateOf(Categories.PERSONAL) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var stopId by remember { mutableStateOf<Long?>(null) }
    var moveId by rememberSaveable { mutableStateOf<Long?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var remindersReady by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        remindersReady = NotificationManagerCompat.from(vm.container).areNotificationsEnabled() && vm.container.reminders.exactAllowed()
        vm.container.refreshReminders()
    }
    LaunchedEffect(vm) { vm.notificationTarget.collect { if (it != null) { detailId = it; vm.notificationTarget.value = null } } }
    LaunchedEffect(vm) {
        vm.feedback.collect { feedback ->
            scope.launch {
                val result = snackbar.showSnackbar(feedback.message, if (feedback.undo != null) "DESHACER" else null, withDismissAction = true)
                if (result == SnackbarResult.ActionPerformed) vm.perform { feedback.undo?.invoke() }
            }
        }
    }
    CampusTheme(preferences) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                if (route != "settings" && route != "import" && !route.startsWith("academics/")) FloatingActionButton(onClick = { addMenu = true }, containerColor = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Outlined.Add, "Agregar") }
            },
            topBar = {
                TopAppBar(title = { Text("Kairos", style = MaterialTheme.typography.titleLarge) }, actions = {
                    IconButton(onClick = { nav.navigate("settings") { launchSingleTop = true } }) { Icon(Icons.Outlined.Settings, "Configuración") }
                })
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    destinations.forEach { destination ->
                        NavigationBarItem(selected = route == destination.route || (destination.route == "study" && route.startsWith("academics/")), onClick = {
                            nav.navigate(destination.route) {
                                popUpTo(nav.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                // Inicio must return to its overview, not restore an
                                // evaluation opened from that same tab.
                                restoreState = destination.route != "home"
                            }
                        }, icon = { Icon(destination.icon, destination.label) }, label = { Text(destination.label) })
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            NavHost(navController = nav, startDestination = "home", modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(), enterTransition = { fadeIn(tween(200)) }, exitTransition = { fadeOut(tween(120)) }) {
                destinations.forEach { destination ->
                    composable(destination.route) {
                        if (destination.route == "schedule") ScheduleScreen(activities, { detailId = it.occurrence.id }, vm::complete) { from, to -> vm.perform { vm.repository.generateRange(from, to) } }
                        else if (destination.route == "home") DashboardScreen(activities, now, remindersReady && preferences.notifications, { detailId = it.occurrence.id }, vm::complete, { addMenu = true }, { nav.navigate("settings") }, evaluations, { nav.navigate("academics/${it.evaluation.subjectId}/${it.evaluation.id}") })
                        else if (destination.route == "study") StudyScreen(vm, activities, subjects, { detailId = it.occurrence.id }, { subjectId, minutes ->
                            initial = Schedule(title = "", category = Categories.STUDY, startDay = java.time.LocalDate.now().toEpochDay(), startMinute = 16 * 60, durationMinutes = minutes, subjectId = subjectId, reminderMinutes = preferences.reminderMinutes)
                            editOccurrenceId = null; newCategory = Categories.STUDY; editor = true
                        }, { nav.navigate("academics/${it.id}/0") })
                        else if (destination.route == "workout") WorkoutScreen(vm, activities, routines, { detailId = it.occurrence.id }) { routine ->
                            val today = java.time.LocalDate.now()
                            editOccurrenceId = null
                            initial = Schedule(title = routine.name, category = Categories.WORKOUT, startDay = today.toEpochDay(), startMinute = 18 * 60, durationMinutes = routine.minutes, repeat = "WEEKLY", weekdays = today.dayOfWeek.value.toString(), routineId = routine.id, reminderMinutes = preferences.reminderMinutes)
                            editor = true
                        }
                        else Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            PageHeading(destination.label, "Tu día, con intención.")
                            EmptyState("Un espacio para organizarte", "Tus actividades aparecerán aquí.", destination.icon)
                        }
                    }
                }
                composable("academics/{subjectId}/{evaluationId}") { academicEntry ->
                    AcademicScreen(vm, academicEntry.arguments?.getString("subjectId")?.toLongOrNull() ?: 0, academicEntry.arguments?.getString("evaluationId")?.toLongOrNull() ?: 0) { nav.popBackStack() }
                }
                composable("settings") {
                    SettingsScreen(vm, preferences) { nav.popBackStack() }
                }
                composable("import") {
                    ImportScreen(viewModel<ImportViewModel>(), { nav.popBackStack() }, {
                        nav.popBackStack()
                        vm.perform { vm.message("Horario guardado. Puedes editarlo desde Horario.") }
                    })
                }
            }
            }
        }
        if (addMenu) ModalBottomSheet(onDismissRequest = { addMenu = false }) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Haz espacio para tus planes", style = MaterialTheme.typography.titleLarge)
                listOf("Agregar actividad" to Categories.PERSONAL, "Agregar sesión de estudio" to Categories.STUDY, "Agregar entrenamiento" to Categories.WORKOUT).forEach { (label, kind) ->
                    TextButton(onClick = { initial = null; editOccurrenceId = null; newCategory = kind; editor = true; addMenu = false }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
                TextButton(onClick = { addMenu = false; nav.navigate("import") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text("Importar horario desde imagen") }
                Spacer(Modifier.height(20.dp))
            }
        }
        if (editor) ActivityEditor(initial, newCategory, preferences, categories, subjects, routines, saving, editOccurrenceId != null, saveError, { editor = false }) { value -> vm.saveSchedule(value, editOccurrenceId) { editor = false } }
        activities.find { it.occurrence.id == detailId }?.let { item ->
            val history by remember(item.occurrence.id) { vm.repository.dao.history(item.occurrence.id) }.collectAsStateWithLifecycle(emptyList())
            ActivityDetails(item, history, { detailId = null }, {
                editOccurrenceId = item.occurrence.id
                initial = item.schedule.copy(id = 0, startDay = item.occurrence.day, startMinute = item.occurrence.minute, repeat = "ONCE", endDay = null)
                editor = true; detailId = null
            }, { editOccurrenceId = null; initial = item.schedule; editor = true; detailId = null }, { vm.delete(item); detailId = null }, { vm.complete(item); detailId = null }, { stopId = item.schedule.id; detailId = null }, { moveId = item.occurrence.id; detailId = null })
        }
        activities.find { it.occurrence.id == moveId }?.let { item ->
            RescheduleDialog(item, { moveId = null }) { day, minute ->
                vm.perform {
                    val receipt = vm.repository.move(item.occurrence.id, day, minute)
                    moveId = null
                    val label = java.time.LocalDate.ofEpochDay(day).dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, com.campusflow.app.domain.Spanish)
                    vm.message("Movido al $label") { vm.repository.undoMove(receipt) }
                }
            }
        }
        stopId?.let { id -> AlertDialog(onDismissRequest = { stopId = null }, title = { Text("¿Detener la serie?") }, text = { Text("Se cancelarán las próximas actividades pendientes de esta serie. Las completadas se conservan.") }, confirmButton = {
            TextButton(onClick = { vm.perform { vm.repository.stopSeries(id); vm.message("Repetición detenida") }; stopId = null }) { Text("Detener") }
        }, dismissButton = { TextButton(onClick = { stopId = null }) { Text("Cancelar") } }) }
    }
}
