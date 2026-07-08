Zip Puzzle 🧩
Zip Puzzle is a sleek, brain-teasing Android game built entirely in Kotlin. The goal is simple but highly challenging: draw a single, continuous path that connects numbered dots in the correct order while filling every single cell on the board.

With mathematically guaranteed solvable grids, dynamic wall barriers, and daily streak tracking, this game combines modern UI design with complex algorithmic pathfinding.

🚀 Key Features
 -> Algorithmic Level Generation: Levels are not hard-coded. The game uses a custom Depth-First Search (DFS) algorithm to dynamically generate valid Hamiltonian paths on the fly, ensuring infinite puzzle combinations.
 -> Dynamic Barriers (Hard Mode): Randomly generated solid walls block the player's path, forcing creative problem-solving on larger grids (up to 8x8).
 -> Custom View Engine: The entire puzzle board is built from scratch using Android's Canvas API (ZipPuzzleView), ensuring buttery-smooth 60fps drawing, rounded path joins, and instant touch-response.
 -> Daily Streaks & Stats Tracking: Uses SharedPreferences and java.time to track consecutive daily logins, total plays, win percentages, and personal best times, displayed in a premium full-screen results UI.
 -> Immersive Haptics & Audio: Features responsive haptic feedback (VibratorManager) that clicks when hitting a target node, alongside looping background music with a custom volume slider.
 -> Modern UI/UX: Forced dark-mode for eye comfort, clean Material Design components, and an animated, interactive "How to Play" expanding card.

🎮 How to Play
 -> Connect the Dots: Start at 1 and draw a continuous line to 2, then 3, and so on.
 -> Fill Every Cell: Your path must touch absolutely every empty square on the grid.
 -> Dodge the Walls: You cannot cross the thick black lines.
 -> Beat Your Best Time: Solve it as fast as you can to record a new personal best on the level select screen!

🛠️ Technical Stack
 -> Language: Kotlin
 -> Architecture: Native Android SDK
 -> UI Components: Material Design 3 (MaterialCardView, MaterialButton), Custom View (Canvas/Paint)
 -> Data Persistence: SharedPreferences (Local storage for unlocked levels, times, and streak math)

👨‍💻 Developed By
 -> TECHNICK-TN A passionate full-stack developer and MCA student at Charusat University, based in Morbi, Gujarat. 
 Specializing in sleek UI design, mobile application development, and complex algorithmic integrations.

 -> If you enjoyed this project or want to collaborate, feel free to reach out!
