
package com.yazid.vpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

private val Background = Color(0xFF090611)
private val Purple = Color(0xFF9B5CFF)
private val CardColor = Color(0xFF191226)

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
    var selectedTab by remember { mutableIntStateOf(0) }
    var showDialog by remember { mutableStateOf(false) }
    var subscription by remember { mutableStateOf("") }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Purple,
            background = Background,
            surface = CardColor
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF211039), Background, Background)
                    )
                )
        ) {
            Column(
                modifier = Modifier
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

                Spacer(Modifier.height(30.dp))

                when (selectedTab) {
                    0 -> {
                        Text("خوش اومدی 👋", color = Color.White, fontSize = 22.sp)
                        Spacer(Modifier.height(20.dp))

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = CardColor
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = Purple,
                                    modifier = Modifier.size(65.dp)
                                )
                                Spacer(Modifier.height(15.dp))
                                Text(
                                    "اتصال برقرار نیست",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "برای شروع، اشتراک خود را اضافه کن",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(24.dp))
                                Button(
                                    onClick = { showDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Purple
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("افزودن اشتراک")
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = CardColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Text("وضعیت اشتراک", color = Color.White)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (subscription.isBlank()) "اشتراکی اضافه نشده"
                                    else "لینک اشتراک وارد شده",
                                    color = Purple
                                )
                            }
                        }
                    }

                    1 -> Page("سرورها", "لیست سرورها پس از اتصال به اشتراک نمایش داده می‌شود.")
                    2 -> Page("حساب کاربری", "اطلاعات حساب پس از دریافت اشتراک نمایش داده می‌شود.")
                    3 -> Page("تنظیمات", "تنظیمات برنامه در این قسمت قرار می‌گیرد.")
                }

                Spacer(Modifier.weight(1f))

                NavigationBar(
                    containerColor = Color(0xFF151020)
                ) {
                    val items = listOf("خانه", "سرورها", "حساب", "تنظیمات")
                    val icons = listOf(
                        Icons.Default.Home,
                        Icons.Default.Dns,
                        Icons.Default.Person,
                        Icons.Default.Settings
                    )

                    items.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(icons[index], contentDescription = title)
                            },
                            label = { Text(title) },
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

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("افزودن اشتراک") },
                text = {
                    OutlinedTextField(
                        value = subscription,
                        onValueChange = { subscription = it },
                        label = { Text("لینک اشتراک") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("ذخیره")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("انصراف")
                    }
                }
            )
        }
    }
}

@Composable
fun Page(title: String, description: String) {
    Column {
        Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(description, color = Color.LightGray, fontSize = 14.sp)
    }
}
