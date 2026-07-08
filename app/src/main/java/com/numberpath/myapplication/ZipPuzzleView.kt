package com.numberpath.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import kotlin.math.min

class ZipPuzzleView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    // --- Configuration ---
    // Change this to 8 if you want an 8x8 grid!
    private val gridSize = 5
    private var cellSize = 0f

    private var currentMaxNodes = 5 // Highest number to connect (1 to 4)

    // --- State Management ---
    private var targetNodes = listOf<Node>()
    private val currentPath = mutableListOf<Pair<Int, Int>>()

    // Saves the absolute correct solution for the current level
    private var masterSolutionPath = listOf<Pair<Int, Int>>()

    // Callback to notify the Activity that the level is cleared
    var onLevelCleared: (() -> Unit)? = null

    // Reusable path to prevent memory warnings in onDraw
    private val drawPath = Path()

    val walls = mutableListOf<Wall>()

    // --- Paint Objects (Styling) ---
    private val wallPaint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 24f // Thick, bold lines
        strokeCap = Paint.Cap.ROUND // Gives the walls those nice rounded ends!
        style = Paint.Style.STROKE
    }

    private val gridPaint = Paint().apply {
        color = "#E0E0E0".toColorInt() // Light gray for the grid
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val pathPaint = Paint().apply {
        color = "#E91E63".toColorInt() // The pink path color
        style = Paint.Style.STROKE
        strokeWidth = 60f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val nodePaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 50f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    init {
        generateRandomLevel()
    }

    // --- Calculation ---
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cellSize = (min(w, h) / gridSize).toFloat()
    }

    // --- Level Generation (Solvable Path Algorithm) ---
    // --- Level Generation (Solvable Path Algorithm) ---
    private fun generateRandomLevel() {
        walls.clear()

        val totalCells = gridSize * gridSize

        // Increase node count for larger grids to match your Hard mode image
        currentMaxNodes = if (gridSize >= 6) (10..16).random() else (5..10).random()

        val hiddenPath = mutableListOf<Pair<Int, Int>>()
        var successfullyGenerated = false

        // Loop until a valid path is found quickly
        while (!successfullyGenerated) {
            hiddenPath.clear()
            val visited = Array(gridSize) { BooleanArray(gridSize) }
            val dirs = arrayOf(Pair(0, -1), Pair(0, 1), Pair(-1, 0), Pair(1, 0))

            var iterations = 0
            val maxIterations = 5000 // Safety kill-switch

            fun findFullPath(currentX: Int, currentY: Int): Boolean {
                iterations++
                if (iterations > maxIterations) return false

                hiddenPath.add(Pair(currentX, currentY))
                visited[currentX][currentY] = true

                if (hiddenPath.size == totalCells) return true

                dirs.shuffle()
                for (dir in dirs) {
                    val nx = currentX + dir.first
                    val ny = currentY + dir.second
                    if (nx in 0 until gridSize && ny in 0 until gridSize && !visited[nx][ny]) {
                        if (findFullPath(nx, ny)) return true
                    }
                }

                visited[currentX][currentY] = false
                hiddenPath.removeAt(hiddenPath.size - 1)
                return false
            }

            var startX: Int
            var startY: Int
            do {
                startX = (0 until gridSize).random()
                startY = (0 until gridSize).random()
            } while ((startX + startY) % 2 != 0)

            successfullyGenerated = findFullPath(startX, startY)
        }

        // Place the nodes sequentially along the successfully generated path
        val nodes = mutableListOf<Node>()
        nodes.add(Node(hiddenPath[0].first, hiddenPath[0].second, 1))

        val intermediateIndices = mutableListOf<Int>()
        val availableIndices = (1 until totalCells - 1).toMutableList()
        availableIndices.shuffle()

        for (i in 0 until (currentMaxNodes - 2)) {
            intermediateIndices.add(availableIndices[i])
        }
        intermediateIndices.sort()

        var currentNumber = 2
        for (idx in intermediateIndices) {
            nodes.add(Node(hiddenPath[idx].first, hiddenPath[idx].second, currentNumber))
            currentNumber++
        }

        nodes.add(Node(hiddenPath[totalCells - 1].first, hiddenPath[totalCells - 1].second, currentMaxNodes))

        targetNodes = nodes
        masterSolutionPath = hiddenPath.toList()

        // --- THE FIX: DYNAMIC "SAFE" WALL GENERATION ---

        // 1. Collect every possible wall location on the grid
        val allPossibleWalls = mutableListOf<Wall>()
        for (x in 0 until gridSize) {
            for (y in 0 until gridSize) {
                if (y < gridSize - 1) allPossibleWalls.add(Wall(CellPoint(x, y), CellPoint(x, y + 1))) // Horizontal walls
                if (x < gridSize - 1) allPossibleWalls.add(Wall(CellPoint(x, y), CellPoint(x + 1, y))) // Vertical walls
            }
        }

        // 2. Remove any wall that the winning pink path needs to pass through
        for (i in 0 until masterSolutionPath.size - 1) {
            val step1 = masterSolutionPath[i]
            val step2 = masterSolutionPath[i + 1]
            allPossibleWalls.removeAll { it.blocks(step1, step2) }
        }

        // 3. Shuffle the remaining safe walls and add them to the board
        allPossibleWalls.shuffle()

        // Determine how many walls to add based on grid size
        val numberOfWalls = (gridSize * 2..gridSize * 3).random()

        for (i in 0 until kotlin.math.min(numberOfWalls, allPossibleWalls.size)) {
            walls.add(allPossibleWalls[i])
        }
    }

    // --- Rendering ---
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw the Background Grid
        for (i in 0..gridSize) {
            val position = i * cellSize
            canvas.drawLine(position, 0f, position, gridSize * cellSize, gridPaint)
            canvas.drawLine(0f, position, gridSize * cellSize, position, gridPaint)
        }

        // 2. Draw the solid black walls
        for (wall in walls) {
            if (wall.c1.x == wall.c2.x) {
                // Horizontal wall
                val y = maxOf(wall.c1.y, wall.c2.y)
                val startX = (wall.c1.x * cellSize) + (cellSize * 0.1f)
                val stopX = ((wall.c1.x + 1) * cellSize) - (cellSize * 0.1f)
                val lineY = y * cellSize

                canvas.drawLine(startX, lineY, stopX, lineY, wallPaint)
            } else if (wall.c1.y == wall.c2.y) {
                // Vertical wall
                val x = maxOf(wall.c1.x, wall.c2.x)
                val startY = (wall.c1.y * cellSize) + (cellSize * 0.1f)
                val stopY = ((wall.c1.y + 1) * cellSize) - (cellSize * 0.1f)
                val lineX = x * cellSize

                canvas.drawLine(lineX, startY, lineX, stopY, wallPaint)
            }
        }

        // 3. Draw the user's path (Reusing drawPath to save memory)
        if (currentPath.isNotEmpty()) {
            drawPath.reset()
            val startX = currentPath[0].first * cellSize + (cellSize / 2)
            val startY = currentPath[0].second * cellSize + (cellSize / 2)
            drawPath.moveTo(startX, startY)

            for (i in 1 until currentPath.size) {
                val cx = currentPath[i].first * cellSize + (cellSize / 2)
                val cy = currentPath[i].second * cellSize + (cellSize / 2)
                drawPath.lineTo(cx, cy)
            }
            canvas.drawPath(drawPath, pathPaint)
        }

        // 4. Draw the numbered nodes
        for (node in targetNodes) {
            val cx = node.x * cellSize + (cellSize / 2)
            val cy = node.y * cellSize + (cellSize / 2)

            canvas.drawCircle(cx, cy, cellSize / 3, nodePaint)

            val textOffset = (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(node.number.toString(), cx, cy - textOffset, textPaint)
        }
    }

    // --- Interaction ---
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val gridX = (event.x / cellSize).toInt()
        val gridY = (event.y / cellSize).toInt()

        if (gridX !in 0 until gridSize || gridY !in 0 until gridSize) return true

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath.clear()
                currentPath.add(Pair(gridX, gridY))

                if (targetNodes.any { it.x == gridX && it.y == gridY }) {
                    vibrateOnNumberHit()
                }

                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val lastCell = currentPath.lastOrNull()
                val newCell = Pair(gridX, gridY)

                if (lastCell != null && lastCell != newCell && !currentPath.contains(newCell)) {
                    val dx = Math.abs(lastCell.first - gridX)
                    val dy = Math.abs(lastCell.second - gridY)

                    if ((dx == 1 && dy == 0) || (dx == 0 && dy == 1)) {

                        val isBlocked = walls.any { it.blocks(lastCell, newCell) }

                        if (!isBlocked) {
                            currentPath.add(newCell)

                            if (targetNodes.any { it.x == gridX && it.y == gridY }) {
                                vibrateOnNumberHit()
                            }
                            invalidate()
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                validateGame()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun validateGame() {
        val totalCells = gridSize * gridSize

        if (currentPath.size != totalCells) {
            return
        }

        var expectedNumber = 1
        for (step in currentPath) {
            val nodeAtStep = targetNodes.find { it.x == step.first && it.y == step.second }

            if (nodeAtStep != null) {
                if (nodeAtStep.number == expectedNumber) {
                    expectedNumber++
                } else {
                    return
                }
            }
        }

        if (expectedNumber > currentMaxNodes) {
            onLevelCleared?.invoke()
        }
    }

    fun startNewLevel() {
        generateRandomLevel()
        currentPath.clear()
        invalidate()
    }

    // --- Hint Logic ---
    fun showHint() {
        if (masterSolutionPath.isEmpty()) return

        var correctSteps = 0
        while (correctSteps < currentPath.size && correctSteps < masterSolutionPath.size) {
            if (currentPath[correctSteps] == masterSolutionPath[correctSteps]) {
                correctSteps++
            } else {
                break
            }
        }

        currentPath.clear()
        for (i in 0 until correctSteps) {
            currentPath.add(masterSolutionPath[i])
        }

        if (correctSteps < masterSolutionPath.size) {
            currentPath.add(masterSolutionPath[correctSteps])
        }

        invalidate()
        validateGame()
    }

    // --- Reset Logic ---
    fun resetPath() {
        currentPath.clear()
        invalidate()
    }

    // --- Modern Haptic Feedback ---
    private fun vibrateOnNumberHit() {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator.vibrate(
            VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    // --- Data Classes ---
    data class Node(val x: Int, val y: Int, val number: Int)

    data class CellPoint(val x: Int, val y: Int)

    data class Wall(val c1: CellPoint, val c2: CellPoint) {
        fun blocks(swipeStart: Pair<Int, Int>, swipeEnd: Pair<Int, Int>): Boolean {
            val start = CellPoint(swipeStart.first, swipeStart.second)
            val end = CellPoint(swipeEnd.first, swipeEnd.second)
            return (c1 == start && c2 == end) || (c1 == end && c2 == start)
        }
    }
}