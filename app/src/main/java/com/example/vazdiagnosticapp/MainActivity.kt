package com.example.vazdiagnosticapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var statusText by mutableStateOf("Статус: Отключено")
    private var isConnected by mutableStateOf(false)
    private var consoleText by mutableStateOf("Лог диагностики:\n")

    // Датчики автомобиля
    private var engineRpm by mutableStateOf(0)
    private var coolantTemp by mutableStateOf(0)
    private var batteryVoltage by mutableStateOf(0.0)
    private var throttlePosition by mutableStateOf(0)

    private val activeErrors = mutableStateListOf<String>()
    private var isReadingErrors by mutableStateOf(false)

    private val dtcDatabase = mapOf(
        "P0102" to "Низкий уровень сигнала цепи датчика массового расхода воздуха (ДМРВ)",
        "P0103" to "Высокий уровень сигнала цепи датчика массового расхода воздуха (ДМРВ)",
        "P0116" to "Выход сигнала датчика температуры охлаждающей жидкости из допустимого диапазона",
        "P0117" to "Низкий уровень сигнала датчика температуры охлаждающей жидкости",
        "P0118" to "Высокий уровень сигнала датчика температуры охлаждающей жидкости",
        "P0122" to "Низкий уровень сигнала датчика положения дроссельной заслонки",
        "P0123" to "Высокий уровень сигнала датчика положения дроссельной заслонки",
        "P0130" to "Датчик кислорода до нейтрализатора неисправен",
        "P0171" to "Слишком бедная смесь в системе подачи топлива",
        "P0172" to "Слишком богатая смесь в системе подачи топлива",
        "P0300" to "Обнаружены случайные/множественные пропуски зажигания",
        "P0301" to "Обнаружены пропуски зажигания в 1-м цилиндре",
        "P0302" to "Обнаружены пропуски зажигания во 2-м цилиндре",
        "P0303" to "Обнаружены пропуски зажигания в 3-м цилиндре",
        "P0304" to "Обнаружены пропуски зажигания в 4-м цилиндре",
        "P0327" to "Низкий уровень сигнала датчика детонации",
        "P0328" to "Высокий уровень сигнала датчика детонации",
        "P0422" to "Эффективность нейтрализатора ниже допустимого порога",
        "P0500" to "Неисправность цепи датчика скорости автомобиля",
        "P0562" to "Пониженное напряжение бортовой сети автомобиля",
        "P0563" to "Повышенное напряжение бортовой сети автомобиля"
    )

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            statusText = if (permissions.values.all { it }) "Разрешения получены. Готов к работе." else "Ошибка: Доступы отклонены!"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }

        setContent {
            var selectedTab by remember { mutableStateOf(0) }

            MaterialTheme(colorScheme = darkColorScheme()) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("ВАЗ-2114 Диагностика", fontWeight = FontWeight.Bold) },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Text("📊", fontSize = 20.sp) },
                                label = { Text("Панель") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Text("⚠️", fontSize = 20.sp) },
                                label = { Text("Ошибки") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Text("💻", fontSize = 20.sp) },
                                label = { Text("Терминал") }
                            )
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp)
                    ) {
                        ConnectionHeader()
                        Spacer(modifier = Modifier.height(16.dp))
                        when (selectedTab) {
                            0 -> DashboardScreen()
                            1 -> ErrorsScreen()
                            2 -> TerminalScreen()
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ConnectionHeader() {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = statusText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { if (isConnected) disconnect() else connectToAvtovizor() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isConnected) "Выйти" else "Связь")
                }
            }
        }
    }

    @Composable
    fun DashboardScreen() {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SensorCard("Обороты", "$engineRpm", "об/мин", Modifier.weight(1f), Color(0xFF388E3C))
                SensorCard("Температура ОЖ", "$coolantTemp", "°C", Modifier.weight(1f), if (coolantTemp > 102) Color(0xFFD32F2F) else Color(0xFF1976D2))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SensorCard("Вольтаж сети", String.format(Locale.US, "%.1f", batteryVoltage), "В", Modifier.weight(1f), Color(0xFFF57C00))
                SensorCard("Дроссель", "$throttlePosition", "%", Modifier.weight(1f), Color(0xFF7B1FA2))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { startLiveUpdateData() },
                enabled = isConnected,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔄 Опросить все датчики")
            }
        }
    }

    @Composable
    fun SensorCard(title: String, value: String, unit: String, modifier: Modifier, accentColor: Color) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = modifier.height(130.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = value, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = accentColor)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = unit, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
        }
    }
    @Composable
    fun ErrorsScreen() {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ошибки ЭБУ:", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Button(
                    onClick = { readVehicleErrors() },
                    enabled = isConnected && !isReadingErrors
                ) {
                    Text(if (isReadingErrors) "Чтение..." else "Читать ошибки")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (activeErrors.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Нажмите кнопку выше для чтения ошибок.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    activeErrors.forEach { error ->
                        val description = dtcDatabase[error] ?: "Неизвестный код ошибки (Проверьте по таблице ВАЗ)"
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0x22D32F2F)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = error, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF5350))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { clearVehicleErrors() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🔥 Стереть ошибки из ЭБУ (Сброс)")
                    }
                }
            }
        }
    }
    @Composable
    fun TerminalScreen() {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black, RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(text = consoleText, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.Green)
            }
        }
    }
    @SuppressLint("MissingPermission")
    private fun connectToAvtovizor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            statusText = "Нет разрешения на Bluetooth! Включите в настройках приложения."
            Toast.makeText(this, "Дайте разрешение 'Устройства поблизости' в настройках телефона!", Toast.LENGTH_LONG).show()
            return
        }
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "Сначала включите Bluetooth на телефоне!", Toast.LENGTH_SHORT).show()
            return
        }
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        val adapterDevice = pairedDevices?.find { device ->
            val devName = device.name ?: ""
            devName.contains("avto", ignoreCase = true) ||
                    devName.contains("vizor", ignoreCase = true) ||
                    devName.contains("ELM", ignoreCase = true) ||
                    devName.contains("OBD", ignoreCase = true)
        }

        if (adapterDevice == null) {
            statusText = "Адаптер avtovizor не найден в сохраненных!"
            Toast.makeText(this, "Проверьте, что avtovizor есть в списке сохраненных в настройках Bluetooth!", Toast.LENGTH_LONG).show()
            return
        }
        statusText = "Подключение к ${adapterDevice.name}..."
        Thread {
            try {
                bluetoothAdapter?.cancelDiscovery()
                bluetoothSocket = adapterDevice.createRfcommSocketToServiceRecord(sppUuid)
                bluetoothSocket?.connect()
                outputStream = bluetoothSocket?.outputStream
                inputStream = bluetoothSocket?.inputStream
                isConnected = true
                statusText = "Подключено: ${adapterDevice.name}"
                initElm()
            } catch (ex: Exception) {
                isConnected = false
                statusText = "Ошибка подключения. Проверьте зажигание!"
                consoleText += "Ошибка сокета: ${ex.message}\n"
            }
        }.start()
    }
    private fun initElm() {
        executeCommand("ATZ\r", 1000)
        executeCommand("ATE0\r", 400)
        executeCommand("ATSP5\r", 500)
    }
    private fun startLiveUpdateData() {
        Thread {
            try {
// 1. Обороты двигателя (010C)
                val rpmResp = executeCommand("010C\r", 300)
                if (rpmResp.contains("41 0C")) {
                    val parts = rpmResp.trim().split(" ").filter { it.isNotEmpty() }
                    if (parts.size >= 4) {
                        val a = parts[2].toInt(16)
                        val b = parts[3].toInt(16)
                        engineRpm = ((a * 256) + b) / 4
                    }
                }
// 2. Температура охлаждающей жидкости (0105)
                val tempResp = executeCommand("0105\r", 300)
                if (tempResp.contains("41 05")) {
                    val parts = tempResp.trim().split(" ").filter { it.isNotEmpty() }
                    if (parts.size >= 3) {
                        val a = parts[2].toInt(16)
                        coolantTemp = a - 40
                    }
                }
// 3. Бортовое напряжение
                val voltResp = executeCommand("ATRV\r", 300)
                val digits = voltResp.filter { it.isDigit() || it == '.' }
                batteryVoltage = digits.toDoubleOrNull() ?: 12.5
// 4. Положение дроссельной заслонки (0111)
                val throttleResp = executeCommand("0111\r", 300)
                if (throttleResp.contains("41 11")) {
                    val parts = throttleResp.trim().split(" ").filter { it.isNotEmpty() }
                    if (parts.size >= 3) {
                        val a = parts[2].toInt(16)
                        throttlePosition = (a * 100) / 255
                    }
                }
            } catch (ex: Exception) {
                consoleText += "Ошибка парсинга датчиков: ${ex.message}\n"
            }
        }.start()
    }
    private fun readVehicleErrors() {
        isReadingErrors = true
        activeErrors.clear()
        Thread {
            try {
                val rawResponse = executeCommand("03\r", 600)
                if (rawResponse.contains("43")) {
                    val hexBytes = rawResponse.replace("\r", " ").replace("\n", " ")
                        .split(" ")
                        .filter { it.length == 2 && it != "43" }
                    for (i in hexBytes.indices step 2) {
                        if (i + 1 < hexBytes.size) {
                            val b1 = hexBytes[i]
                            val b2 = hexBytes[i + 1]
                            if (b1 != "00" || b2 != "00") {
                                val typeChar = when (b1.take(1)) {
                                    "0" -> "P0"
                                    "1" -> "P1"
                                    "2" -> "P2"
                                    "3" -> "P3"
                                    else -> "P"
                                }
                                val rawCode = typeChar + b1.drop(1) + b2
                                if (!activeErrors.contains(rawCode)) activeErrors.add(rawCode)
                            }
                        }
                    }
                }
            } catch (ex: Exception) {
                consoleText += "Ошибка чтения DTC: ${ex.message}\n"
            } finally {
                isReadingErrors = false
            }
        }.start()
    }
    private fun clearVehicleErrors() {
        Thread {
            executeCommand("04\r", 1000)
            runOnUiThread {
                activeErrors.clear()
                Toast.makeText(this@MainActivity, "Команда сброса Check Engine отправлена", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }
    private fun executeCommand(command: String, timeout: Long): String {
        val out = outputStream ?: return ""
        val ins = inputStream ?: return ""
        return try {
            out.write(command.toByteArray())
            out.flush()
            Thread.sleep(timeout)
            val buffer = ByteArray(1024)
            var bytes: Int
            val responseBuilder = StringBuilder()
            while (ins.available() > 0 || responseBuilder.isEmpty()) {
                bytes = ins.read(buffer)
                if (bytes > 0) {
                    val chunk = String(buffer, 0, bytes)
                    responseBuilder.append(chunk)
                    if (chunk.contains(">")) break
                } else break
            }
            val cleanResponse = responseBuilder.toString().replace(">", "").trim()
            consoleText += "-> $command\n<- $cleanResponse\n"
            cleanResponse
        } catch (ex: Exception) {
            "ERROR"
        }
    }
    private fun disconnect() {
        try {
            bluetoothSocket?.close()
            isConnected = false
            statusText = "Статус: Отключено"
            engineRpm = 0
            coolantTemp = 0
            batteryVoltage = 0.0
            throttlePosition = 0
            activeErrors.clear()
        } catch (ex: Exception) {
// Ошибка закрытия
        }
    }
}