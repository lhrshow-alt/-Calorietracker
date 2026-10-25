package com.example.calorietracker

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

data class Food(val id: Long, val name: String, val kcal: Int)
data class Entry(val id: Long, val name: String, val kcal: Int, val qty: Double, val day: Long, val meal: String = "Snack") {
    val total get() = (kcal * qty).roundToInt()
}

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("cal", Context.MODE_PRIVATE)
    private fun arr(k: String) = JSONArray(p.getString(k, "[]"))
    fun foods(): List<Food> = arr("foods").let { a -> (0 until a.length()).map { val o = a.getJSONObject(it); Food(o.getLong("id"), o.getString("name"), o.getInt("kcal")) } }
    fun saveFoods(l: List<Food>) = p.edit().putString("foods", JSONArray().also { a -> l.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("kcal", it.kcal)) } }.toString()).apply()
    fun entries(): List<Entry> = arr("entries").let { a -> (0 until a.length()).map { val o = a.getJSONObject(it); Entry(o.getLong("id"), o.getString("name"), o.getInt("kcal"), o.getDouble("qty"), o.getLong("day"), o.optString("meal", "Snack")) } }
    fun saveEntries(l: List<Entry>) = p.edit().putString("entries", JSONArray().also { a -> l.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("kcal", it.kcal).put("qty", it.qty).put("day", it.day).put("meal", it.meal)) } }.toString()).apply()
    fun goal() = p.getInt("goal", 2000)
    fun saveGoal(g: Int) = p.edit().putInt("goal", g).apply()
    fun water(d: Long) = p.getInt("w$d", 0)
    fun saveWater(d: Long, n: Int) = p.edit().putInt("w$d", n).apply()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(this)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF66BB6A)) else lightColorScheme(primary = Color(0xFF2E7D32))) { App(store) }
        }
    }
}

@Composable
fun App(store: Store) {
    var foods by remember { mutableStateOf(store.foods()) }
    var entries by remember { mutableStateOf(store.entries()) }
    var goal by remember { mutableStateOf(store.goal()) }
    var tab by remember { mutableStateOf(0) }
    var setup by remember { mutableStateOf(foods.isEmpty()) }
    val today = LocalDate.now().toEpochDay()
    var water by remember { mutableStateOf(store.water(today)) }

    val onFoods = { l: List<Food> -> foods = l; store.saveFoods(l) }
    if (setup) {
        FoodsScreen(foods, onFoods, onboarding = true) { if (foods.isNotEmpty()) setup = false }
        return
    }
    Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("📊") }, label = { Text("Dashboard") })
            NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("🍽️") }, label = { Text("My Foods") })
            NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Text("📈") }, label = { Text("History") })
        }
    }) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> Dashboard(foods, entries, today, goal, water,
                    onLog = { f, q, m -> entries = entries + Entry(System.nanoTime(), f.name, f.kcal, q, today, m); store.saveEntries(entries) },
                    onDelete = { e -> entries = entries.filter { it.id != e.id }; store.saveEntries(entries) },
                    onGoal = { goal = it; store.saveGoal(it) },
                    onWater = { water = it.coerceIn(0, 30); store.saveWater(today, water) },
                    onCopyYesterday = { entries = entries + entries.filter { it.day == today - 1 }.mapIndexed { i, e -> e.copy(id = System.nanoTime() + i, day = today) }; store.saveEntries(entries) })
                1 -> FoodsScreen(foods, onFoods, onboarding = false) {}
                else -> HistoryScreen(entries, goal, today)
            }
        }
    }
}

@Composable
fun Ring(eaten: Int, goal: Int) {
    val frac by animateFloatAsState((eaten.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f), tween(800), label = "ring")
    val over = eaten > goal
    val col = if (over) Color(0xFFD32F2F) else Color(0xFF2E7D32)
    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val st = Stroke(32f, cap = StrokeCap.Round)
            val tl = Offset(16f, 16f); val sz = Size(size.width - 32f, size.height - 32f)
            drawArc(Color.Gray.copy(alpha = 0.25f), -90f, 360f, false, tl, sz, style = st)
            drawArc(col, -90f, 360f * frac, false, tl, sz, style = st)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${abs(goal - eaten)}", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = col)
            Text(if (over) "kcal over goal" else "kcal remaining", color = Color.Gray)
        }
    }
}

@Composable
fun Stat(label: String, value: String, mod: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Card(mod, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (onClick != null) TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) { Text("$label ✎", fontSize = 12.sp) }
            else Text(label, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

@Composable
fun Dashboard(foods: List<Food>, all: List<Entry>, day: Long, goal: Int, water: Int, onLog: (Food, Double, String) -> Unit, onDelete: (Entry) -> Unit, onGoal: (Int) -> Unit, onWater: (Int) -> Unit, onCopyYesterday: () -> Unit) {
    val today = all.filter { it.day == day }
    val eaten = today.sumOf { it.total }
    val sorted = foods.sortedByDescending { f -> all.count { it.name == f.name } }
    var pick by remember { mutableStateOf<Food?>(null) }
    var editGoal by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF43A047), Color(0xFF1B5E20))), RoundedCornerShape(28.dp)).padding(20.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Today", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Card(shape = RoundedCornerShape(150.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) { Box(Modifier.padding(10.dp)) { Ring(eaten, goal) } }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Daily goal", "$goal", Modifier.weight(1f)) { editGoal = true }
                Stat("Eaten", "$eaten", Modifier.weight(1f))
                Stat("Remaining", "${(goal - eaten).coerceAtLeast(0)}", Modifier.weight(1f))
            }
        }
        item { QuickAdd(sorted, onLog) }
        item { WaterCard(water, onWater) }
        item { if (all.any { it.day == day - 1 }) OutlinedButton(onClick = onCopyYesterday, modifier = Modifier.fillMaxWidth()) { Text("↻ Same as yesterday") } }
        item { Text("What did you eat?", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        items(sorted) { f ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(f.name, fontWeight = FontWeight.SemiBold); Text("${f.kcal} kcal each", color = Color.Gray, fontSize = 13.sp) }
                    Button(onClick = { pick = f }) { Text("+ Add") }
                }
            }
        }
        item { Text("Today's log", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        item { Text(meals.joinToString("  ·  ") { m -> "$m ${today.filter { it.meal == m }.sumOf { it.total }}" }, color = Color.Gray, fontSize = 12.sp) }
        if (today.isEmpty()) item { Text("Nothing logged yet.", color = Color.Gray) }
        items(today.reversed()) { e ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${qtyText(e.qty)} × ${e.name}  ·  ${e.meal}", fontWeight = FontWeight.SemiBold)
                        Text("${e.kcal} × ${qtyText(e.qty)} = ${e.total} kcal", color = Color.Gray, fontSize = 13.sp)
                    }
                    TextButton(onClick = { onDelete(e) }) { Text("✕", color = Color(0xFFD32F2F)) }
                }
            }
        }
    }

    pick?.let { f ->
        var qty by remember(f) { mutableStateOf(1.0) }
        var meal by remember(f) { mutableStateOf(defaultMeal()) }
        AlertDialog(onDismissRequest = { pick = null },
            title = { Text("How many ${f.name}?") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        OutlinedButton(onClick = { if (qty > 0.5) qty -= 0.5 }) { Text("−") }
                        Text(qtyText(qty), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { qty += 0.5 }) { Text("+") }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("${f.kcal} × ${qtyText(qty)} = ${(f.kcal * qty).roundToInt()} kcal", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(meals) { m -> FilterChip(selected = meal == m, onClick = { meal = m }, label = { Text(m, fontSize = 12.sp) }) } }
                }
            },
            confirmButton = { Button(onClick = { onLog(f, qty, meal); pick = null }) { Text("Add to today") } },
            dismissButton = { TextButton(onClick = { pick = null }) { Text("Cancel") } })
    }
    if (editGoal) GoalDialog(goal, { editGoal = false }, onGoal)
}

fun qtyText(q: Double) = if (q % 1.0 == 0.0) q.toInt().toString() else q.toString()

@Composable
fun FoodsScreen(foods: List<Food>, onChange: (List<Food>) -> Unit, onboarding: Boolean, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Food?>(null) }
    val suggestions = listOf("Roti", "Rice (1 plate)", "Egg", "Chai", "Banana", "Biryani (1 plate)")
    editing?.let { f ->
        var n by remember(f) { mutableStateOf(f.name) }; var k by remember(f) { mutableStateOf(f.kcal.toString()) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("Edit food") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(n, { n = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(k, { k = it.filter(Char::isDigit) }, label = { Text("Calories per unit") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) } },
            confirmButton = { Button(onClick = { k.toIntOrNull()?.let { kk -> onChange(foods.map { if (it.id == f.id) it.copy(name = n.trim().ifBlank { f.name }, kcal = kk) else it }) }; editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } })
    }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(if (onboarding) "What do you usually eat?" else "My Foods", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Add a food once with the calories of ONE unit (e.g. 1 roti = 200). You'll just pick the quantity later.", color = Color.Gray)
        }
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Food name (e.g. Roti)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(kcal, { kcal = it.filter(Char::isDigit) }, label = { Text("Calories in 1 unit") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Text("Quick add (approximate - edit with ✎ later)", fontSize = 12.sp, color = Color.Gray)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(presets.filter { p -> foods.none { it.name == p.name } }) { p ->
                            AssistChip(onClick = { onChange(foods + p.copy(id = System.nanoTime())) }, label = { Text("${p.name} · ${p.kcal}", fontSize = 12.sp) })
                        }
                    }
                    Button(onClick = {
                        val k = kcal.toIntOrNull()
                        if (name.isNotBlank() && k != null) { onChange(foods + Food(System.nanoTime(), name.trim(), k)); name = ""; kcal = "" }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Add food") }
                }
            }
        }
        items(foods) { f ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(f.name, fontWeight = FontWeight.SemiBold); Text("${f.kcal} kcal per unit", color = Color.Gray, fontSize = 13.sp) }
                    TextButton(onClick = { editing = f }) { Text("✎") }
                    TextButton(onClick = { onChange(foods.filter { it.id != f.id }) }) { Text("✕", color = Color(0xFFD32F2F)) }
                }
            }
        }
        if (onboarding) item {
            Button(onClick = onDone, enabled = foods.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Continue to dashboard") }
        }
    }
}
