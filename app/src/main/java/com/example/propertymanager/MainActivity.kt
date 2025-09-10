package com.example.propertymanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text // Keep for error case
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
import com.example.propertymanager.ui.screens.PropertyScreen
import com.example.propertymanager.ui.screens.RoomScreen
import com.example.propertymanager.ui.screens.RoomDetailsScreen
import com.example.propertymanager.ui.viewmodel.PropertyViewModel
import com.example.propertymanager.ui.viewmodel.PropertyViewModelFactory
import com.example.propertymanager.ui.viewmodel.RoomViewModel
import com.example.propertymanager.ui.viewmodel.RoomViewModelFactory

class MainActivity : ComponentActivity() {

    private lateinit var propertyViewModel: PropertyViewModel
    // RoomViewModel is now created per propertyId, so no single instance here

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = PropertyManagerDatabase.getDatabase(applicationContext)
        val propertyDao = database.propertyDao()
        // DAOs for RoomViewModelFactory & PropertyViewModelFactory - can be defined once here
        val roomDao = database.roomDao()
        val tenantDao = database.tenantDao()
        val monthlyBillDao = database.monthlyBillDao()
        val paymentInstallmentDao = database.paymentInstallmentDao()

        // Repositories
        val propertyRepository = PropertyRepository(propertyDao)
        val roomRepository = RoomRepository(roomDao)
        val tenantRepository = TenantRepository(tenantDao)
        val monthlyBillRepository = MonthlyBillRepository(monthlyBillDao)
        val paymentInstallmentRepository = PaymentInstallmentRepository(paymentInstallmentDao)

        // PropertyViewModelFactory setup
        val propertyFactory = PropertyViewModelFactory(
            propertyRepository,
            roomRepository, 
            monthlyBillRepository, 
            paymentInstallmentRepository
        )
        propertyViewModel = ViewModelProvider(this, propertyFactory)[PropertyViewModel::class.java]


        setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "propertyList") {
                composable("propertyList") {
                    PropertyScreen(
                        viewModel = propertyViewModel,
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
                        )["RoomViewModel_property_$propertyId", RoomViewModel::class.java] // Unique key
                        
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
                            propertyId // Use propertyId for the factory
                        )
                        // Get the ViewModel scoped to this propertyId
                        val roomViewModel = ViewModelProvider(
                            this@MainActivity, 
                            roomViewModelFactory
                        )["RoomViewModel_property_$propertyId", RoomViewModel::class.java] // Unique key

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
