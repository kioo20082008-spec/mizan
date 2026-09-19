package com.mizan.money.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

// Registry of icons a user can assign to a category. `catIcon()` consults
// `assigned` first so custom picks appear everywhere (transaction cards,
// budget rows, widget source data) without changing every call site to a
// composable overload. `assigned` is kept in sync by MainViewModel from prefs.
object CategoryIcons {
    data class Option(val key: String, val icon: ImageVector)

    val options: List<Option> = listOf(
        Option("category", Icons.Default.Category),
        Option("home", Icons.Default.Home),
        Option("restaurant", Icons.Default.Restaurant),
        Option("fastfood", Icons.Default.Fastfood),
        Option("pizza", Icons.Default.LocalPizza),
        Option("cafe", Icons.Default.LocalCafe),
        Option("grocery", Icons.Default.ShoppingCart),
        Option("shopping", Icons.Default.ShoppingBag),
        Option("gift", Icons.Default.CardGiftcard),
        Option("car", Icons.Default.DirectionsCar),
        Option("fuel", Icons.Default.LocalGasStation),
        Option("bus", Icons.Default.DirectionsBus),
        Option("train", Icons.Default.Train),
        Option("two_wheeler", Icons.Default.TwoWheeler),
        Option("flight", Icons.Default.Flight),
        Option("hotel", Icons.Default.Hotel),
        Option("receipt", Icons.Default.Receipt),
        Option("phone", Icons.Default.PhoneAndroid),
        Option("wifi", Icons.Default.Wifi),
        Option("bolt", Icons.Default.Bolt),
        Option("water", Icons.Default.WaterDrop),
        Option("health", Icons.Default.LocalHospital),
        Option("fitness", Icons.Default.FitnessCenter),
        Option("spa", Icons.Default.Spa),
        Option("movie", Icons.Default.Movie),
        Option("music", Icons.Default.MusicNote),
        Option("games", Icons.Default.SportsEsports),
        Option("sports", Icons.Default.SportsSoccer),
        Option("subscriptions", Icons.Default.Subscriptions),
        Option("school", Icons.Default.School),
        Option("book", Icons.Default.MenuBook),
        Option("work", Icons.Default.Work),
        Option("build", Icons.Default.Build),
        Option("brush", Icons.Default.Brush),
        Option("camera", Icons.Default.CameraAlt),
        Option("pets", Icons.Default.Pets),
        Option("child", Icons.Default.ChildCare),
        Option("celebration", Icons.Default.Celebration),
        Option("transfer", Icons.Default.SwapHoriz),
        Option("cash", Icons.Default.LocalAtm),
        Option("savings", Icons.Default.Savings),
        Option("wallet", Icons.Default.AccountBalanceWallet),
        Option("bank", Icons.Default.AccountBalance),
        Option("credit", Icons.Default.CreditCard),
        Option("star", Icons.Default.Star),
        Option("favorite", Icons.Default.Favorite),
    )

    private val byKey: Map<String, ImageVector> = options.associate { it.key to it.icon }

    // Compose state so `catIcon()` refreshes in every composable the moment the
    // user picks an icon, without each screen needing to observe it manually.
    var assigned: Map<String, String> by mutableStateOf(emptyMap())

    fun iconFor(cat: String): ImageVector? = assigned[cat]?.let { byKey[it] }

    fun iconForKey(key: String?): ImageVector? = key?.let { byKey[it] }
}
