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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.media.MediaPlayer
import android.widget.SeekBar
import android.view.Gravity
import java.time.LocalDate
import java.time.temporal.ChronoUnit


class MainActivity : AppCompatActivity() {

    // Screens
    private lateinit var screenMainMenu: LinearLayout
    private lateinit var screenLevelSelect: LinearLayout
    private lateinit var screenGame: LinearLayout
    private lateinit var screenResults: View
    private var backgroundMusic: MediaPlayer? = null


    // Add this near your MediaPlayer declaration
    private var currentMusicVolume = 0.4f // Default to 40% volume

    // Game Elements
    private lateinit var puzzleBoard: ZipPuzzleView // Make sure to import your custom view!
    private lateinit var tvTimer: TextView
    private lateinit var tvLevel: TextView
    private lateinit var levelGrid: GridLayout

    // Game State
    private var playingLevel = 1
    private var highestUnlockedLevel = 1
    private val totalLevels = 1000 // Change this to however many levels you want
    private lateinit var prefs: SharedPreferences




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
        //splash screen
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



        // Initialize SharedPreferences to save progress
        prefs = getSharedPreferences("ZipGamePrefs", Context.MODE_PRIVATE)
        highestUnlockedLevel = prefs.getInt("HIGHEST_LEVEL", 1)

        // Load the saved theme instantly on startup
        val savedTheme = prefs.getInt("THEME_MODE", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)


        // Bind Views
        screenMainMenu = findViewById(R.id.screenMainMenu)
        screenLevelSelect = findViewById(R.id.screenLevelSelect)
        screenGame = findViewById(R.id.screenGame)
        screenResults = findViewById(R.id.screenResults)
        puzzleBoard = findViewById(R.id.puzzleBoard)
        tvTimer = findViewById(R.id.tvTimer)
        tvLevel = findViewById(R.id.tvLevel)
        levelGrid = findViewById(R.id.levelGrid)



        // 2. Initialize the MediaPlayer
        // Replace 'bg_music' with whatever you named your file in the raw folder
        backgroundMusic = MediaPlayer.create(this, R.raw.bg_music)
        backgroundMusic?.isLooping = true

        // Optional: Lower the volume so it doesn't overpower your haptic vibrations
        backgroundMusic?.setVolume(0.4f, 0.4f)


        // --- HOW TO PLAY TOGGLE LOGIC ---
        val header = findViewById<View>(R.id.layoutHowToPlayHeader)
        val content = findViewById<View>(R.id.layoutHowToPlayContent)
        val arrow = findViewById<View>(R.id.ivArrow)

        header.setOnClickListener {
            if (content.visibility == View.GONE) {
                // Expand the card
                content.visibility = View.VISIBLE
                arrow.animate().rotation(270f).setDuration(200).start() // Point arrow up
            } else {
                // Collapse the card
                content.visibility = View.GONE
                arrow.animate().rotation(90f).setDuration(200).start() // Point arrow down
            }
        }


        // Settings Button Listener
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            showSettingsDialog()
        }

        // Reset Button Listener
        findViewById<View>(R.id.btnReset).setOnClickListener {
            puzzleBoard.resetPath()
        }

        // Listen for the Back button click on the game screen
        findViewById<View>(R.id.btnBackFromGame).setOnClickListener {
            showLevelSelection()
        }

        // Set up Click Listeners for Menu
        findViewById<Button>(R.id.btnPlay).setOnClickListener {
            showLevelSelection()
        }

        findViewById<Button>(R.id.btnBackToMenu).setOnClickListener {
            showMainMenu()
        }

        findViewById<Button>(R.id.btnHint).setOnClickListener {
            puzzleBoard.showHint()
        }

        // Listen for Win Condition
        puzzleBoard.onLevelCleared = {
            handleLevelWin()
        }

        // Build the level grid dynamically
        buildLevelGrid()
    }

    // --- SCREEN NAVIGATION ---


    private fun showMainMenu() {
        screenMainMenu.visibility = View.VISIBLE
        screenLevelSelect.visibility = View.GONE
        screenGame.visibility = View.GONE
        pauseTimer()
    }

    private fun showLevelSelection() {
        screenMainMenu.visibility = View.GONE
        screenLevelSelect.visibility = View.VISIBLE
        screenGame.visibility = View.GONE

        // Rebuild grid in case they just unlocked a new level
        buildLevelGrid()
    }

    private fun startGameForLevel(level: Int) {
        playingLevel = level
        screenMainMenu.visibility = View.GONE
        screenLevelSelect.visibility = View.GONE
        screenGame.visibility = View.VISIBLE

        tvLevel.text = "Level $playingLevel"

        puzzleBoard.startNewLevel() // Trigger your custom view to build a board
        startTimer()
    }

    // --- LEVEL SELECT LOGIC ---
    private fun buildLevelGrid() {
        levelGrid.removeAllViews()

        for (i in 1..totalLevels) {

            // 1. Create a container for the Text and the Button
            val cellContainer = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    setMargins(16, 16, 16, 16)
                }
            }

            // 2. Create the Timer Text (above the button)
            val timeText = android.widget.TextView(this).apply {
                // Check if they have a saved time for this specific level
                val savedTime = prefs.getInt("LEVEL_${i}_TIME", -1)

                if (savedTime != -1) {
                    // Format the saved seconds into M:SS
                    val m = savedTime / 60
                    val s = savedTime % 60
                    text = String.format("⏱ %d:%02d", m, s)
                    setTextColor(android.graphics.Color.DKGRAY)
                } else {
                    text = " " // Leave a blank space so the grid stays perfectly aligned
                }

                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 8) // Small gap between text and button
                gravity = Gravity.CENTER
            }

            // 3. Create your existing Material Button
            val btn = com.google.android.material.button.MaterialButton(this).apply {
                // Notice the size is now applied to the button's standard layout params
                layoutParams = android.widget.LinearLayout.LayoutParams(220, 220)

                cornerRadius = 32
                insetTop = 0
                insetBottom = 0

                if (i <= highestUnlockedLevel) {
                    // UNLOCKED
                    text = i.toString()
                    textSize = 28f
                    isEnabled = true
                    backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E91E63"))
                    setTextColor(android.graphics.Color.WHITE)
                    setOnClickListener {
                        startGameForLevel(i)
                    }
                } else {
                    // LOCKED
                    text = "🔒"
                    textSize = 24f
                    isEnabled = false
                    backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E0E0E0"))
                    setTextColor(android.graphics.Color.DKGRAY)
                }
            }

            // 4. Add the Text and Button to the container, then add the container to the Grid!
            cellContainer.addView(timeText)
            cellContainer.addView(btn)
            levelGrid.addView(cellContainer)
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
// --- WIN LOGIC ---
    private fun handleLevelWin() {
        pauseTimer()

        // 1. Progress Unlocks
        if (playingLevel == highestUnlockedLevel) {
            highestUnlockedLevel++
            prefs.edit().putInt("HIGHEST_LEVEL", highestUnlockedLevel).apply()
        }

        // 2. Best Time Logic
        val timeKey = "LEVEL_${playingLevel}_TIME"
        var previousBestTime = prefs.getInt(timeKey, Int.MAX_VALUE)

        if (secondsElapsed < previousBestTime) {
            prefs.edit().putInt(timeKey, secondsElapsed).apply()
            previousBestTime = secondsElapsed // Update variable so the UI shows the new record
        }

        // 3. Daily Streak Logic
        val today = LocalDate.now()
        val lastPlayedStr = prefs.getString("LAST_PLAYED_DATE", "")

        var currentStreak = prefs.getInt("CURRENT_STREAK", 0)
        var maxStreak = prefs.getInt("MAX_STREAK", 0)
        var totalPlays = prefs.getInt("TOTAL_PLAYS", 0)

        totalPlays++ // Add 1 to total plays

        // Calculate if they kept the streak alive
        if (lastPlayedStr.isNullOrEmpty()) {
            currentStreak = 1
        } else {
            val lastPlayed = LocalDate.parse(lastPlayedStr)
            val daysBetween = ChronoUnit.DAYS.between(lastPlayed, today)

            if (daysBetween == 1L) {
                currentStreak++ // Played yesterday, streak goes up!
            } else if (daysBetween > 1L) {
                currentStreak = 1 // Missed a day, reset to 1
            }
        }

        if (currentStreak > maxStreak) {
            maxStreak = currentStreak
        }

        // Save new stats to memory
        prefs.edit()
            .putString("LAST_PLAYED_DATE", today.toString())
            .putInt("CURRENT_STREAK", currentStreak)
            .putInt("MAX_STREAK", maxStreak)
            .putInt("TOTAL_PLAYS", totalPlays)
            .apply()

        // Refresh the background grid
        buildLevelGrid()

        // 4. HIDE the game board, SHOW the new results screen
        screenGame.visibility = View.GONE
        screenResults.visibility = View.VISIBLE

        // 5. Connect the Kotlin code to your new activity_result.xml layout
        val tvFinalTime = findViewById<TextView>(R.id.tvFinalTime)
        val tvZipLevelTitle = findViewById<TextView>(R.id.tvZipLevelTitle)
        val tvPlays = findViewById<TextView>(R.id.tvPlays)
        val tvBestScore = findViewById<TextView>(R.id.tvBestScore)
        val tvMaxStreak = findViewById<TextView>(R.id.tvMaxStreak)
        val tvCurrentStreakTitle = findViewById<TextView>(R.id.tvCurrentStreakTitle)

        val btnNextLevel = findViewById<View>(R.id.btnNextLevelFromResults)
        val btnClose = findViewById<View>(R.id.btnCloseResult)

        // 6. Update the text with the real math!
        tvFinalTime.text = "Solved in ${tvTimer.text.substring(2)}"
        tvZipLevelTitle.text = "Zip #$playingLevel"
        tvPlays.text = totalPlays.toString()

        // Format the best score (e.g., 0:14)
        val m = previousBestTime / 60
        val s = previousBestTime % 60
        tvBestScore.text = String.format("%d:%02d", m, s)

        tvMaxStreak.text = maxStreak.toString()
        tvCurrentStreakTitle.text = "$currentStreak-day win streak 🔥"

        // 7. Setup Buttons
        btnNextLevel.setOnClickListener {
            screenResults.visibility = View.GONE
            startGameForLevel(playingLevel + 1)
        }

        btnClose.setOnClickListener {
            screenResults.visibility = View.GONE
            showLevelSelection()
        }
    }

    private fun showSettingsDialog() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(64, 64, 64, 32)
        }

        var settingsDialog: androidx.appcompat.app.AlertDialog? = null

        // 1. Volume Section
        val volumeTitle = android.widget.TextView(this).apply {
            text = "Music Volume"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        val seekBar = android.widget.SeekBar(this).apply {
            max = 100
            progress = (currentMusicVolume * 100).toInt()
            setPadding(0, 0, 0, 48) // Extra padding below the slider
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    currentMusicVolume = progress / 100f
                    backgroundMusic?.setVolume(currentMusicVolume, currentMusicVolume)
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })
        }

        // 2. About Us Button (Outlined Style)
        val btnAbout = com.google.android.material.button.MaterialButton(this).apply {
            text = "About Us"
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            strokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E91E63"))
            strokeWidth = 3
            elevation = 0f
            setTextColor(android.graphics.Color.parseColor("#E91E63")) // Pink text

            setOnClickListener {
                showAboutDialog()
            }
        }

        // 3. Privacy Policy Button (Text Style)
        val btnPrivacy = com.google.android.material.button.MaterialButton(this).apply {
            text = "Privacy Policy"
            backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            elevation = 0f
            strokeWidth = 0
            setTextColor(android.graphics.Color.parseColor("#E91E63")) // Pink text

            setOnClickListener {
                showPrivacyDialog()
            }
        }

        // Assemble the layout (Notice themeSwitch is gone!)
        layout.addView(volumeTitle)
        layout.addView(seekBar)
        layout.addView(btnAbout)
        layout.addView(btnPrivacy)

        settingsDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Settings")
            .setView(layout)
            .setPositiveButton("Close") { dialog, _ ->
                dialog.dismiss()
            }
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
            .setPositiveButton("Awesome") { dialog, _ ->
                dialog.dismiss()
            }
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
            .setPositiveButton("I Understand") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    // 3. Start music when the app is on screen
    override fun onResume() {
        super.onResume()
        backgroundMusic?.start()
    }

    // 4. Pause music if the user minimizes the app or turns off their screen
    override fun onPause() {
        super.onPause()
        if (backgroundMusic?.isPlaying == true) {
            backgroundMusic?.pause()
        }
    }

    // 5. Clean up memory when the app is completely closed
    override fun onDestroy() {
        super.onDestroy()
        pauseTimer() // Your existing timer cleanup

        backgroundMusic?.release()
        backgroundMusic = null
    }
}