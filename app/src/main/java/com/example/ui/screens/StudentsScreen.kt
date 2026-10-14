package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ClassLog
import com.example.model.Payment
import com.example.model.Student
import com.example.ui.components.AddEditStudentBottomSheet
import com.example.ui.components.BalanceChip
import com.example.ui.components.MonthlyStatementBottomSheet
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.TealPrimary
import com.example.utils.DateUtils
import com.example.utils.LedgerEngine
import com.example.viewmodel.TuitionLedgerViewModel
import java.util.Calendar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentsScreen(
    viewModel: TuitionLedgerViewModel,
    onRecordPayment: (studentId: String) -> Unit,
    onLogClassForStudent: (studentId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val students by viewModel.students.collectAsState()
    val balances by viewModel.studentBalances.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val classLogs by viewModel.classLogs.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Active") } // "Active" | "Archived" | "Has dues" | "All"
    var selectedStudentId by remember { mutableStateOf<String?>(null) }
    var studentToEdit by remember { mutableStateOf<Student?>(null) }

    // Detail view if a student is clicked
    if (selectedStudentId != null) {
        val student = students.find { it.id == selectedStudentId }
        if (student != null) {
            StudentDetailScreen(
                student = student,
                viewModel = viewModel,
                onBack = { selectedStudentId = null },
                onEdit = { studentToEdit = student },
                onRecordPayment = { onRecordPayment(student.id) },
                onLogClass = { onLogClassForStudent(student.id) }
            )
            return
        }
    }

    val filteredStudents = remember(students, searchQuery, selectedFilter, balances) {
        students.filter { s ->
            val matchesQuery = s.name.contains(searchQuery, ignoreCase = true) ||
                    s.subject.contains(searchQuery, ignoreCase = true) ||
                    s.phone.contains(searchQuery)
            val balance = balances[s.id]?.balance ?: 0L

            val matchesFilter = when (selectedFilter) {
                "Active" -> s.status == "active"
                "Archived" -> s.status == "archived"
                "Has dues" -> balance > 0L
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Students (${students.count { it.status == "active" }} active)",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by name, subject, phone...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("student_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter chips: Active / Archived / Has dues / All
        val filters = listOf("Active", "Archived", "Has dues", "All")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            filters.forEach { f ->
                FilterChip(
                    selected = selectedFilter == f,
                    onClick = { selectedFilter = f },
                    label = { Text(f) },
                    modifier = Modifier.testTag("filter_$f")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredStudents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (students.isEmpty()) "No students added yet. Tap + to add your first student!" else "No matching students found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredStudents) { student ->
                    val balance = balances[student.id]?.balance ?: 0L
                    val today = DateUtils.todayIso()
                    val hasLoggedToday = classLogs.any { it.studentId == student.id && it.date == today }

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedStudentId = student.id }
                            .testTag("student_card_${student.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = student.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (student.status == "archived") {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "Archived",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (student.subject.isNotBlank())
                                        "${student.subject} ${if (student.grade != null) "• " + student.grade else ""}"
                                    else
                                        student.phone,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${LedgerEngine.formatMoney(student.ratePerClass, settings.currencySymbol)}/class",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TealPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                BalanceChip(balance = balance, currencySymbol = settings.currencySymbol)
                                Spacer(modifier = Modifier.height(6.dp))
                                // Quick "Log class" or "Log again" button
                                OutlinedButton(
                                    onClick = { onLogClassForStudent(student.id) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("quick_log_btn_${student.id}")
                                ) {
                                    Text(
                                        text = if (hasLoggedToday) "Log again" else "Log class",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    studentToEdit?.let { student ->
        AddEditStudentBottomSheet(
            initialStudent = student,
            existingStudents = students,
            currencySymbol = settings.currencySymbol,
            onDismiss = { studentToEdit = null },
            onSaveStudent = { updated ->
                viewModel.saveStudent(updated)
                studentToEdit = null
            }
        )
    }
}

@Composable
fun StudentDetailScreen(
    student: Student,
    viewModel: TuitionLedgerViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRecordPayment: () -> Unit,
    onLogClass: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val balances by viewModel.studentBalances.collectAsState()
    val allLogs by viewModel.classLogs.collectAsState()
    val allPayments by viewModel.payments.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val balanceInfo = balances[student.id]
    val balance = balanceInfo?.balance ?: 0L

    var selectedMonthKey by remember { mutableStateOf(DateUtils.currentMonthKey()) }

    // Statement for current selected month
    val statement = remember(student, selectedMonthKey, allLogs, allPayments) {
        viewModel.getMonthlyStatement(student, selectedMonthKey)
    }

    val studentLogs = remember(allLogs, student.id) {
        allLogs.filter { it.studentId == student.id }.sortedByDescending { it.date }
    }
    val studentPayments = remember(allPayments, student.id) {
        allPayments.filter { it.studentId == student.id }.sortedByDescending { it.date }
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0=Classes, 1=Payments
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showStatementSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = student.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit student")
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete student", tint = RoseDanger)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Profile & Rate Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = student.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (student.subject.isNotBlank())
                                        "${student.subject} ${if (student.grade != null) "• " + student.grade else ""}"
                                    else "Tuition Student",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BalanceChip(balance = balance, currencySymbol = settings.currencySymbol)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Rate info
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Rate: ${LedgerEngine.formatMoney(student.ratePerClass, settings.currencySymbol)} / class",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TealPrimary
                            )
                        }

                        if (!student.guardianName.isNullOrEmpty()) {
                            Text("Guardian: ${student.guardianName}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Phone: ${student.phone}", style = MaterialTheme.typography.bodySmall)
                        if (!student.notes.isNullOrEmpty()) {
                            Text("Notes: ${student.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Contact & Archive actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Call
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${student.phone}"))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Call")
                            }

                            // Archive action
                            OutlinedButton(
                                onClick = {
                                    val nextStatus = if (student.status == "active") "archived" else "active"
                                    viewModel.saveStudent(student.copy(status = nextStatus))
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (student.status == "active") Icons.Default.Archive else Icons.Default.Unarchive,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (student.status == "active") "Archive" else "Restore")
                            }
                        }
                    }
                }
            }

            // Quick Action Buttons (Log class / Pay)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onLogClass,
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Log Class", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onRecordPayment,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Record Payment", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 2. MONTH SWITCHER & MONTHLY STATEMENT (Algorithm C)
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Month switcher header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                val cal = DateUtils.parseIsoDate("$selectedMonthKey-01")
                                cal.add(Calendar.MONTH, -1)
                                selectedMonthKey = String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
                            }) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
                            }

                            Text(
                                text = DateUtils.formatMonthName(selectedMonthKey),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(onClick = {
                                val cal = DateUtils.parseIsoDate("$selectedMonthKey-01")
                                cal.add(Calendar.MONTH, 1)
                                selectedMonthKey = String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
                            }) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Statement overview
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Previous balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(LedgerEngine.formatMoney(statement.previousBalance, settings.currencySymbol), fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Classes subtotal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("+${LedgerEngine.formatMoney(statement.monthSubtotal, settings.currencySymbol)}", fontWeight = FontWeight.Bold, color = TealPrimary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Payments", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("-${LedgerEngine.formatMoney(statement.monthPaymentsTotal, settings.currencySymbol)}", fontWeight = FontWeight.Bold, color = EmeraldSuccess)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Closing Balance:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = LedgerEngine.formatMoney(statement.closingBalance, settings.currencySymbol),
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (statement.closingBalance > 0) RoseDanger else EmeraldSuccess
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Share statement button
                        Button(
                            onClick = { showStatementSheet = true },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("share_statement_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share Statement", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. MONTH CALENDAR VIEW (with dots for days with classes)
            item {
                MonthCalendarDotsView(
                    monthKey = selectedMonthKey,
                    heldLogs = statement.heldLogs
                )
            }

            // 4. TABS: Classes / Payments
            item {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Classes (${studentLogs.size})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Payments (${studentPayments.size})") }
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> {
                    if (studentLogs.isEmpty()) {
                        item {
                            Text(
                                "No classes logged yet for this student.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        items(studentLogs) { log ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (log.status == "held") EmeraldSuccess else RoseDanger)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = DateUtils.formatDisplayDate(log.date),
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (log.durationMin != null) {
                                            Text(
                                                text = "${log.durationMin} mins • ${log.status.replaceFirstChar { it.uppercase() }}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (!log.note.isNullOrBlank()) {
                                            Text(
                                                text = log.note,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = LedgerEngine.formatMoney(log.rate, settings.currencySymbol),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (log.status == "held") TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(onClick = { viewModel.softDeleteClassLog(log.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete class", tint = RoseDanger, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (studentPayments.isEmpty()) {
                        item {
                            Text(
                                "No payments recorded yet for this student.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        items(studentPayments) { p ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "+${LedgerEngine.formatMoney(p.amount, settings.currencySymbol)}",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = EmeraldSuccess
                                        )
                                        Text(
                                            text = "${DateUtils.formatDisplayDate(p.date)} • ${p.method}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!p.note.isNullOrBlank()) {
                                            Text(
                                                text = p.note,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                    IconButton(onClick = { viewModel.softDeletePayment(p.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete payment", tint = RoseDanger, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Move Student to Trash?") },
            text = { Text("Move ${student.name} to Trash? You can restore this student anytime from Settings > Trash.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.softDeleteStudent(student.id)
                        showDeleteDialog = false
                        onBack()
                    }
                ) {
                    Text("Move to Trash", color = RoseDanger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showStatementSheet) {
        MonthlyStatementBottomSheet(
            statement = statement,
            currencySymbol = settings.currencySymbol,
            onDismiss = { showStatementSheet = false }
        )
    }
}

/**
 * Month Calendar View with dots indicating days on which classes were held.
 */
@Composable
fun MonthCalendarDotsView(
    monthKey: String,
    heldLogs: List<ClassLog>,
    modifier: Modifier = Modifier
) {
    val cal = remember(monthKey) {
        val c = DateUtils.parseIsoDate("$monthKey-01")
        c.set(Calendar.DAY_OF_MONTH, 1)
        c
    }

    val daysInMonth = remember(cal) { cal.getActualMaximum(Calendar.DAY_OF_MONTH) }
    val firstDayOfWeek = remember(cal) { cal.get(Calendar.DAY_OF_WEEK) - 1 } // 0=Sun..6=Sat

    val heldDays = remember(heldLogs) {
        heldLogs.mapNotNull { log ->
            val parts = log.date.split("-")
            if (parts.size == 3) parts[2].toIntOrNull() else null
        }.toSet()
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Class Days Calendar",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Weekday headers: S, M, T, W, T, F, S
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { w ->
                    Text(
                        text = w,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Grid of days
            val totalCells = firstDayOfWeek + daysInMonth
            val totalRows = (totalCells + 6) / 7

            for (row in 0 until totalRows) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        val dayNum = cellIndex - firstDayOfWeek + 1
                        if (dayNum in 1..daysInMonth) {
                            val hasClass = dayNum in heldDays
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(32.dp)
                            ) {
                                Text(
                                    text = "$dayNum",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (hasClass) FontWeight.Bold else FontWeight.Normal,
                                    color = if (hasClass) TealPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (hasClass) TealPrimary else Color.Transparent)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(32.dp))
                        }
                    }
                }
            }
        }
    }
}
