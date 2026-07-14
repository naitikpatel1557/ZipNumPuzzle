package com.numberpath.myapplication

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.games.PlayGames
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class MainActivity : AppCompatActivity() {

    // Screens
    private lateinit var screenMainMenu: LinearLayout
    private lateinit var screenDifficulty: LinearLayout
    private lateinit var screenLevelSelect: LinearLayout
    private lateinit var screenGame: LinearLayout
    private lateinit var screenResults: View

    private var backgroundMusic: MediaPlayer? = null
    private var currentMusicVolume = 0.4f
    private var activeDifficultyMode = "EASY"

    // Game Elements
    private lateinit var puzzleBoard: ZipPuzzleView
    private lateinit var tvTimer: TextView
    private lateinit var tvLevel: TextView
    private lateinit var levelGrid: GridLayout

    // Game State
    private var playingLevel = 1
    private var highestUnlockedLevel = 1
    private val totalLevels = 100
    private lateinit var prefs: SharedPreferences

    // Economy State
    private var totalCoins = 0
    private var equippedColor = "#E91E63"

    // --- ADMOB STATE ---
    private var rewardedAd: RewardedAd? = null
    private val adUnitId = "ca-app-pub-1048582027476561/9159199511"

    // Timer State
    private var secondsElapsed = 0
    private var isTimerRunning = false
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isTimerRunning) {
                secondsElapsed++
                updateTimerText()
                timerHandler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("ZipGamePrefs", Context.MODE_PRIVATE)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)

        // --- INITIALIZE GOOGLE SERVICES ---
        MobileAds.initialize(this) {}
        loadRewardedAd()

        // Initialize Play Games Services
        PlayGames.getGamesSignInClient(this).signIn()

        // Bind Views
        screenMainMenu = findViewById(R.id.screenMainMenu)
        screenDifficulty = findViewById(R.id.screenDifficulty)
        screenLevelSelect = findViewById(R.id.screenLevelSelect)
        screenGame = findViewById(R.id.screenGame)
        screenResults = findViewById(R.id.screenResults)
        puzzleBoard = findViewById(R.id.puzzleBoard)
        tvTimer = findViewById(R.id.tvTimer)
        tvLevel = findViewById(R.id.tvLevel)
        levelGrid = findViewById(R.id.levelGrid)

        // Background Music Only
        backgroundMusic = MediaPlayer.create(this, R.raw.bg_music)
        backgroundMusic?.isLooping = true
        backgroundMusic?.setVolume(0.4f, 0.4f)

        // UI Toggles
        val header = findViewById<View>(R.id.layoutHowToPlayHeader)
        val content = findViewById<View>(R.id.layoutHowToPlayContent)
        val arrow = findViewById<View>(R.id.ivArrow)

        header.setOnClickListener {
            if (content.visibility == View.GONE) {
                content.visibility = View.VISIBLE
                arrow.animate().rotation(270f).setDuration(200).start()
            } else {
                content.visibility = View.GONE
                arrow.animate().rotation(90f).setDuration(200).start()
            }
        }

        // Navigation
        findViewById<View>(R.id.btnSettings).setOnClickListener { showSettingsDialog() }
        findViewById<View>(R.id.btnReset).setOnClickListener { puzzleBoard.resetPath() }

        // --- HINT ECONOMY WITH VIDEO ADS ---
        findViewById<Button>(R.id.btnHint).setOnClickListener {
            if (totalCoins >= 25) {
                totalCoins -= 25
                prefs.edit().putInt("TOTAL_COINS", totalCoins).apply()
                findViewById<Button>(R.id.btnShop).text = "🪙 $totalCoins | SHOP"
                puzzleBoard.showHint()
            } else {
                showAdPromptDialog()
            }
        }

        findViewById<Button>(R.id.btnPlay).setOnClickListener {
            screenMainMenu.visibility = View.GONE
            screenDifficulty.visibility = View.VISIBLE
        }
        findViewById<View>(R.id.btnBackFromDifficulty).setOnClickListener {
            screenDifficulty.visibility = View.GONE
            screenMainMenu.visibility = View.VISIBLE
        }

        // Load Economy
        totalCoins = prefs.getInt("TOTAL_COINS", 0)
        equippedColor = prefs.getString("EQUIPPED_COLOR", "#E91E63") ?: "#E91E63"
        findViewById<Button>(R.id.btnShop).text = "🪙 $totalCoins | SHOP"
        puzzleBoard.setPathColor(equippedColor)
        findViewById<Button>(R.id.btnShop).setOnClickListener { showShopDialog() }

        // Difficulty
        findViewById<View>(R.id.btnEasy).setOnClickListener { openLevelSelectForMode("EASY") }
        findViewById<View>(R.id.btnMedium).setOnClickListener { openLevelSelectForMode("MEDIUM") }
        findViewById<View>(R.id.btnHard).setOnClickListener { openLevelSelectForMode("HARD") }

        findViewById<View>(R.id.btnBackFromGame).setOnClickListener { showLevelSelection() }
        findViewById<Button>(R.id.btnBackToMenu).setOnClickListener { showMainMenu() }

        puzzleBoard.onLevelCleared = { handleLevelWin() }
    }

    // --- ADMOB LOGIC ---
    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(this, adUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                rewardedAd = null
            }
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
            }
        })
    }

    private fun showAdPromptDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Out of Coins!")
            .setMessage("You need 25 coins for a hint. Watch a short video ad to get a free hint instead?")
            .setPositiveButton("Watch Ad") { dialog, _ ->
                showRewardedAd()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRewardedAd() {
        if (rewardedAd != null) {
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    loadRewardedAd()
                    backgroundMusic?.start()
                }
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedAd = null
                }
            }

            backgroundMusic?.pause()

            rewardedAd?.show(this) { _ ->
                puzzleBoard.showHint()
                Toast.makeText(this, "Hint Unlocked!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Ad is still loading. Try again in a few seconds.", Toast.LENGTH_SHORT).show()
            loadRewardedAd()
        }
    }

    // --- SCREEN NAVIGATION ---
    private fun showMainMenu() {
        screenMainMenu.visibility = View.VISIBLE
        screenDifficulty.visibility = View.GONE
        screenLevelSelect.visibility = View.GONE
        screenGame.visibility = View.GONE
        pauseTimer()
    }

    private fun openLevelSelectForMode(mode: String) {
        activeDifficultyMode = mode
        highestUnlockedLevel = prefs.getInt("HIGHEST_LEVEL_$activeDifficultyMode", 1)
        screenDifficulty.visibility = View.GONE
        screenLevelSelect.visibility = View.VISIBLE
        refreshLevelGrid()
    }

    private fun showLevelSelection() {
        screenMainMenu.visibility = View.GONE
        screenDifficulty.visibility = View.GONE
        screenLevelSelect.visibility = View.VISIBLE
        screenGame.visibility = View.GONE
        refreshLevelGrid()
    }

    private fun startGameForLevel(level: Int) {
        playingLevel = level
        screenMainMenu.visibility = View.GONE
        screenDifficulty.visibility = View.GONE
        screenLevelSelect.visibility = View.GONE
        screenGame.visibility = View.VISIBLE

        tvLevel.text = "Level $playingLevel"
        puzzleBoard.startNewLevel(activeDifficultyMode)
        startTimer()
    }

    // --- LEVEL SELECT LOGIC ---
    private fun buildLevelGrid() {
        levelGrid.removeAllViews()

        for (i in 1..totalLevels) {
            val cellContainer = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply { setMargins(16, 16, 16, 16) }
            }

            val timeText = android.widget.TextView(this).apply {
                val savedTime = prefs.getInt("LEVEL_${i}_TIME_$activeDifficultyMode", -1)
                if (savedTime != -1) {
                    val m = savedTime / 60
                    val s = savedTime % 60
                    text = String.format("⏱ %d:%02d", m, s)
                    setTextColor(android.graphics.Color.DKGRAY)
                } else {
                    text = " "
                }
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 8)
                gravity = Gravity.CENTER
            }

            val btn = com.google.android.material.button.MaterialButton(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(220, 220)
                cornerRadius = 32
                insetTop = 0
                insetBottom = 0

                if (i <= highestUnlockedLevel) {
                    text = i.toString()
                    textSize = 28f
                    isEnabled = true
                    backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E91E63"))
                    setTextColor(android.graphics.Color.WHITE)
                    setOnClickListener { startGameForLevel(i) }
                } else {
                    text = "🔒"
                    textSize = 24f
                    isEnabled = false
                    backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E0E0E0"))
                    setTextColor(android.graphics.Color.DKGRAY)
                }
            }
            cellContainer.addView(timeText)
            cellContainer.addView(btn)
            levelGrid.addView(cellContainer)
        }
    }

    private fun refreshLevelGrid() {
        if (levelGrid.childCount == 0) {
            buildLevelGrid()
            return
        }

        for (i in 0 until levelGrid.childCount) {
            val levelNumber = i + 1
            val cellContainer = levelGrid.getChildAt(i) as android.widget.LinearLayout
            val timeText = cellContainer.getChildAt(0) as android.widget.TextView
            val btn = cellContainer.getChildAt(1) as com.google.android.material.button.MaterialButton

            val savedTime = prefs.getInt("LEVEL_${levelNumber}_TIME_$activeDifficultyMode", -1)
            if (savedTime != -1) {
                val m = savedTime / 60
                val s = savedTime % 60
                timeText.text = String.format("⏱ %d:%02d", m, s)
            } else {
                timeText.text = " "
            }

            if (levelNumber <= highestUnlockedLevel) {
                btn.text = levelNumber.toString()
                btn.isEnabled = true
                btn.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E91E63"))
                btn.setTextColor(android.graphics.Color.WHITE)
            } else {
                btn.text = "🔒"
                btn.isEnabled = false
                btn.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E0E0E0"))
                btn.setTextColor(android.graphics.Color.DKGRAY)
            }
        }
    }

    // --- TIMER LOGIC ---
    private fun startTimer() {
        secondsElapsed = 0
        isTimerRunning = true
        updateTimerText()
        timerHandler.postDelayed(timerRunnable, 1000)
    }

    private fun pauseTimer() {
        isTimerRunning = false
        timerHandler.removeCallbacks(timerRunnable)
    }

    private fun updateTimerText() {
        val minutes = secondsElapsed / 60
        val seconds = secondsElapsed % 60
        tvTimer.text = String.format("⏱ %d:%02d", minutes, seconds)
    }

    // --- WIN LOGIC ---
    private fun handleLevelWin() {
        pauseTimer()

        if (playingLevel == highestUnlockedLevel) {
            highestUnlockedLevel++
            prefs.edit().putInt("HIGHEST_LEVEL_$activeDifficultyMode", highestUnlockedLevel).apply()
        }

        val timeKey = "LEVEL_${playingLevel}_TIME_$activeDifficultyMode"
        var previousBestTime = prefs.getInt(timeKey, Int.MAX_VALUE)

        if (secondsElapsed < previousBestTime) {
            prefs.edit().putInt(timeKey, secondsElapsed).apply()
            previousBestTime = secondsElapsed
        }

        val today = LocalDate.now()
        val lastPlayedStr = prefs.getString("LAST_PLAYED_DATE", "")
        var currentStreak = prefs.getInt("CURRENT_STREAK", 0)
        var maxStreak = prefs.getInt("MAX_STREAK", 0)
        var totalPlays = prefs.getInt("TOTAL_PLAYS", 0)

        totalPlays++
        var coinsEarnedThisLevel = 10

        if (lastPlayedStr.isNullOrEmpty()) {
            currentStreak = 1
        } else {
            val lastPlayed = LocalDate.parse(lastPlayedStr)
            val daysBetween = ChronoUnit.DAYS.between(lastPlayed, today)

            if (daysBetween == 1L) {
                currentStreak++
                coinsEarnedThisLevel += 50
            } else if (daysBetween > 1L) {
                currentStreak = 1
            }
        }

        if (currentStreak > maxStreak) maxStreak = currentStreak
        totalCoins += coinsEarnedThisLevel

        prefs.edit()
            .putString("LAST_PLAYED_DATE", today.toString())
            .putInt("CURRENT_STREAK", currentStreak)
            .putInt("MAX_STREAK", maxStreak)
            .putInt("TOTAL_PLAYS", totalPlays)
            .putInt("TOTAL_COINS", totalCoins)
            .apply()

        // --- PUSH TO GLOBAL GOOGLE LEADERBOARD ---
        try {
            PlayGames.getLeaderboardsClient(this)
                .submitScore("CgkI38_WhZIEEAIQAA", maxStreak.toLong())
        } catch (e: Exception) {
            Log.e("ZipPuzzle", "Play Games not logged in or configured yet.")
        }

        refreshLevelGrid()

        screenGame.visibility = View.GONE
        screenResults.visibility = View.VISIBLE

        val tvFinalTime = findViewById<TextView>(R.id.tvFinalTime)
        val tvZipLevelTitle = findViewById<TextView>(R.id.tvZipLevelTitle)
        val tvPlays = findViewById<TextView>(R.id.tvPlays)
        val tvBestScore = findViewById<TextView>(R.id.tvBestScore)
        val tvMaxStreak = findViewById<TextView>(R.id.tvMaxStreak)
        val tvCurrentStreakTitle = findViewById<TextView>(R.id.tvCurrentStreakTitle)
        val btnNextLevel = findViewById<View>(R.id.btnNextLevelFromResults)
        val btnClose = findViewById<View>(R.id.btnCloseResult)

        tvFinalTime.text = "Solved in ${tvTimer.text.substring(2)}"
        tvZipLevelTitle.text = "$activeDifficultyMode #$playingLevel"
        tvPlays.text = totalPlays.toString()

        val m = previousBestTime / 60
        val s = previousBestTime % 60
        tvBestScore.text = String.format("%d:%02d", m, s)

        tvMaxStreak.text = maxStreak.toString()
        tvCurrentStreakTitle.text = "$currentStreak-day win streak 🔥"

        val todayIndex = if (today.dayOfWeek.value == 7) 0 else today.dayOfWeek.value
        val dayCards = listOf(R.id.cardDay1, R.id.cardDay2, R.id.cardDay3, R.id.cardDay4, R.id.cardDay5, R.id.cardDay6, R.id.cardDay7)
        val dayChecks = listOf(R.id.tvDayCheck1, R.id.tvDayCheck2, R.id.tvDayCheck3, R.id.tvDayCheck4, R.id.tvDayCheck5, R.id.tvDayCheck6, R.id.tvDayCheck7)

        for (i in 0..6) {
            val card = findViewById<com.google.android.material.card.MaterialCardView>(dayCards[i])
            val check = findViewById<TextView>(dayChecks[i])
            val daysAgo = (todayIndex - i + 7) % 7

            if (daysAgo < currentStreak && daysAgo < 7) {
                card.setCardBackgroundColor(android.graphics.Color.parseColor("#FFB300"))
                card.strokeWidth = 0
                check.visibility = View.VISIBLE
            } else {
                card.setCardBackgroundColor(android.graphics.Color.WHITE)
                card.strokeWidth = 3
                card.strokeColor = android.graphics.Color.parseColor("#E0E0E0")
                check.visibility = View.GONE
            }
        }

        // --- EXTENDED 1000-DAY ACHIEVEMENTS LOGIC ---
        val achievementTiers = mapOf(
            R.id.achieve3 to 3,
            R.id.achieve5 to 5,
            R.id.achieve7 to 7,
            R.id.achieve31 to 31,
            R.id.achieve50 to 50,
            R.id.achieve100 to 100,
            R.id.achieve150 to 150,
            R.id.achieve200 to 200,
            R.id.achieve250 to 250,
            R.id.achieve300 to 300,
            R.id.achieve365 to 365,
            R.id.achieve500 to 500,
            R.id.achieve1000 to 1000
        )

        for ((viewId, requiredStreak) in achievementTiers) {
            val achieveView = findViewById<View>(viewId)
            if (achieveView != null) {
                achieveView.alpha = if (maxStreak >= requiredStreak) 1.0f else 0.3f
            }
        }

        findViewById<Button>(R.id.btnShop).text = "🪙 $totalCoins | SHOP"

        // Visual Confetti Burst (No SFX)
        val confetti = findViewById<ConfettiView>(R.id.confettiView)
        confetti?.burst()

        btnNextLevel.setOnClickListener {
            screenResults.visibility = View.GONE
            startGameForLevel(playingLevel + 1)
        }

        btnClose.setOnClickListener {
            screenResults.visibility = View.GONE
            showLevelSelection()
        }
    }

    private fun showShopDialog() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(64, 32, 64, 32)
        }

        val titleText = android.widget.TextView(this).apply {
            text = "Your Wallet: $totalCoins 🪙"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 32)
        }
        layout.addView(titleText)

        // Make sure there are no accidental spaces in your hex codes!
        val items = listOf(
            Triple("Classic Pink", "#E91E63", 0),
            Triple("Neon Green", "#00E676", 150),
            Triple("Electric Blue", "#00E5FF", 300),
            Triple("Sunset (Gradient)", "#FF512F,#DD2476", 500),
            Triple("Oceanic (Gradient)", "#2193b0,#6dd5ed", 500),
            Triple("Cyberpunk (Gradient)", "#8A2387,#E94057,#F27121", 800)
        )

        var dialog: androidx.appcompat.app.AlertDialog? = null

        for (item in items) {
            val (name, hex, cost) = item
            val isOwned = cost == 0 || prefs.getBoolean("OWNED_$hex", false)
            val isEquipped = equippedColor == hex

            val itemButton = com.google.android.material.button.MaterialButton(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 16) }

                text = when {
                    isEquipped -> "$name (EQUIPPED)"
                    isOwned -> "$name (OWNED)"
                    else -> "$name - $cost 🪙"
                }

                if (hex.contains(",")) {
                    val colorInts = hex.split(",").map { android.graphics.Color.parseColor(it.trim()) }.toIntArray()
                    val gd = android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, colorInts
                    )
                    gd.cornerRadius = 60f
                    background = gd
                    backgroundTintList = null
                    setTextColor(android.graphics.Color.WHITE)
                } else {
                    backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(hex.trim()))
                    setTextColor(if (hex.trim() == "#00E676" || hex.trim() == "#00E5FF") android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }

                setOnClickListener {
                    if (isOwned) {
                        equippedColor = hex
                        prefs.edit().putString("EQUIPPED_COLOR", hex).apply()
                        puzzleBoard.setPathColor(hex)
                        dialog?.dismiss()
                    } else if (totalCoins >= cost) {
                        totalCoins -= cost
                        equippedColor = hex
                        prefs.edit()
                            .putInt("TOTAL_COINS", totalCoins)
                            .putBoolean("OWNED_$hex", true)
                            .putString("EQUIPPED_COLOR", hex)
                            .apply()

                        // THE FIX: Properly scoping the UI update to the Activity!
                        this@MainActivity.findViewById<Button>(R.id.btnShop).text = "🪙 $totalCoins | SHOP"

                        puzzleBoard.setPathColor(hex)
                        dialog?.dismiss()
                    } else {
                        Toast.makeText(this@MainActivity, "Need more coins!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            layout.addView(itemButton)
        }

        dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Customization Shop")
            .setView(layout)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showSettingsDialog() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(64, 64, 64, 32)
        }

        var settingsDialog: androidx.appcompat.app.AlertDialog? = null

        val volumeTitle = android.widget.TextView(this).apply {
            text = "Music Volume"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        val seekBar = android.widget.SeekBar(this).apply {
            max = 100
            progress = (currentMusicVolume * 100).toInt()
            setPadding(0, 0, 0, 48)
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    currentMusicVolume = progress / 100f
                    backgroundMusic?.setVolume(currentMusicVolume, currentMusicVolume)
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })
        }

        val btnAbout = com.google.android.material.button.MaterialButton(this).apply {
            text = "About Us"
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            strokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E91E63"))
            strokeWidth = 3
            elevation = 0f
            setTextColor(android.graphics.Color.parseColor("#E91E63"))
            setOnClickListener { showAboutDialog() }
        }

        val btnPrivacy = com.google.android.material.button.MaterialButton(this).apply {
            text = "Privacy Policy"
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            elevation = 0f
            strokeWidth = 0
            setTextColor(android.graphics.Color.parseColor("#E91E63"))
            setOnClickListener { showPrivacyDialog() }
        }

        layout.addView(volumeTitle)
        layout.addView(seekBar)
        layout.addView(btnAbout)
        layout.addView(btnPrivacy)

        settingsDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Settings")
            .setView(layout)
            .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showAboutDialog() {
        val bioText = """
            Zip Puzzle App
            Version 1.0.0
            
            Developed by:
            TECHNICK-TN
           
            Specializing in everything from sleek UI design and Android development to complex Machine Learning and IoT integrations, this game was built to challenge your brain with modern, satisfying puzzle mechanics.
        """.trimIndent()

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("About Us")
            .setMessage(bioText)
            .setPositiveButton("Awesome") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showPrivacyDialog() {
        val privacyText = """
            Privacy Policy
            
            This puzzle game respects your privacy. We do not collect, store, or share any personal user data or track your location. 
            
            All game progress, level unlocks, and best times are stored entirely locally on your device using standard SharedPreferences. If you clear your app data or uninstall the app, your progress will be permanently erased.
        """.trimIndent()

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Privacy Policy")
            .setMessage(privacyText)
            .setPositiveButton("I Understand") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onResume() {
        super.onResume()
        backgroundMusic?.start()
    }

    override fun onPause() {
        super.onPause()
        if (backgroundMusic?.isPlaying == true) {
            backgroundMusic?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pauseTimer()
        backgroundMusic?.release()
        backgroundMusic = null
    }
}