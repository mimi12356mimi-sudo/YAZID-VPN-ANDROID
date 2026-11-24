
package com.yazid.vpn

import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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

private val Purple = Color(0xFF9B5CFF)
private val CardColor = Color(0xFF191226)
private val Background = Color(0xFF090611)

data class ServerItem(
    val name: String,
    val protocol: String,
    val address: String
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
    val prefs = remember {
        context.getSharedPreferences("yazid_vpn", 0)
    }
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf(false) }
    var input by remember {
        mutableStateOf(prefs.getString("subscription_url", "") ?: "")
    }
    var savedUrl by remember {
        mutableStateOf(prefs.getString("subscription_url", "") ?: "")
    }

    val servers = remember { mutableStateListOf<ServerItem>() }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("هنوز اشتراکی اضافه نشده") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val arr = JSONArray(prefs.getString("servers", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                servers.add(
                    ServerItem(
                        o.optString("name"),
                        o.optString("protocol"),
                        o.optString("address")
                    )
                )
            }
            if (servers.isNotEmpty()) {
                message = "${servers.size} سرور ذخیره‌شده"
            }
        } catch (_: Exception) {
        }
    }

    fun importLink() {
        val link = input.trim()

        if (!link.startsWith("https://", true) &&
            !link.startsWith("http://", true)
        ) {
            error = "لینک باید با http یا https شروع شود"
            return
        }

        busy = true
        error = null

        scope.launch {
            try {
                val found = withContext(Dispatchers.IO) {
                    fetchSubscription(link)
                }

                if (found.isEmpty()) {
                    error = "کانفیگ قابل پشتیبانی پیدا نشد"
                } else {
                    servers.clear()
                    servers.addAll(found)
                    savedUrl = link
                    message = "${found.size} سرور دریافت شد"

                    val json = JSONArray()
                    found.forEach {
                        json.put(
                            JSONObject()
                                .put("name", it.name)
                                .put("protocol", it.protocol)
                                .put("address", it.address)
                        )
                    }

                    prefs.edit()
                        .putString("subscription_url", link)
                        .putString("servers", json.toString())
                        .apply()

                    dialog = false
                }
            } catch (e: Exception) {
                error = e.message ?: "خطا در دریافت اشتراک"
            } finally {
                busy = false
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Purple,
            background = Background,
            surface = CardColor
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF211039),
                            Background,
                            Background
                        )
                    )
                )
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    "YAZID VPN",
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Text(
                    "SECURE • FAST • PRIVATE",
                    fontSize = 10.sp,
                    color = Purple,
                    letterSpacing = 3.sp
                )

                Spacer(Modifier.height(25.dp))

                when (tab) {
                    0 -> {
                        Text(
                            "خوش اومدی 👋",
                            color = Color.White,
                            fontSize = 22.sp
                        )

                        Spacer(Modifier.height(16.dp))

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = CardColor
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = Purple,
                                    modifier = Modifier.size(58.dp)
                                )

                                Spacer(Modifier.height(12.dp))

                                Text(
                                    "مدیریت اشتراک",
                                    color = Color.White,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    message,
                                    color = Purple,
                                    fontSize = 13.sp
                                )

                                Spacer(Modifier.height(18.dp))

                                Button(
                                    onClick = {
                                        input = savedUrl
                                        error = null
                                        dialog = true
                                    },
                                    enabled = !busy,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Purple
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (busy) "در حال دریافت..."
                                        else if (savedUrl.isBlank())
                                            "افزودن اشتراک"
                                        else "بروزرسانی اشتراک"
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            "موتور اتصال VPN هنوز اضافه نشده است.",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }

                    1 -> {
                        Text(
                            "سرورها",
                            color = Color.White,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(12.dp))

                        if (servers.isEmpty()) {
                            Text(
                                "هنوز سروری دریافت نشده.",
                                color = Color.LightGray
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                items(servers) { server ->
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = CardColor
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            Modifier.padding(15.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Dns,
                                                contentDescription = null,
                                                tint = Purple
                                            )

                                            Spacer(Modifier.width(12.dp))

                                            Column {
                                                Text(
                                                    server.name,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )

                                                Text(
                                                    "${server.protocol} • ${server.address}",
                                                    color = Color.LightGray,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> Page(
                        "حساب کاربری",
                        if (savedUrl.isBlank())
                            "اشتراکی ثبت نشده"
                        else
                            "اشتراک روی دستگاه ذخیره شده است."
                    )

                    3 -> Page(
                        "تنظیمات",
                        "نسخه 1.1.0 • پشتیبانی: @YazidSup"
                    )
                }

                Spacer(Modifier.weight(1f))

                NavigationBar(
                    containerColor = Color(0xFF151020)
                ) {
                    val labels = listOf(
                        "خانه", "سرورها", "حساب", "تنظیمات"
                    )

                    val icons = listOf(
                        Icons.Default.Home,
                        Icons.Default.Dns,
                        Icons.Default.Person,
                        Icons.Default.Settings
                    )

                    labels.forEachIndexed { i, label ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = {
                                Icon(icons[i], label)
                            },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Purple,
                                selectedTextColor = Purple,
                                indicatorColor = Color(0xFF302047),
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray
                            )
                        )
                    }
                }
            }
        }

        if (dialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!busy) dialog = false
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

                        if (error != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                error ?: "",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !busy,
                        onClick = { importLink() }
                    ) {
                        Text(
                            if (busy) "در حال بررسی..."
                            else "دریافت و ذخیره"
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !busy,
                        onClick = { dialog = false }
                    ) {
                        Text("انصراف")
                    }
                }
            )
        }
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
            "YAZID-VPN-Android/1.1"
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
            it.protocol + it.address + it.name
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

                        result.add(
                            ServerItem(
                                name,
                                "VMess",
                                if (host.isBlank())
                                    "VMess"
                                else "$host:$port"
                            )
                        )
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

                        val host = uri.host ?: protocol
                        val port = if (uri.port >= 0)
                            ":${uri.port}"
                        else ""

                        result.add(
                            ServerItem(
                                name,
                                protocol,
                                host + port
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }

    return result
}

@Composable
fun Page(title: String, description: String) {
    Column {
        Text(
            title,
            color = Color.White,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        Text(
            description,
            color = Color.LightGray,
            fontSize = 14.sp
        )
    }
}
