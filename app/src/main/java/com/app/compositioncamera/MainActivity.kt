package com.app.compositioncamera

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.app.compositioncamera.ui.theme.CompositionCameraTheme

class MainActivity : ComponentActivity() {
    private var backPressedTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            )
        )

        window.isNavigationBarContrastEnforced = false

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())

        setContent {
            CompositionCameraTheme {
                val navController = rememberNavController()

                BackHandler {
                    if (!navController.popBackStack()) {
                        handleBackPress()
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "splash",
                        modifier = Modifier
                    ) {
                        composable("splash") {
                            SplashScreen(onTimeout = {
                                navController.navigate("camera") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            })
                        }

                        composable("camera") {
                            CameraScreen()
                        }
                    }
                }
            }
        }
    }
    private fun handleBackPress() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            finish()
        } else {
            Toast.makeText(this, "'뒤로' 버튼을 한 번 더 누르면 종료됩니다", Toast.LENGTH_SHORT).show()
            backPressedTime = System.currentTimeMillis()
        }
    }
}