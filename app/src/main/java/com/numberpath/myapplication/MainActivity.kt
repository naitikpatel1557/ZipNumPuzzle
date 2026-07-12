package com.numberpath.myapplication

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.media.MediaPlayer
import android.view.Gravity
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

    // --- ECONOMY STATE ---
    private var totalCoins = 0
    private var equippedColor = "#E91E63" // Default Pink

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

        val savedTheme = prefs.getInt("THEME_MODE", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)

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

        backgroundMusic = MediaPlayer.create(this, R.raw.bg_music)
        backgroundMusic?.isLooping = true
        backgroundMusic?.setVolume(0.4f, 0.4f)

        // --- HOW TO PLAY TOGGLE LOGIC ---
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

        // --- NAVIGATION LISTENERS ---
        findViewById<View>(R.id.btnSettings).setOnClickListener { showSettingsDialog() }
        findViewById<View>(R.id.btnReset).setOnClickListener { puzzleBoard.resetPath() }

        // --- HINT ECONOMY ---
        findViewById<Button>(R.id.btnHint).setOnClickListener {
            if (totalCoins >= 25) {
                // Deduct coins and save
                totalCoins -= 25
                prefs.edit().putInt("TOTAL_COINS", totalCoins).apply()

                // Update UI and trigger hint
                findViewById<Button>(R.id.btnShop).text = "🪙 $totalCoins | SHOP"
                puzzleBoard.showHint()
            } else {
                // Not enough money!
                android.widget.Toast.makeText(this, "Not enough coins! Win levels to earn more.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // Main Menu PLAY button -> Shows Difficulty Screen
        findViewById<Button>(R.id.btnPlay).setOnClickListener {
            screenMainMenu.visibility = View.GONE
            screenDifficulty.visibility = View.VISIBLE
        }

        // Back button on Difficulty Screen -> Goes to Main Menu
        findViewById<View>(R.id.btnBackFromDifficulty).setOnClickListener {
            screenDifficulty.visibility = View.GONE
            screenMainMenu.visibility = View.VISIBLE
        }

        // --- LOAD ECONOMY ---
        totalCoins = prefs.getInt("TOTAL_COINS", 0)
        equippedColor = prefs.getString("EQUIPPED_COLOR", "#E91E63") ?: "#E91E63"

        // Ensure btnShop exists in activity_main.xml inside screenMainMenu!
        findViewById<Button>(R.id.btnShop).text = "🪙 $totalCoins | SHOP"
        puzzleBoard.setPathColor(equippedColor)

        findViewById<Button>(R.id.btnShop).setOnClickListener {
            showShopDialog()
        }

        // Difficulty Selection Buttons
        findViewById<View>(R.id.btnEasy).setOnClickListener { openLevelSelectForMode("EASY") }
        findViewById<View>(R.id.btnMedium).setOnClickListener { openLevelSelectForMode("MEDIUM") }
        findViewById<View>(R.id.btnHard).setOnClickListener { openLevelSelectForMode("HARD") }

        findViewById<View>(R.id.btnBackFromGame).setOnClickListener { showLevelSelection() }
        findViewById<Button>(R.id.btnBackToMenu).setOnClickListener { showMainMenu() }

        puzzleBoard.onLevelCleared = { handleLevelWin() }
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

        var coinsEarnedThisLevel = 10 // Base win reward

        if (lastPlayedStr.isNullOrEmpty()) {
            currentStreak = 1
        } else {
            val lastPlayed = LocalDate.parse(lastPlayedStr)
            val daysBetween = ChronoUnit.DAYS.between(lastPlayed, today)

            if (daysBetween == 1L) {
                currentStreak++
                coinsEarnedThisLevel += 50 // Streak bonus!
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

        // --- 7-DAY CALENDAR UI ---
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

        // --- ACHIEVEMENTS LOGIC ---
        findViewById<View>(R.id.layoutAchieve3).alpha = if (maxStreak >= 3) 1.0f else 0.3f
        findViewById<View>(R.id.layoutAchieve5).alpha = if (maxStreak >= 5) 1.0f else 0.3f
        findViewById<View>(R.id.layoutAchieve7).alpha = if (maxStreak >= 7) 1.0f else 0.3f
        findViewById<View>(R.id.layoutAchieve31).alpha = if (maxStreak >= 31) 1.0f else 0.3f

        // Update Shop UI
        findViewById<Button>(R.id.btnShop).text = "🪙 $totalCoins | SHOP"

        // --- CONFETTI BURST ---
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

        val items = listOf(
            Triple("Classic Pink", "#E91E63", 0),
            Triple("Neon Green", "#00E676", 150),
            Triple("Electric Blue", "#00E5FF", 300)
        )

        var dialog: androidx.appcompat.app.AlertDialog? = null

        for (item in items) {
            val (name, hex, cost) = item
            val isOwned = cost == 0 || prefs.getBoolean("OWNED_$hex", false)
            val isEquipped = equippedColor == hex

            val itemButton = com.google.android.material.button.MaterialButton(this).apply {
                text = when {
                    isEquipped -> "$name (EQUIPPED)"
                    isOwned -> "$name (OWNED)"
                    else -> "$name - $cost 🪙"
                }

                backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(hex))
                setTextColor(if (hex == "#00E676" || hex == "#00E5FF") android.graphics.Color.BLACK else android.graphics.Color.WHITE)

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

                        findViewById<Button>(R.id.btnShop).text = "🪙 $totalCoins | SHOP"
                        puzzleBoard.setPathColor(hex)
                        dialog?.dismiss()
                    } else {
                        android.widget.Toast.makeText(context, "Need more coins!", android.widget.Toast.LENGTH_SHORT).show()
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
            
            TECHNICK-TN is a passionate full-stack developer, tech creator, and MCA student at Charusat University, based in Morbi, Gujarat. 
            
            Specializing in everything from sleek UI design and Flutter development to complex Machine Learning and IoT integrations, this game was built to challenge your brain with modern, satisfying puzzle mechanics.
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