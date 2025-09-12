package com.example.propertymanager

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text // Keep for error case
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.propertymanager.data.db.PropertyManagerDatabase
import com.example.propertymanager.data.repository.PropertyRepository
import com.example.propertymanager.data.repository.RoomRepository
import com.example.propertymanager.data.repository.TenantRepository
import com.example.propertymanager.data.repository.MonthlyBillRepository
import com.example.propertymanager.data.repository.PaymentInstallmentRepository
import com.example.propertymanager.data.repository.UserPreferencesRepository
import com.example.propertymanager.ui.screens.PropertyScreen
import com.example.propertymanager.ui.screens.RoomScreen
import com.example.propertymanager.ui.screens.RoomDetailsScreen
import com.example.propertymanager.ui.theme.MyApplication3Theme
import com.example.propertymanager.ui.viewmodel.PropertyViewModel
import com.example.propertymanager.ui.viewmodel.PropertyViewModelFactory
import com.example.propertymanager.ui.viewmodel.RoomViewModel
import com.example.propertymanager.ui.viewmodel.RoomViewModelFactory
import com.example.propertymanager.ui.viewmodel.ThemeViewModel
import com.example.propertymanager.ui.viewmodel.ThemeViewModelFactory
import com.example.propertymanager.utils.LanguageManager
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var propertyViewModel: PropertyViewModel
    private lateinit var themeViewModel: ThemeViewModel

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyPersistedLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentLangCode = LanguageManager.getCurrentLanguage(this)
        val locale = Locale(currentLangCode)
        Locale.setDefault(locale) // Set JVM default locale

        val resources = getResources()
        val config = Configuration(resources.configuration) // Get a mutable copy

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            config.setLocales(localeList)
        } else {
            config.setLocale(locale)
        }
        config.setLayoutDirection(locale)

        resources.updateConfiguration(config, resources.displayMetrics)

        val database = PropertyManagerDatabase.getDatabase(applicationContext)
        val propertyDao = database.propertyDao()
        val roomDao = database.roomDao()
        val tenantDao = database.tenantDao()
        val monthlyBillDao = database.monthlyBillDao()
        val paymentInstallmentDao = database.paymentInstallmentDao()

        val propertyRepository = PropertyRepository(propertyDao)
        val roomRepository = RoomRepository(roomDao)
        val tenantRepository = TenantRepository(tenantDao)
        val monthlyBillRepository = MonthlyBillRepository(monthlyBillDao)
        val paymentInstallmentRepository = PaymentInstallmentRepository(paymentInstallmentDao)
        val userPreferencesRepository = UserPreferencesRepository(applicationContext)

        val propertyFactory = PropertyViewModelFactory(
            application,
            propertyRepository,
            roomRepository,
            monthlyBillRepository,
            paymentInstallmentRepository,
            tenantRepository
        )
        propertyViewModel = ViewModelProvider(this, propertyFactory)[PropertyViewModel::class.java]

        val themeViewModelFactory = ThemeViewModelFactory(userPreferencesRepository)
        themeViewModel = ViewModelProvider(this, themeViewModelFactory)[ThemeViewModel::class.java]

        setContent {
            val currentTheme by themeViewModel.selectedTheme.collectAsState()

            CompositionLocalProvider(LocalConfiguration provides config) {
                MyApplication3Theme(appTheme = currentTheme) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "propertyList") {
                        composable(route = "propertyList") {
                            PropertyScreen(
                                viewModel = propertyViewModel,
                                themeViewModel = themeViewModel,
                                onPropertyClick = { propertyId ->
                                    navController.navigate("roomList/$propertyId")
                                }
                            )
                        }
                        composable(
                            route = "roomList/{propertyId}",
                            arguments = listOf(navArgument("propertyId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val propertyId = backStackEntry.arguments?.getInt("propertyId")
                            if (propertyId != null) {
                                val roomViewModelFactory = RoomViewModelFactory(
                                    roomRepository,
                                    tenantRepository,
                                    monthlyBillRepository,
                                    paymentInstallmentRepository,
                                    propertyId
                                )
                                val roomViewModel = ViewModelProvider(
                                    this@MainActivity,
                                    roomViewModelFactory
                                )["RoomViewModel_property_$propertyId", RoomViewModel::class.java]
                                RoomScreen(
                                    propertyId = propertyId,
                                    roomViewModel = roomViewModel,
                                    navController = navController
                                )
                            } else {
                                Text("Error: Property ID not found")
                            }
                        }
                        composable(
                            route = "room_details/{propertyId}/{roomId}",
                            arguments = listOf(
                                navArgument("propertyId") { type = NavType.IntType },
                                navArgument("roomId") { type = NavType.IntType }
                            )
                        ) { backStackEntry ->
                            val propertyId = backStackEntry.arguments?.getInt("propertyId")
                            val roomId = backStackEntry.arguments?.getInt("roomId")
                            if (propertyId != null && roomId != null) {
                                val roomViewModelFactory = RoomViewModelFactory(
                                    roomRepository,
                                    tenantRepository,
                                    monthlyBillRepository,
                                    paymentInstallmentRepository,
                                    propertyId
                                )
                                val roomViewModel = ViewModelProvider(
                                    this@MainActivity,
                                    roomViewModelFactory
                                )["RoomViewModel_property_$propertyId", RoomViewModel::class.java]
                                RoomDetailsScreen(
                                    roomId = roomId,
                                    roomViewModel = roomViewModel,
                                    navController = navController
                                )
                            } else {
                                Text("Error: Property or Room ID not found. Please go back.")
                            }
                        }
                    }
                }
            }
        }
    }
}
