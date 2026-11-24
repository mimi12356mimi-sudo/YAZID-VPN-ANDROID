
package com.yazid.vpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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

private val Background = Color(0xFF100B1C)
private val SurfaceDark = Color(0xFF211832)
private val Purple = Color(0xFF9B65FF)
private val Muted = Color(0xFFB9ADC9)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YazidApp()
        }
    }
}

@Composable
fun YazidApp() {
    var selectedTab by remember { mutableStateOf(0) }
    var showImport by remember { mutableStateOf(false) }
    var subscriptionUrl by remember { mutableStateOf("") }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Purple,
            background = Background,
            surface = SurfaceDark
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF281343),
                            Background,
                            Color(0xFF090711)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                Purple.copy(alpha = 0.2f),
                                RoundedCornerShape(15.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Y",
                            color = Color.White,
                            fontSize = 29.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column {
                        Text(
                            "YAZID VPN",
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "SECURE CONNECTION",
                            color = Muted,
                            fontSize = 10.sp,
                            letterSpacing = 2.sp
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = Purple,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.height(35.dp))

                when (selectedTab) {
                    0 -> HomePage(
                        onImport = { showImport = true }
                    )

                    1 -> SimplePage(
                        "سرورها",
                        "پس از اضافه‌شدن اشتراک، سرورها اینجا نمایش داده می‌شوند."
                    )

                    2 -> SimplePage(
                        "حساب کاربری",
                        "اطلاعات حجم و تاریخ انقضای اشتراک اینجا نمایش داده می‌شود."
                    )

                    3 -> SimplePage(
                        "تنظیمات",
                        "تنظیمات برنامه"
                    )
                }

                Spacer(Modifier.weight(1f))

                NavigationBar(
                    containerColor = Color(0xEE171021)
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

                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(
                                    icons[index],
                                    contentDescription = label
                                )
                            },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Purple,
                                selectedTextColor = Purple,
                                indicatorColor = Purple.copy(alpha = 0.18f),
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        }

        if (showImport) {
            AlertDialog(
                onDismissRequest = { showImport = false },
                containerColor = SurfaceDark,
                title = {
                    Text("افزودن اشتراک")
                },
                text = {
                    OutlinedTextField(
                        value = subscriptionUrl,
                        onValueChange = { subscriptionUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text("لینک اشتراک را وارد کن")
                        },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { showImport = false }
                    ) {
                        Text("ذخیره")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showImport = false }
                    ) {
                        Text("انصراف")
                    }
                }
            )
        }
    }
}

@Composable
fun HomePage(onImport: () -> Unit) {
    Column {
        Text(
            "اتصال امن، ساده و سریع",
            color = Muted,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = SurfaceDark
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("وضعیت اتصال", color = Muted)

                Spacer(Modifier.height(22.dp))

                Box(
                    modifier = Modifier
                        .size(155.dp)
                        .background(
                            Purple.copy(alpha = 0.12f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(
                                Purple.copy(alpha = 0.2f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            tint = Purple,
                            modifier = Modifier.size(58.dp)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    "هنوز متصل نیستید",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "برای شروع، اشتراک خود را اضافه کنید",
                    color = Muted,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = onImport,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        Icons.Default.AddLink,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("افزودن اشتراک")
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    "موتور اتصال هنوز در این نسخه فعال نشده است",
                    color = Color(0xFFFFC46B),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun SimplePage(title: String, description: String) {
    Column {
        Text(
            title,
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(15.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = SurfaceDark
            ),
            shape = RoundedCornerShape(22.dp)
        ) {
            Text(
                description,
                modifier = Modifier.padding(20.dp),
                color = Muted
            )
        }
    }
}
