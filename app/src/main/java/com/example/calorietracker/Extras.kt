package com.example.calorietracker

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

val meals = listOf("Breakfast", "Lunch", "Dinner", "Snack")
fun defaultMeal() = when (LocalTime.now().hour) { in 4..10 -> "Breakfast"; in 11..15 -> "Lunch"; in 18..22 -> "Dinner"; else -> "Snack" }

// Approximate values - tap the pencil on My Foods to adjust to your portion.
val presets = listOf(
    Food(0, "Roti (1)", 120), Food(0, "Paratha (1)", 260), Food(0, "Naan (1)", 260), Food(0, "Rice (1 cup)", 200),
    Food(0, "Biryani (1 plate)", 500), Food(0, "Daal (1 bowl)", 180), Food(0, "Chicken curry (1 bowl)", 250),
    Food(0, "Egg, boiled (1)", 78), Food(0, "Chai with milk (1 cup)", 90), Food(0, "Samosa (1)", 260),
    Food(0, "Banana (1)", 105), Food(0, "Apple (1)", 95), Food(0, "Milk (1 glass)", 120), Food(0, "Yogurt (1 bowl)", 100)
)

@Composable
fun WaterCard(n: Int, onChange: (Int) -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("💧 Water", fontWeight = FontWeight.SemiBold); Text("$n / 8 glasses", color = Color.Gray, fontSize = 13.sp) }
            OutlinedButton(onClick = { onChange(n - 1) }) { Text("−") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onChange(n + 1) }) { Text("+") }
        }
        LinearProgressIndicator(progress = { (n / 8f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp))
    }
}

@Composable
fun GoalDialog(goal: Int, onClose: () -> Unit, onSave: (Int) -> Unit) {
    var t by remember { mutableStateOf(goal.toString()) }
    var age by remember { mutableStateOf("") }; var h by remember { mutableStateOf("") }; var w by remember { mutableStateOf("") }
    var male by remember { mutableStateOf(true) }; var act by remember { mutableStateOf(1.375) }; var aim by remember { mutableStateOf(0) }
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)
    val a = age.toIntOrNull(); val hh = h.toDoubleOrNull(); val ww = w.toDoubleOrNull()
    val suggested = if (a != null && hh != null && ww != null) ((10 * ww + 6.25 * hh - 5 * a + (if (male) 5 else -161)) * act + aim).roundToInt() else null
    AlertDialog(onDismissRequest = onClose, title = { Text("Daily calorie goal") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(t, { t = it.filter(Char::isDigit) }, label = { Text("Goal (kcal)") }, singleLine = true, keyboardOptions = num) }
                item { Text("Or calculate it for me", fontWeight = FontWeight.SemiBold) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(age, { age = it.filter(Char::isDigit) }, label = { Text("Age") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
                        OutlinedTextField(h, { h = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("cm") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
                        OutlinedTextField(w, { w = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("kg") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
                    }
                }
                item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(male, { male = true }, { Text("Male") }); FilterChip(!male, { male = false }, { Text("Female") }) } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Low" to 1.2, "Medium" to 1.375, "High" to 1.55).forEach { (n, v) -> FilterChip(act == v, { act = v }, { Text(n, fontSize = 12.sp) }) } } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Lose" to -500, "Maintain" to 0, "Gain" to 300).forEach { (n, v) -> FilterChip(aim == v, { aim = v }, { Text(n, fontSize = 12.sp) }) } } }
                if (suggested != null) item { OutlinedButton(onClick = { t = suggested.toString() }) { Text("Use $suggested kcal") } }
            }
        },
        confirmButton = { Button(onClick = { t.toIntOrNull()?.takeIf { it > 0 }?.let(onSave); onClose() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
fun HistoryScreen(entries: List<Entry>, goal: Int, today: Long, onImport: (String) -> Int) {
    val ctx = LocalContext.current
    var showImport by remember { mutableStateOf(false) }
    fun report(n: Int) {
        Toast.makeText(ctx, if (n > 0) "Imported $n entries" else if (n == 0) "Nothing new to import (already added, or not a Calorie Tracker CSV)" else "Couldn't read that file", Toast.LENGTH_LONG).show()
        showImport = false
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            report(if (text != null) onImport(text) else -1)
        }
    }
    if (showImport) {
        var pasted by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showImport = false }, title = { Text("Import from CSV") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Choose a file…") }
                    Text("or paste the exported text:", fontSize = 12.sp, color = Color.Gray)
                    OutlinedTextField(pasted, { pasted = it }, modifier = Modifier.fillMaxWidth().height(140.dp), placeholder = { Text("date,meal,food,qty,kcal …") })
                }
            },
            confirmButton = { Button(enabled = pasted.isNotBlank(), onClick = { report(onImport(pasted)) }) { Text("Import pasted text") } },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Cancel") } })
    }
    val days = (6 downTo 0).map { today - it }
    val totals = days.map { d -> entries.filter { it.day == d }.sumOf { it.total } }
    val max = maxOf(goal, totals.max()).coerceAtLeast(1)
    fun ok(d: Long) = entries.filter { it.day == d }.sumOf { it.total } in 1..goal
    var d = if (ok(today)) today else today - 1
    var streak = 0
    while (ok(d)) { streak++; d-- }
    val logged = totals.filter { it > 0 }
    val avg = if (logged.isEmpty()) 0 else logged.average().roundToInt()

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Last 7 days", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Day streak 🔥", "$streak", Modifier.weight(1f))
                Stat("Daily average", "$avg", Modifier.weight(1f))
            }
        }
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp)) {
                    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                        val slot = size.width / 7f; val bw = slot * 0.55f
                        totals.forEachIndexed { i, v ->
                            val bh = size.height * v / max
                            drawRoundRect(if (v > goal) Color(0xFFD32F2F) else Color(0xFF43A047), Offset(i * slot + (slot - bw) / 2, size.height - bh), Size(bw, bh), CornerRadius(12f, 12f))
                        }
                        val gy = size.height - size.height * goal / max
                        drawLine(Color.Gray, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 3f)
                    }
                    Row { days.forEach { Text(LocalDate.ofEpochDay(it).dayOfWeek.name.take(3), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = Color.Gray) } }
                    Text("Grey line = your goal ($goal kcal). Red = went over.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        item {
            Button(onClick = {
                val csv = "date,meal,food,qty,kcal\n" + entries.sortedBy { it.day }.joinToString("\n") { "${LocalDate.ofEpochDay(it.day)},${it.meal},\"${it.name}\",${it.qty},${it.total}" }
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Calorie log").putExtra(Intent.EXTRA_TEXT, csv), "Export / back up"))
            }, modifier = Modifier.fillMaxWidth()) { Text("Export / back up (CSV)") }
        }
        item { OutlinedButton(onClick = { showImport = true }, modifier = Modifier.fillMaxWidth()) { Text("Import from CSV") } }
    }
}

@Composable
fun QuickAdd(foods: List<Food>, onLog: (Food, Double, String) -> Unit) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("⚡ Quick add", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Tap once = 1 serving. Use + Add below for a different quantity.", fontSize = 12.sp, color = Color.Gray)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(foods) { f ->
                AssistChip(
                    onClick = {
                        onLog(f, 1.0, defaultMeal())
                        Toast.makeText(ctx, "Added 1 × ${f.name} (+${f.kcal} kcal)", Toast.LENGTH_SHORT).show()
                    },
                    label = { Text("${f.name} · ${f.kcal}", fontSize = 13.sp) }
                )
            }
        }
    }
}

private val csvRow = Regex("""^(\d{4}-\d{2}-\d{2}),([^,]+),"?(.*?)"?,([0-9.]+),(\d+)$""")

/** Reads text exported by "Export / back up (CSV)". Returns merged entries, merged foods, and how many entries were new. */
fun mergeImport(text: String, entries: List<Entry>, foods: List<Food>): Triple<List<Entry>, List<Food>, Int> {
    val parsed = text.lines().mapIndexedNotNull { i, raw ->
        val m = csvRow.matchEntire(raw.trim()) ?: return@mapIndexedNotNull null
        val (date, meal, name, q, total) = m.destructured
        val qty = q.toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapIndexedNotNull null
        val day = runCatching { LocalDate.parse(date).toEpochDay() }.getOrNull() ?: return@mapIndexedNotNull null
        Entry(System.nanoTime() + i, name.trim(), (total.toInt() / qty).roundToInt(), qty, day, meal.trim())
    }
    fun key(e: Entry) = listOf(e.day, e.meal, e.name, e.qty, e.total)
    val seen = entries.map { key(it) }.toSet()
    val fresh = parsed.filter { key(it) !in seen }
    val newFoods = fresh.distinctBy { it.name }.filter { e -> foods.none { it.name == e.name } }
        .mapIndexed { i, e -> Food(System.nanoTime() + i, e.name, e.kcal) }
    return Triple(entries + fresh, foods + newFoods, fresh.size)
}
