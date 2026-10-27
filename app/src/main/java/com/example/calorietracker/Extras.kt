package com.example.calorietracker

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
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
import java.time.YearMonth
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
        item { CalendarCard(entries, goal, today) }
        item {
            Button(onClick = {
                runCatching { shareXlsx(ctx, entries, goal) }.onFailure { Toast.makeText(ctx, "Couldn't create the Excel file", Toast.LENGTH_LONG).show() }
            }, modifier = Modifier.fillMaxWidth()) { Text("Export as Excel (.xlsx)") }
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

@Composable
fun CalendarCard(entries: List<Entry>, goal: Int, today: Long) {
    var month by remember { mutableStateOf(YearMonth.from(LocalDate.ofEpochDay(today))) }
    var sel by remember { mutableStateOf(today) }
    val byDay = remember(entries) { entries.groupBy { it.day }.mapValues { (_, l) -> l.sumOf { it.total } } }
    val offset = month.atDay(1).dayOfWeek.value - 1 // Monday first
    val rows = (offset + month.lengthOfMonth() + 6) / 7
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { month = month.minusMonths(1) }) { Text("◀") }
                Text("${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                TextButton(onClick = { month = month.plusMonths(1) }) { Text("▶") }
            }
            Row { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = Color.Gray) } }
            for (r in 0 until rows) {
                Row {
                    for (c in 0 until 7) {
                        val day = r * 7 + c - offset + 1
                        if (day < 1 || day > month.lengthOfMonth()) { Spacer(Modifier.weight(1f)); continue }
                        val d = month.atDay(day).toEpochDay()
                        val total = byDay[d] ?: 0
                        val shape = RoundedCornerShape(10.dp)
                        val bg = when { total == 0 -> Color.Transparent; total > goal -> Color(0x33D32F2F); else -> Color(0x3343A047) }
                        Box(
                            Modifier.weight(1f).height(52.dp).padding(2.dp).clip(shape).background(bg)
                                .then(if (d == sel) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                                .clickable { sel = d },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$day", fontSize = 12.sp, fontWeight = if (d == today) FontWeight.ExtraBold else FontWeight.Normal)
                                if (total > 0) Text("$total", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
            Text("Number under the date = kcal used that day. Green = within goal, red = over.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(8.dp))
            val dayEntries = entries.filter { it.day == sel }
            val tot = dayEntries.sumOf { it.total }
            Text(LocalDate.ofEpochDay(sel).toString(), fontWeight = FontWeight.Bold)
            Text(
                if (dayEntries.isEmpty()) "Nothing logged." else "$tot of $goal kcal used · " + if (tot > goal) "${tot - goal} over" else "${goal - tot} left",
                color = Color.Gray, fontSize = 13.sp
            )
            meals.forEach { m ->
                val l = dayEntries.filter { it.meal == m }
                if (l.isNotEmpty()) {
                    Text("$m · ${l.sumOf { it.total }} kcal", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    l.forEach { Text("   ${qtyText(it.qty)} × ${it.name} = ${it.total}", fontSize = 12.sp, color = Color.Gray) }
                }
            }
        }
    }
}

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun sheetXml(rows: List<List<Any>>): String {
    val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
    rows.forEachIndexed { r, row ->
        sb.append("<row r=\"${r + 1}\">")
        row.forEachIndexed { c, v ->
            val ref = "${'A' + c}${r + 1}"
            if (v is Number) sb.append("<c r=\"$ref\"><v>$v</v></c>")
            else sb.append("<c r=\"$ref\" t=\"inlineStr\"><is><t>${esc(v.toString())}</t></is></c>")
        }
        sb.append("</row>")
    }
    return sb.append("</sheetData></worksheet>").toString()
}

/** Writes a real .xlsx (zip of XML parts) with two sheets: Log and Daily totals. */
fun buildXlsx(file: File, entries: List<Entry>, goal: Int) {
    val sorted = entries.sortedBy { it.day }
    val log = listOf<List<Any>>(listOf("Date", "Meal", "Food", "Quantity", "Kcal per unit", "Total kcal")) +
        sorted.map { listOf<Any>(LocalDate.ofEpochDay(it.day).toString(), it.meal, it.name, it.qty, it.kcal, it.total) }
    val daily = listOf<List<Any>>(listOf("Date", "Total kcal", "Goal", "Left (+) / Over (-)")) +
        sorted.groupBy { it.day }.toSortedMap().map { (d, l) -> val t = l.sumOf { it.total }; listOf<Any>(LocalDate.ofEpochDay(d).toString(), t, goal, goal - t) }
    val rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    ZipOutputStream(file.outputStream()).use { z ->
        fun put(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray(Charsets.UTF_8)); z.closeEntry() }
        val head = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
        put("[Content_Types].xml", head + """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
        put("_rels/.rels", head + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$rel/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
        put("xl/workbook.xml", head + """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="$rel"><sheets><sheet name="Log" sheetId="1" r:id="rId1"/><sheet name="Daily totals" sheetId="2" r:id="rId2"/></sheets></workbook>""")
        put("xl/_rels/workbook.xml.rels", head + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$rel/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="$rel/worksheet" Target="worksheets/sheet2.xml"/></Relationships>""")
        put("xl/worksheets/sheet1.xml", sheetXml(log))
        put("xl/worksheets/sheet2.xml", sheetXml(daily))
    }
}

fun shareXlsx(ctx: Context, entries: List<Entry>, goal: Int) {
    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
    val f = File(dir, "CalorieTracker-${LocalDate.now()}.xlsx")
    buildXlsx(f, entries, goal)
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    val send = Intent(Intent.ACTION_SEND).setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri("", uri)
    ctx.startActivity(Intent.createChooser(send, "Export Excel"))
}
