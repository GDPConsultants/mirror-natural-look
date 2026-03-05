package com.mirrornaturallook

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.animation.AlphaAnimation
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mirrornaturallook.databinding.ActivityMirrorBinding
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.min

class MirrorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMirrorBinding
    private lateinit var premiumManager: PremiumManager
    private lateinit var adManager: AdManager
    private lateinit var billingManager: BillingManager

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    private var currentZoom = 1.0f
    private val zoomLevels = listOf(1.0f, 1.5f, 2.0f, 2.5f, 3.0f)

    private var isPinching = false
    private var lastPinchDist = 0f

    private val uiHandler = Handler(Looper.getMainLooper())
    private var uiVisible = true
    private val hideUiRunnable = Runnable { hideUI() }
    private val UI_HIDE_MS = 4000L

    private var currentTheme = "dark"
    private var sessionCount = 0

    companion object {
        private const val CAM_PERM = Manifest.permission.CAMERA
        private const val RC_PAYWALL = 1001
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startCamera() else showCameraBlocked() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMirrorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        premiumManager = PremiumManager(this)
        adManager = AdManager(this)
        setupBilling()
        setupFullscreen()
        setupGestures()
        setupButtons()
        applyTheme()
        checkCamera()
        refreshPremiumUI()
        if (premiumManager.needsPaywall) openPaywall()

        // Show interstitial every 3 sessions
        sessionCount++
        if (premiumManager.showAds && sessionCount % 3 == 0) {
            Handler(Looper.getMainLooper()).postDelayed({ adManager.showInterstitial(this) }, 2000)
        }
    }

    private fun setupFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideUI()
    }

    private fun hideUI() {
        WindowInsetsControllerCompat(window, binding.root).hide(WindowInsetsCompat.Type.systemBars())
        uiVisible = false
        binding.controlsOverlay.startAnimation(AlphaAnimation(1f,0f).apply { duration=600; fillAfter=true })
        Handler(Looper.getMainLooper()).postDelayed({ binding.controlsOverlay.visibility = View.INVISIBLE }, 600)
    }

    private fun showUI() {
        WindowInsetsControllerCompat(window, binding.root).show(WindowInsetsCompat.Type.systemBars())
        uiVisible = true
        binding.controlsOverlay.visibility = View.VISIBLE
        binding.controlsOverlay.startAnimation(AlphaAnimation(0f,1f).apply { duration=300; fillAfter=true })
        uiHandler.removeCallbacks(hideUiRunnable)
        uiHandler.postDelayed(hideUiRunnable, UI_HIDE_MS)
    }

    private fun checkCamera() {
        if (ContextCompat.checkSelfPermission(this, CAM_PERM) == PackageManager.PERMISSION_GRANTED)
            startCamera()
        else permLauncher.launch(CAM_PERM)
    }

    private fun startCamera() {
        binding.cameraBlockedLayout.visibility = View.GONE
        ProcessCameraProvider.getInstance(this).addListener({
            cameraProvider = it.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            binding.viewFinder.scaleX = -1f // mirror flip
            try {
                cameraProvider!!.unbindAll()
                camera = cameraProvider!!.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview)
                updateZoomUI()
            } catch (e: Exception) { showCameraBlocked() }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showCameraBlocked() { binding.cameraBlockedLayout.visibility = View.VISIBLE }

    private fun setZoom(ratio: Float) {
        currentZoom = ratio
        camera?.cameraControl?.setZoomRatio(ratio)
        binding.zoomBadge.visibility = if (ratio > 1.01f) View.VISIBLE else View.GONE
        binding.zoomBadge.text = String.format("%.1f×", ratio)
        updateZoomUI()
    }

    private fun updateZoomUI() {
        listOf(binding.zoom1x, binding.zoom15x, binding.zoom2x, binding.zoom25x, binding.zoom3x)
            .forEachIndexed { i, btn ->
                val on = abs(zoomLevels[i] - currentZoom) < 0.12f
                btn.alpha = if (on) 1.0f else 0.55f
                btn.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    if (on) 0xFFC0A062.toInt() else 0x33FFFFFF.toInt()
                )
            }
    }

    private fun setupGestures() {
        binding.mirrorContainer.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> if (!uiVisible) showUI() else {
                    uiHandler.removeCallbacks(hideUiRunnable)
                    uiHandler.postDelayed(hideUiRunnable, UI_HIDE_MS)
                }
                MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount == 2) {
                    isPinching = true; lastPinchDist = pinchDist(e)
                }
                MotionEvent.ACTION_MOVE -> if (isPinching && e.pointerCount == 2) {
                    val d = pinchDist(e)
                    if (lastPinchDist > 0) {
                        val max = camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 3f
                        setZoom((currentZoom * (d / lastPinchDist)).coerceIn(1f, min(3f, max)))
                    }
                    lastPinchDist = d
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                    if (e.pointerCount <= 1) { isPinching = false; lastPinchDist = 0f }
            }
            true
        }
    }

    private fun pinchDist(e: MotionEvent) =
        Math.hypot((e.getX(0)-e.getX(1)).toDouble(), (e.getY(0)-e.getY(1)).toDouble()).toFloat()

    private fun setupButtons() {
        binding.zoom1x.setOnClickListener  { setZoom(1.0f) }
        binding.zoom15x.setOnClickListener { setZoom(1.5f) }
        binding.zoom2x.setOnClickListener  { setZoom(2.0f) }
        binding.zoom25x.setOnClickListener { setZoom(2.5f) }
        binding.zoom3x.setOnClickListener  { setZoom(3.0f) }

        binding.btnTheme.setOnClickListener {
            currentTheme = if (currentTheme == "dark") "silver" else "dark"
            applyTheme()
        }
        binding.btnUpgrade.setOnClickListener { openPaywall() }
        binding.btnAllowCamera.setOnClickListener { checkCamera() }

        binding.btnSilverTint.setOnClickListener {
            binding.mirrorOverlay.silverTintEnabled = !binding.mirrorOverlay.silverTintEnabled
            binding.btnSilverTint.alpha = if (binding.mirrorOverlay.silverTintEnabled) 1f else 0.45f
        }
        binding.btnEdgeGlow.setOnClickListener {
            binding.mirrorOverlay.edgeGlowEnabled = !binding.mirrorOverlay.edgeGlowEnabled
            binding.btnEdgeGlow.alpha = if (binding.mirrorOverlay.edgeGlowEnabled) 1f else 0.45f
        }
        binding.btnFrostedBorder.setOnClickListener {
            binding.mirrorOverlay.frostedBorderEnabled = !binding.mirrorOverlay.frostedBorderEnabled
            binding.btnFrostedBorder.alpha = if (binding.mirrorOverlay.frostedBorderEnabled) 1f else 0.45f
        }
        binding.btnWatchAd.setOnClickListener {
            adManager.showRewarded(this, onRewarded = {
                Toast.makeText(this, "Ads off for 1 hour ✓", Toast.LENGTH_SHORT).show()
                binding.adBannerContainer.visibility = View.GONE
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!premiumManager.isPremium) binding.adBannerContainer.visibility = View.VISIBLE
                }, 3_600_000L)
            })
        }
    }

    private fun applyTheme() {
        val dark = currentTheme == "dark"
        binding.mirrorOverlay.isDarkTheme = dark
        binding.mirrorBackground.setBackgroundResource(
            if (dark) R.drawable.bg_mirror_dark else R.drawable.bg_mirror_silver
        )
        binding.btnTheme.text = if (dark) "☽  Dark" else "✦  Silver"
    }

    private fun refreshPremiumUI() {
        if (premiumManager.isPremium) {
            binding.btnUpgrade.visibility = View.GONE
            binding.adBannerContainer.visibility = View.GONE
            binding.btnWatchAd.visibility = View.GONE
        } else {
            binding.btnUpgrade.text = if (premiumManager.trialDaysLeft > 0)
                "${premiumManager.trialDaysLeft}d free · \$5" else "Get Premium"
            adManager.loadBanner(binding.adBannerContainer)
            adManager.loadRewarded()
            adManager.loadInterstitial()
            binding.btnWatchAd.visibility = View.VISIBLE
        }
    }

    private fun setupBilling() {
        billingManager = BillingManager(this, premiumManager,
            onPurchaseSuccess = { refreshPremiumUI(); Toast.makeText(this,"Welcome to Premium! ✓",Toast.LENGTH_LONG).show() },
            onPurchaseFailed  = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
        )
        billingManager.init()
    }

    private fun openPaywall() = startActivityForResult(Intent(this, PaywallActivity::class.java), RC_PAYWALL)

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_PAYWALL) refreshPremiumUI()
    }

    override fun onResume()  { super.onResume();  setupFullscreen(); adManager.resumeBanner(); refreshPremiumUI(); if (premiumManager.needsPaywall) openPaywall() }
    override fun onPause()   { super.onPause();   adManager.pauseBanner() }
    override fun onDestroy() { super.onDestroy(); adManager.destroyBanner(); billingManager.destroy(); uiHandler.removeCallbacks(hideUiRunnable) }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideUI() }
}
