package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MonthlyStatement
import com.example.model.Payment
import com.example.model.Student
import com.example.model.StudentBalanceInfo
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningContainer
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.TealPrimary
import com.example.utils.DateUtils
import com.example.utils.LedgerEngine
import java.util.Calendar
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerDialog(
    initialDateIso: String,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialCal = remember(initialDateIso) { DateUtils.parseIsoDate(initialDateIso) }
    val initialUtcMillis = remember(initialDateIso) {
        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.set(initialCal.get(Calendar.YEAR), initialCal.get(Calendar.MONTH), initialCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                        cal.timeInMillis = millis
                        val selectedIso = String.format(
                            "%04d-%02d-%02d",
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH) + 1,
                            cal.get(Calendar.DAY_OF_MONTH)
                        )
                        onDateSelected(selectedIso)
                    }
                    onDismiss()
                }
            ) {
                Text("Select", fontWeight = FontWeight.Bold, color = TealPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
fun DatePickerField(
    label: String,
    dateIso: String,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "date_picker_field"
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = "${DateUtils.formatDisplayDate(dateIso)} ($dateIso)",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                IconButton(onClick = { showDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Select Date",
                        tint = TealPrimary
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
        // Clickable transparent overlay to trigger dialog on tapping any part of the field
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showDialog = true }
        )
    }

    if (showDialog) {
        AppDatePickerDialog(
            initialDateIso = dateIso,
            onDateSelected = onDateSelected,
            onDismiss = { showDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickLogBottomSheet(
    students: List<Student>,
    initialStudentId: String? = null,
    currencySymbol: String = "৳",
    onDismiss: () -> Unit,
    onSaveLog: (studentId: String, date: String, durationMin: Int?, rate: Long, note: String?, status: String) -> Unit,
    onCheckAlreadyLoggedToday: (studentId: String, date: String) -> Boolean
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedStudentId by remember {
        mutableStateOf(initialStudentId ?: students.firstOrNull()?.id ?: "")
    }
    var dateStr by remember { mutableStateOf(DateUtils.todayIso()) }
    var note by remember { mutableStateOf("") }

    val currentStudent = remember(selectedStudentId, students) {
        students.find { it.id == selectedStudentId }
    }

    var rateStr by remember(selectedStudentId) {
        val s = students.find { it.id == selectedStudentId }
        val r = s?.ratePerClass ?: 0L
        mutableStateOf(r.toString())
    }

    val alreadyLogged = remember(selectedStudentId, dateStr) {
        if (selectedStudentId.isNotEmpty()) {
            onCheckAlreadyLoggedToday(selectedStudentId, dateStr)
        } else false
    }

    var confirmedDoubleLog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Log Class",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Student selection chips (recent students)
            Text("Select Student", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                students.filter { it.status == "active" }.forEach { s ->
                    FilterChip(
                        selected = s.id == selectedStudentId,
                        onClick = {
                            selectedStudentId = s.id
                            confirmedDoubleLog = false
                            rateStr = s.ratePerClass.toString()
                        },
                        label = { Text(s.name) },
                        modifier = Modifier.testTag("log_student_chip_${s.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Warning for accidental double log on same date
            if (alreadyLogged && !confirmedDoubleLog) {
                Surface(
                    color = AmberWarningContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AmberWarning)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Already logged for this date",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "A class was already logged today for this student. Add another?",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { confirmedDoubleLog = true },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Yes, Add")
                        }
                    }
                }
            }

            // Date Picker Field
            DatePickerField(
                label = "Class Date",
                dateIso = dateStr,
                onDateSelected = { selectedDate ->
                    dateStr = selectedDate
                    confirmedDoubleLog = false
                },
                testTag = "log_date_input"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Class Fee (Rate) - Duration removed per request
            OutlinedTextField(
                value = rateStr,
                onValueChange = { rateStr = it.filter { c -> c.isDigit() } },
                label = { Text("Class Fee ($currencySymbol)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("log_rate_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Note (optional)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note / Topic (optional)") },
                modifier = Modifier.fillMaxWidth().testTag("log_note_input")
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val rate = rateStr.toLongOrNull() ?: 0L
                    if (selectedStudentId.isNotEmpty()) {
                        onSaveLog(
                            selectedStudentId,
                            dateStr,
                            null, // duration removed
                            rate,
                            note.ifEmpty { null },
                            "held" // default to held
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_class_log_button"),
                enabled = selectedStudentId.isNotEmpty() && (!alreadyLogged || confirmedDoubleLog),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Class Log", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddPaymentBottomSheet(
    students: List<Student>,
    studentBalances: Map<String, StudentBalanceInfo>,
    initialStudentId: String? = null,
    initialMethod: String = "cash",
    currencySymbol: String = "৳",
    onDismiss: () -> Unit,
    onSavePayment: (Payment) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedStudentId by remember {
        mutableStateOf(initialStudentId ?: students.firstOrNull()?.id ?: "")
    }
    var amountStr by remember { mutableStateOf("") }
    var dateStr by remember { mutableStateOf(DateUtils.todayIso()) }
    var selectedMethod by remember { mutableStateOf(initialMethod) }
    var note by remember { mutableStateOf("") }

    val currentBalance = studentBalances[selectedStudentId]?.balance ?: 0L

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Record Payment",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Student selection
            Text("Select Student", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                students.forEach { s ->
                    FilterChip(
                        selected = s.id == selectedStudentId,
                        onClick = { selectedStudentId = s.id },
                        label = { Text(s.name) },
                        modifier = Modifier.testTag("payment_student_${s.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick shortcut: Fill full balance if student has dues
            if (currentBalance > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .clickable { amountStr = currentBalance.toString() }
                        .padding(bottom = 10.dp)
                ) {
                    Text(
                        text = "Fill full balance: $currencySymbol$currentBalance",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            // Amount input
            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it.filter { char -> char.isDigit() } },
                label = { Text("Amount ($currencySymbol) *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("payment_amount_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Date Picker Field
            DatePickerField(
                label = "Payment Date",
                dateIso = dateStr,
                onDateSelected = { selectedDate ->
                    dateStr = selectedDate
                },
                testTag = "payment_date_input"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Method chips
            Text("Payment Method", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(6.dp))
            val methods = listOf("cash", "bKash", "Nagad", "bank", "other")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                methods.forEach { m ->
                    FilterChip(
                        selected = selectedMethod == m,
                        onClick = { selectedMethod = m },
                        label = { Text(m.replaceFirstChar { it.uppercase() }) },
                        modifier = Modifier.testTag("payment_method_$m")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Note (optional)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth().testTag("payment_note_input")
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val amt = amountStr.toLongOrNull() ?: 0L
                    if (amt > 0 && selectedStudentId.isNotEmpty()) {
                        val payment = Payment(
                            id = "",
                            studentId = selectedStudentId,
                            amount = amt,
                            date = dateStr,
                            method = selectedMethod,
                            note = note.ifEmpty { null },
                            createdAt = DateUtils.todayIso(),
                            updatedAt = DateUtils.todayIso()
                        )
                        onSavePayment(payment)
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_payment_confirm_button"),
                enabled = (amountStr.toLongOrNull() ?: 0L) > 0 && selectedStudentId.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Payment", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditStudentBottomSheet(
    initialStudent: Student? = null,
    existingStudents: List<Student> = emptyList(),
    currencySymbol: String = "৳",
    onDismiss: () -> Unit,
    onSaveStudent: (Student) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initialStudent?.name ?: "") }
    var phone by remember { mutableStateOf(initialStudent?.phone ?: "") }
    var guardianName by remember { mutableStateOf(initialStudent?.guardianName ?: "") }
    var subject by remember { mutableStateOf(initialStudent?.subject ?: "") }
    var grade by remember { mutableStateOf(initialStudent?.grade ?: "") }
    var notes by remember { mutableStateOf(initialStudent?.notes ?: "") }
    var rateStr by remember { mutableStateOf(initialStudent?.ratePerClass?.toString() ?: "1000") }
    var status by remember { mutableStateOf(initialStudent?.status ?: "active") }

    // Duplicate phone check
    val isDuplicatePhone = remember(phone, initialStudent, existingStudents) {
        val cleanPhone = LedgerEngine.normalizePhone(phone)
        if (cleanPhone.length >= 7) {
            existingStudents.any { s ->
                s.id != initialStudent?.id && LedgerEngine.normalizePhone(s.phone) == cleanPhone
            }
        } else false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = if (initialStudent != null) "Edit Student" else "Add Student",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Student Name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("student_name_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number (e.g. 017...) *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("student_phone_input")
            )

            // Duplicate phone warning
            if (isDuplicatePhone) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⚠️ Warning: A student with this phone number already exists.",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberWarning,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Subject (e.g. Higher Math, English)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("student_subject_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = grade,
                    onValueChange = { grade = it },
                    label = { Text("Grade / Class") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = guardianName,
                    onValueChange = { guardianName = it },
                    label = { Text("Guardian Name") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Rate per class
            OutlinedTextField(
                value = rateStr,
                onValueChange = { rateStr = it.filter { char -> char.isDigit() } },
                label = { Text("Rate per Class ($currencySymbol)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("student_rate_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Status: Active vs Archived
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = status == "active",
                    onClick = { status = "active" },
                    label = { Text("Active") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = status == "archived",
                    onClick = { status = "archived" },
                    label = { Text("Archived") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        val rate = rateStr.toLongOrNull() ?: 0L
                        val student = Student(
                            id = initialStudent?.id ?: "",
                            name = name.trim(),
                            guardianName = guardianName.ifEmpty { null },
                            phone = phone.trim(),
                            subject = subject.trim(),
                            grade = grade.ifEmpty { null },
                            notes = notes.ifEmpty { null },
                            ratePerClass = rate,
                            billByDuration = false,
                            status = status,
                            createdAt = initialStudent?.createdAt ?: DateUtils.todayIso(),
                            updatedAt = DateUtils.todayIso()
                        )
                        onSaveStudent(student)
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_student_confirm_button"),
                enabled = name.isNotBlank() && phone.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Student", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindBottomSheet(
    student: Student,
    dueAmount: Long,
    template: String,
    currencySymbol: String,
    heldClassCount: Int = 0,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val currentMonth = DateUtils.formatMonthName(DateUtils.currentMonthKey())
    var messageText by remember {
        mutableStateOf(
            LedgerEngine.formatReminder(
                template = template,
                studentName = student.name,
                amount = dueAmount,
                currencySymbol = currencySymbol,
                monthName = currentMonth,
                classCount = heldClassCount
            )
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Send Fee Reminder",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "To: ${student.name} • ${student.phone}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                label = { Text("Reminder Message") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // WhatsApp & SMS deep links as per Algorithm F
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val url = LedgerEngine.getWhatsAppUrl(student.phone, messageText)
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("remind_whatsapp_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Message, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("WhatsApp", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        val uriStr = LedgerEngine.getSmsUri(student.phone, messageText)
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(uriStr))
                        context.startActivity(intent)
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("remind_sms_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Send SMS", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyStatementBottomSheet(
    statement: MonthlyStatement,
    currencySymbol: String = "৳",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val statementText = remember(statement, currencySymbol) {
        LedgerEngine.formatStatementText(statement, currencySymbol)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Monthly Statement",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${statement.student.name} • ${DateUtils.formatMonthName(statement.monthKey)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Card preview of the statement
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Previous Balance Carried Forward", style = MaterialTheme.typography.bodySmall)
                        Text(
                            LedgerEngine.formatMoney(statement.previousBalance, currencySymbol),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Classes Subtotal (${statement.heldLogs.size} classes)", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "+${LedgerEngine.formatMoney(statement.monthSubtotal, currencySymbol)}",
                            fontWeight = FontWeight.Bold,
                            color = TealPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Payments Received (${statement.monthPayments.size})", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "-${LedgerEngine.formatMoney(statement.monthPaymentsTotal, currencySymbol)}",
                            fontWeight = FontWeight.Bold,
                            color = EmeraldSuccess
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Closing Balance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = LedgerEngine.formatMoney(statement.closingBalance, currencySymbol),
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge,
                            color = if (statement.closingBalance > 0) RoseDanger else EmeraldSuccess
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Share buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val url = LedgerEngine.getWhatsAppUrl(statement.student.phone, statementText)
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("WhatsApp")
                }

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Tuition Statement", statementText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Statement copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
