package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.ui.components.AddEditStudentBottomSheet
import com.example.ui.components.AddPaymentBottomSheet
import com.example.ui.components.QuickLogBottomSheet
import com.example.ui.components.SpeedDialFab
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudentsScreen
import com.example.ui.screens.TrashScreen
import com.example.ui.theme.TuitionLedgerTheme
import com.example.viewmodel.TuitionLedgerViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: TuitionLedgerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by viewModel.settings.collectAsState()

            TuitionLedgerTheme(themePreference = settings.theme) {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

enum class ScreenTab {
    HOME, STUDENTS, INSIGHTS
}

enum class SubScreen {
    NONE, SETTINGS, TRASH
}

@Composable
fun MainAppContent(viewModel: TuitionLedgerViewModel) {
    var currentTab by remember { mutableStateOf(ScreenTab.HOME) }
    var subScreen by remember { mutableStateOf(SubScreen.NONE) }

    // Bottom sheet dialog states
    var showAddStudentSheet by remember { mutableStateOf(false) }
    var showAddPaymentSheet by remember { mutableStateOf(false) }
    var addPaymentInitialStudentId by remember { mutableStateOf<String?>(null) }
    var showQuickLogSheet by remember { mutableStateOf(false) }
    var quickLogInitialStudentId by remember { mutableStateOf<String?>(null) }

    val students by viewModel.students.collectAsState()
    val balances by viewModel.studentBalances.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val snackbarMsg by viewModel.snackbarMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle snackbar messages with 5-second Undo support for deletes
    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let { msg ->
            val hasUndo = msg.contains("Trash") || msg.contains("moved to Trash") || msg.contains("deleted")
            val actionLabel = if (hasUndo) "Undo" else null
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = actionLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoLastDelete()
            }
            viewModel.dismissSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (subScreen == SubScreen.NONE) {
                NavigationBar(modifier = Modifier.testTag("bottom_nav_bar")) {
                    NavigationBarItem(
                        selected = currentTab == ScreenTab.HOME,
                        onClick = { currentTab = ScreenTab.HOME },
                        icon = { Icon(Icons.AutoMirrored.Filled.EventNote, contentDescription = "Home") },
                        label = { Text("Home") },
                        modifier = Modifier.testTag("nav_tab_home")
                    )
                    NavigationBarItem(
                        selected = currentTab == ScreenTab.STUDENTS,
                        onClick = { currentTab = ScreenTab.STUDENTS },
                        icon = { Icon(Icons.Default.People, contentDescription = "Students") },
                        label = { Text("Students") },
                        modifier = Modifier.testTag("nav_tab_students")
                    )
                    NavigationBarItem(
                        selected = currentTab == ScreenTab.INSIGHTS,
                        onClick = { currentTab = ScreenTab.INSIGHTS },
                        icon = { Icon(Icons.Default.BarChart, contentDescription = "Insights") },
                        label = { Text("Insights") },
                        modifier = Modifier.testTag("nav_tab_insights")
                    )
                }
            }
        },
        floatingActionButton = {
            if (subScreen == SubScreen.NONE) {
                SpeedDialFab(
                    onAddStudent = { showAddStudentSheet = true },
                    onAddPayment = {
                        addPaymentInitialStudentId = null
                        showAddPaymentSheet = true
                    },
                    onLogClass = {
                        quickLogInitialStudentId = students.firstOrNull { it.status == "active" }?.id
                        showQuickLogSheet = true
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (subScreen) {
                SubScreen.SETTINGS -> {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { subScreen = SubScreen.NONE },
                        onNavigateToTrash = { subScreen = SubScreen.TRASH }
                    )
                }
                SubScreen.TRASH -> {
                    TrashScreen(
                        viewModel = viewModel,
                        onBack = { subScreen = SubScreen.SETTINGS }
                    )
                }
                SubScreen.NONE -> {
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "TabContent"
                    ) { tab ->
                        when (tab) {
                            ScreenTab.HOME -> {
                                HomeScreen(
                                    viewModel = viewModel,
                                    onNavigateToSettings = { subScreen = SubScreen.SETTINGS },
                                    onRecordPaymentForStudent = { studentId ->
                                        addPaymentInitialStudentId = studentId
                                        showAddPaymentSheet = true
                                    }
                                )
                            }
                            ScreenTab.STUDENTS -> {
                                StudentsScreen(
                                    viewModel = viewModel,
                                    onRecordPayment = { studentId ->
                                        addPaymentInitialStudentId = studentId
                                        showAddPaymentSheet = true
                                    },
                                    onLogClassForStudent = { studentId ->
                                        quickLogInitialStudentId = studentId
                                        showQuickLogSheet = true
                                    }
                                )
                            }
                            ScreenTab.INSIGHTS -> {
                                InsightsScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Quick Log Class Sheet
    if (showQuickLogSheet) {
        QuickLogBottomSheet(
            students = students,
            initialStudentId = quickLogInitialStudentId,
            currencySymbol = settings.currencySymbol,
            onDismiss = { showQuickLogSheet = false },
            onSaveLog = { studentId, date, durationMin, rate, note, status ->
                viewModel.logClass(
                    studentId = studentId,
                    date = date,
                    durationMin = durationMin,
                    rateOverride = rate,
                    note = note,
                    status = status
                )
                showQuickLogSheet = false
            },
            onCheckAlreadyLoggedToday = { studentId, date ->
                viewModel.hasClassLoggedOnDate(studentId, date)
            }
        )
    }

    // Modal Add Payment Sheet
    if (showAddPaymentSheet) {
        AddPaymentBottomSheet(
            students = students,
            studentBalances = balances,
            initialStudentId = addPaymentInitialStudentId,
            initialMethod = settings.lastUsedPaymentMethod,
            currencySymbol = settings.currencySymbol,
            onDismiss = { showAddPaymentSheet = false },
            onSavePayment = { payment ->
                viewModel.savePayment(payment)
                showAddPaymentSheet = false
            }
        )
    }

    // Modal Add Student Sheet
    if (showAddStudentSheet) {
        AddEditStudentBottomSheet(
            existingStudents = students,
            currencySymbol = settings.currencySymbol,
            onDismiss = { showAddStudentSheet = false },
            onSaveStudent = { student ->
                viewModel.saveStudent(student)
                showAddStudentSheet = false
            }
        )
    }
}
