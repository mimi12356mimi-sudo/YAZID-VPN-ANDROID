
package com.yazid.vpn

import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit

private val Sky = Color(0xFF38BDF8)
private val Blue = Color(0xFF0284C7)
private val Navy = Color(0xFF10243A)
private val Background = Color(0xFFF1F8FE)
private val White = Color.White
private val TextDark = Color(0xFF183047)
private val Muted = Color(0xFF71869A)

data class ServerItem(
    val id: String,
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int
)

data class Subscription(
    val id: String,
    val name: String,
    val url: String,
    val servers: List<ServerItem>
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { YazidApp() }
    }
}

@Composable
fun YazidApp() {
    val context = androidx.compose.ui.platform.LocalContext.current

    // ذخیره‌سازی رمزنگاری‌شده روی گوشی
    val prefs = remember {
        SecurePrefs(context)
    }

    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val subscriptions = remember {
        mutableStateListOf<Subscription>()
    }

    val pingResults = remember {
        mutableStateMapOf<String, Int>()
    }

    var selectedServerId by remember {
        mutableStateOf(
            prefs.getString("selected_server", "") ?: ""
        )
    }

    var pingingId by remember { mutableStateOf<String?>(null) }

    fun saveAll() {
        val array = JSONArray()

        subscriptions.forEach { sub ->
            val servers = JSONArray()

            sub.servers.forEach { server ->
                servers.put(
                    JSONObject()
                        .put("id", server.id)
                        .put("name", server.name)
                        .put("protocol", server.protocol)
                        .put("host", server.host)
                        .put("port", server.port)
                )
            }

            array.put(
                JSONObject()
                    .put("id", sub.id)
                    .put("name", sub.name)
                    .put("url", sub.url)
                    .put("servers", servers)
            )
        }

        prefs.edit()
            .putString("subscriptions", array.toString())
            .putString("selected_server", selectedServerId)
            .apply()
    }

    LaunchedEffect(Unit) {
        try {
            val raw = prefs.getString("subscriptions", null)

            if (raw != null) {
                val array = JSONArray(raw)

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val serverArray = obj.getJSONArray("servers")
                    val serverList = mutableListOf<ServerItem>()

                    for (j in 0 until serverArray.length()) {
                        val s = serverArray.getJSONObject(j)

                        serverList.add(
                            ServerItem(
                                id = s.optString("id"),
                                name = s.optString("name"),
                                protocol = s.optString("protocol"),
                                host = s.optString("host"),
                                port = s.optInt("port")
                            )
                        )
                    }

                    subscriptions.add(
                        Subscription(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            url = obj.optString("url"),
                            servers = serverList
                        )
                    )
                }
            } else {
                // انتقال اشتراک نسخه قبلی به ساختار چنداشتراکی
                val oldUrl = prefs.getString("subscription_url", "") ?: ""
                val oldRaw = prefs.getString("servers", "[]") ?: "[]"

                if (oldUrl.isNotBlank()) {
                    val oldArray = JSONArray(oldRaw)
                    val oldServers = mutableListOf<ServerItem>()
                    val subId = UUID.randomUUID().toString()

                    for (i in 0 until oldArray.length()) {
                        val s = oldArray.getJSONObject(i)
                        val address = s.optString("address")
                        val host = address.substringBeforeLast(":")
                        val port = address.substringAfterLast(":")
                            .toIntOrNull() ?: 443

                        oldServers.add(
                            ServerItem(
                                id = UUID.randomUUID().toString(),
                                name = s.optString("name"),
                                protocol = s.optString("protocol"),
                                host = host,
                                port = port
                            )
                        )
                    }

                    subscriptions.add(
                        Subscription(
                            id = subId,
                            name = "اشتراک قبلی",
                            url = oldUrl,
                            servers = oldServers
                        )
                    )

                    saveAll()
                }
            }
        } catch (_: Exception) {
        }
    }

    val allServers = subscriptions.flatMap { it.servers }

    val bestServer = allServers
        .filter { (pingResults[it.id] ?: -1) >= 0 }
        .minByOrNull { pingResults[it.id] ?: Int.MAX_VALUE }

    val selectedServer = allServers.find {
        it.id == selectedServerId
    }

    fun addSubscription() {
        val link = input.trim()

        if (!link.startsWith("https://", true) &&
            !link.startsWith("http://", true)
        ) {
            error = "لینک اشتراک معتبر نیست"
            return
        }

        if (subscriptions.any { it.url == link }) {
            error = "این اشتراک قبلاً اضافه شده"
            return
        }

        busy = true
        error = null
        loadingMessage = "در حال دریافت اشتراک..."

        scope.launch {
            try {
                val servers = withContext(Dispatchers.IO) {
                    fetchSubscription(link)
                }

                if (servers.isEmpty()) {
                    error = "هیچ سرور قابل پشتیبانی پیدا نشد"
                } else {
                    val subscription = Subscription(
                        id = UUID.randomUUID().toString(),
                        name = "اشتراک ${subscriptions.size + 1}",
                        url = link,
                        servers = servers
                    )

                    subscriptions.add(subscription)
                    saveAll()

                    input = ""
                    showAdd = false
                }
            } catch (e: Exception) {
                error = e.message ?: "خطا در دریافت اشتراک"
            } finally {
                busy = false
                loadingMessage = ""
            }
        }
    }

    fun deleteSubscription(id: String) {
        val sub = subscriptions.find { it.id == id }

        if (sub != null) {
            sub.servers.forEach {
                pingResults.remove(it.id)
            }

            subscriptions.remove(sub)

            if (sub.servers.any { it.id == selectedServerId }) {
                selectedServerId = ""
            }

            saveAll()
        }

        showDelete = null
    }

    fun testPing(server: ServerItem) {
        if (pingingId != null) return

        pingingId = server.id

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runIcmpPing(server.host)
            }

            pingResults[server.id] = result ?: -1
            pingingId = null
        }
    }
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue,
            secondary = Sky,
            background = Background,
            surface = White
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFDDF3FF),
                            Background,
                            Color(0xFFF8FBFF)
                        )
                    )
                )
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp)
            ) {
                Spacer(Modifier.height(22.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "YAZID VPN",
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Black,
                            color = Navy
                        )

                        Text(
                            "FAST • SECURE • PRIVATE",
                            fontSize = 10.sp,
                            color = Blue,
                            letterSpacing = 2.sp
                        )
                    }

                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = Blue,
                        modifier = Modifier.size(39.dp)
                    )
                }

                Spacer(Modifier.height(20.dp))

                when (tab) {
                    0 -> {
                        Text(
                            "سلام، خوش اومدی 👋",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = Navy
                        )

                        Spacer(Modifier.height(15.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = White
                            ),
                            elevation = CardDefaults.cardElevation(3.dp)
                        ) {
                            Column(
                                Modifier.padding(20.dp)
                            ) {
                                Text(
                                    "وضعیت سرورها",
                                    color = Muted,
                                    fontSize = 13.sp
                                )

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    "${allServers.size} سرور موجود",
                                    color = Navy,
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    "${subscriptions.size} اشتراک ثبت‌شده",
                                    color = Blue,
                                    fontSize = 13.sp
                                )

                                HorizontalDivider(
                                    Modifier.padding(vertical = 16.dp),
                                    color = Background
                                )

                                Text(
                                    "سرور انتخاب‌شده",
                                    color = Muted,
                                    fontSize = 12.sp
                                )

                                Text(
                                    selectedServer?.name
                                        ?: "هنوز سروری انتخاب نشده",
                                    color = Navy,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                if (selectedServer != null) {
                                    Text(
                                        selectedServer.host,
                                        color = Muted,
                                        fontSize = 12.sp
                                    )
                                }

                                Spacer(Modifier.height(16.dp))

                                Text(
                                    "پیشنهاد برنامه",
                                    color = Blue,
                                    fontWeight = FontWeight.Bold
                                )

                                if (bestServer != null) {
                                    Text(
                                        bestServer.name,
                                        color = Navy,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        "پینگ: ${pingResults[bestServer.id]} ms",
                                        color = Blue
                                    )

                                    Button(
                                        onClick = {
                                            selectedServerId = bestServer.id
                                            saveAll()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("انتخاب بهترین سرور")
                                    }
                                } else {
                                    Text(
                                        "برای دریافت پیشنهاد، پینگ سرورها را بگیر.",
                                        color = Muted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(15.dp))

                        Button(
                            onClick = { showAdd = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Blue
                            )
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("افزودن اشتراک")
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            "اتصال واقعی VPN هنوز در این نسخه پیاده‌سازی نشده است.",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }

                    1 -> {
                        Text(
                            "سرورها",
                            color = Navy,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(10.dp))

                        if (allServers.isEmpty()) {
                            EmptyMessage("هنوز سروری اضافه نشده")
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 15.dp)
                            ) {
                                items(
                                    allServers,
                                    key = { it.id }
                                ) { server ->
                                    ServerCard(
                                        server = server,
                                        selected = server.id == selectedServerId,
                                        best = server.id == bestServer?.id,
                                        ping = pingResults[server.id],
                                        loading = pingingId == server.id,
                                        onSelect = {
                                            selectedServerId = server.id
                                            saveAll()
                                        },
                                        onPing = {
                                            testPing(server)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    2 -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "اشتراک‌های من",
                                color = Navy,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = { showAdd = true }
                            ) {
                                Icon(
                                    Icons.Default.AddCircle,
                                    null,
                                    tint = Blue
                                )
                            }
                        }

                        if (subscriptions.isEmpty()) {
                            EmptyMessage("اشتراکی ثبت نشده")
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(
                                    subscriptions,
                                    key = { it.id }
                                ) { sub ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = White
                                        )
                                    ) {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(15.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Cloud,
                                                null,
                                                tint = Blue,
                                                modifier = Modifier.size(28.dp)
                                            )

                                            Spacer(Modifier.width(12.dp))

                                            Column(
                                                Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    sub.name,
                                                    color = Navy,
                                                    fontWeight = FontWeight.Bold
                                                )

                                                Text(
                                                    "${sub.servers.size} سرور",
                                                    color = Muted,
                                                    fontSize = 12.sp
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    showDelete = sub.id
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    "حذف اشتراک",
                                                    tint = Color(0xFFEF4444)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        Text(
                            "تنظیمات",
                            color = Navy,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(15.dp))

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = White
                            ),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                Modifier.padding(18.dp)
                            ) {
                                Text(
                                    "YAZID VPN",
                                    color = Navy,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )

                                Text(
                                    "نسخه 1.2.0",
                                    color = Blue
                                )

                                Spacer(Modifier.height(12.dp))

                                Text(
                                    "پشتیبانی: @YazidSup",
                                    color = Muted
                                )

                                Text(
                                    "پینگ فقط با درخواست کاربر اجرا می‌شود.",
                                    color = Muted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                NavigationBar(
                    containerColor = White,
                    tonalElevation = 5.dp
                ) {
                    val labels = listOf(
                        "خانه", "سرورها", "اشتراک‌ها", "تنظیمات"
                    )

                    val icons = listOf(
                        Icons.Default.Home,
                        Icons.Default.Dns,
                        Icons.Default.Cloud,
                        Icons.Default.Settings
                    )

                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = {
                                Icon(icons[index], null)
                            },
                            label = {
                                Text(label, fontSize = 10.sp)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Blue,
                                selectedTextColor = Blue,
                                indicatorColor = Color(0xFFDDF3FF),
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        }

        if (showAdd) {
            AlertDialog(
                onDismissRequest = {
                    if (!busy) showAdd = false
                },
                title = {
                    Text("افزودن اشتراک")
                },
                text = {
                    Column {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            label = { Text("لینک اشتراک") },
                            placeholder = { Text("https://...") },
                            singleLine = true,
                            enabled = !busy
                        )

                        if (busy) {
                            Spacer(Modifier.height(12.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(25.dp),
                                color = Blue
                            )
                            Text(loadingMessage, color = Blue)
                        }

                        if (error != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                error ?: "",
                                color = Color.Red,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !busy,
                        onClick = { addSubscription() }
                    ) {
                        Text("دریافت و ذخیره")
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !busy,
                        onClick = { showAdd = false }
                    ) {
                        Text("انصراف")
                    }
                }
            )
        }

        if (showDelete != null) {
            AlertDialog(
                onDismissRequest = { showDelete = null },
                title = { Text("حذف اشتراک") },
                text = {
                    Text("این اشتراک و سرورهای مربوط به آن حذف شوند؟")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            deleteSubscription(showDelete!!)
                        }
                    ) {
                        Text(
                            "حذف",
                            color = Color.Red
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDelete = null }
                    ) {
                        Text("انصراف")
                    }
                }
            )
        }
    }
}@Composable
private fun ServerCard(
    server: ServerItem,
    selected: Boolean,
    best: Boolean,
    ping: Int?,
    loading: Boolean,
    onSelect: () -> Unit,
    onPing: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                Color(0xFFE0F2FE)
            else White
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Dns,
                    null,
                    tint = Blue,
                    modifier = Modifier.size(27.dp)
                )

                Spacer(Modifier.width(10.dp))

                Column(
                    Modifier.weight(1f)
                ) {
                    Text(
                        server.name.ifBlank { "بدون نام" },
                        color = Navy,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "${server.protocol} • ${server.host}:${server.port}",
                        color = Muted,
                        fontSize = 10.sp
                    )
                }

                if (best) {
                    Surface(
                        color = Color(0xFFD1FAE5),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            "بهترین",
                            color = Color(0xFF047857),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(
                                horizontal = 9.dp,
                                vertical = 5.dp
                            )
                        )
                    }
                } else if (selected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        tint = Blue
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when {
                        ping == null -> "پینگ تست نشده"
                        ping < 0 -> "بدون پاسخ"
                        else -> "$ping ms"
                    },
                    color = if (ping != null && ping >= 0)
                        Blue else Muted,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                OutlinedButton(
                    onClick = onPing,
                    enabled = !loading
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.NetworkPing,
                            null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("گرفتن پینگ")
                    }
                }

                Spacer(Modifier.width(5.dp))

                TextButton(onClick = onSelect) {
                    Text(
                        if (selected) "انتخاب‌شده" else "انتخاب",
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyMessage(message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(30.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            color = Muted
        )
    }
}

private fun runIcmpPing(host: String): Int? {
    if (host.isBlank()) return null

    return try {
        val process = ProcessBuilder(
            "ping",
            "-c",
            "1",
            "-W",
            "2",
            host
        )
            .redirectErrorStream(true)
            .start()

        val finished = process.waitFor(
            4,
            TimeUnit.SECONDS
        )

        if (!finished) {
            process.destroyForcibly()
            return null
        }

        val output = process.inputStream
            .bufferedReader()
            .use { it.readText() }

        if (process.exitValue() != 0) return null

        val match = Regex(
            """time[=<]([0-9.]+)\s*ms""",
            RegexOption.IGNORE_CASE
        ).find(output)

        val value = match?.groupValues?.getOrNull(1)
            ?.toDoubleOrNull() ?: return null

        value.toInt().coerceAtLeast(1)
    } catch (_: Exception) {
        null
    }
}

private fun fetchSubscription(link: String): List<ServerItem> {
    val connection =
        URL(link).openConnection() as HttpURLConnection

    connection.apply {
        requestMethod = "GET"
        connectTimeout = 20000
        readTimeout = 30000
        setRequestProperty(
            "User-Agent",
            "YAZID-VPN-Android/1.2"
        )
        instanceFollowRedirects = true
    }

    try {
        val code = connection.responseCode

        if (code !in 200..299) {
            throw IllegalStateException("خطای سرور: HTTP $code")
        }

        val body = connection.inputStream
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
            .trim()

        if (body.isEmpty()) {
            throw IllegalStateException("پاسخ اشتراک خالی است")
        }

        var result = parseLines(body)

        if (result.isEmpty()) {
            val decoded = decodePayload(body)

            if (decoded != null && decoded != body) {
                result = parseLines(decoded)
            }
        }

        return result.distinctBy {
            it.protocol + it.host + it.port + it.name
        }
    } finally {
        connection.disconnect()
    }
}

private fun decodePayload(value: String): String? {
    val cleaned = value.trim().replace("\\s".toRegex(), "")

    if (cleaned.isEmpty()) return null

    val padded = cleaned +
        "=".repeat((4 - cleaned.length % 4) % 4)

    return try {
        String(
            Base64.decode(
                padded,
                Base64.URL_SAFE or Base64.NO_WRAP
            ),
            Charsets.UTF_8
        )
    } catch (_: Exception) {
        try {
            String(
                Base64.decode(padded, Base64.DEFAULT),
                Charsets.UTF_8
            )
        } catch (_: Exception) {
            null
        }
    }
}

private fun parseLines(text: String): List<ServerItem> {
    val result = mutableListOf<ServerItem>()

    text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { line ->
            try {
                when {
                    line.startsWith("vmess://", true) -> {
                        val payload = line
                            .substringAfter("://")
                            .substringBefore("#")

                        val decoded = decodePayload(payload)
                            ?: return@forEach

                        val json = JSONObject(decoded)

                        val name = json.optString("ps")
                            .ifBlank { "بدون نام در اشتراک" }

                        val host = json.optString("add")
                        val port = json.optString("port")
                            .toIntOrNull() ?: 443

                        if (host.isNotBlank()) {
                            result.add(
                                ServerItem(
                                    UUID.randomUUID().toString(),
                                    name,
                                    "VMess",
                                    host,
                                    port
                                )
                            )
                        }
                    }

                    line.startsWith("vless://", true) ||
                    line.startsWith("ss://", true) ||
                    line.startsWith("trojan://", true) -> {
                        val uri = Uri.parse(line)

                        val protocol = when {
                            line.startsWith("vless://", true) ->
                                "VLESS"

                            line.startsWith("ss://", true) ->
                                "Shadowsocks"

                            else -> "Trojan"
                        }

                        val name = uri.fragment
                            ?.let { Uri.decode(it) }
                            ?.ifBlank { "بدون نام در اشتراک" }
                            ?: "بدون نام در اشتراک"

                        val host = uri.host ?: return@forEach
                        val port = if (uri.port > 0) uri.port else 443

                        result.add(
                            ServerItem(
                                UUID.randomUUID().toString(),
                                name,
                                protocol,
                                host,
                                port
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }

    return result
}
