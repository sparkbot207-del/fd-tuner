package com.bretthalliday.fdtuner

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.bretthalliday.fdtuner.ble.ConnectionState
import com.bretthalliday.fdtuner.ble.FardriverBleManager
import com.bretthalliday.fdtuner.databinding.ActivityMainBinding
import com.bretthalliday.fdtuner.ui.dashboard.DashboardViewModel
import com.bretthalliday.fdtuner.ui.params.ParamsViewModel
import com.bretthalliday.fdtuner.ui.scan.ScanViewModel
import kotlinx.coroutines.launch

/**
 * Single-activity host. Owns the BLE manager, provides ViewModels via factory.
 * Navigation: ScanFragment → [Dashboard | Params | Settings] (bottom nav).
 */
class MainActivity : AppCompatActivity() {

    lateinit var bleManager: FardriverBleManager
        private set

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bleManager = FardriverBleManager(applicationContext)

        // Restore persisted settings so speed/pole pairs are correct from launch
        val prefs = getSharedPreferences("fd_settings", android.content.Context.MODE_PRIVATE)
        bleManager.useMph = prefs.getBoolean("use_mph", true)
        bleManager.polePairs = prefs.getInt("pole_pairs", 23)
        bleManager.wheelCircumferenceMm = prefs.getInt("wheel_circ_mm", 2100)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up navigation
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        // Hide bottom nav on scan screen
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.scanFragment -> {
                    binding.bottomNavigation.visibility = View.GONE
                    binding.toolbar.subtitle = "Disconnected"
                }
                else -> {
                    binding.bottomNavigation.visibility = View.VISIBLE
                }
            }
        }

        setSupportActionBar(binding.toolbar)

        // Observe connection state for toolbar status + back-to-scan on disconnect
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                bleManager.connectionState.collect { state ->
                    updateToolbar(state)
                    invalidateOptionsMenu()

                    // If we were connected/demo and now disconnected, navigate back to scan
                    if (state is ConnectionState.Disconnected) {
                        val currentDest = navController.currentDestination?.id
                        if (currentDest != null && currentDest != R.id.scanFragment) {
                            navController.navigate(R.id.scanFragment)
                        }
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val showDisconnect = bleManager.connectionState.value.let {
            it is ConnectionState.Connected || it is ConnectionState.Demo
        }
        menu.findItem(R.id.action_disconnect)?.isVisible = showDisconnect
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_disconnect -> {
                if (bleManager.isDemo) bleManager.stopDemo()
                else bleManager.disconnect()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateToolbar(state: ConnectionState) {
        when (state) {
            is ConnectionState.Disconnected -> {
                binding.toolbar.subtitle = "Disconnected"
            }
            is ConnectionState.Scanning -> {
                binding.toolbar.subtitle = "Scanning… (${state.found} found)"
            }
            is ConnectionState.Connecting -> {
                binding.toolbar.subtitle = "Connecting to ${state.deviceName}…"
            }
            is ConnectionState.Connected -> {
                binding.toolbar.subtitle = "● ${state.deviceName}"
            }
            is ConnectionState.Error -> {
                binding.toolbar.subtitle = "Error: ${state.message}"
            }
            is ConnectionState.Demo -> {
                binding.toolbar.subtitle = "⚡ DEMO MODE"
            }
        }
    }

    /**
     * ViewModelFactory provider — fragments use activityViewModels() with the default factory,
     * so we implement the ViewModelStoreOwner pattern by providing the BLE manager
     * through the Application-scoped store.
     *
     * For simplicity, we use a custom factory via the Application.
     */
    override val defaultViewModelProviderFactory: androidx.lifecycle.ViewModelProvider.Factory
        get() = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return when {
                    modelClass.isAssignableFrom(ScanViewModel::class.java) ->
                        ScanViewModel(bleManager) as T
                    modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
                        DashboardViewModel(bleManager) as T
                    modelClass.isAssignableFrom(ParamsViewModel::class.java) ->
                        ParamsViewModel(bleManager) as T
                    else -> super.create(modelClass)
                }
            }
        }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.cleanup()
    }
}
