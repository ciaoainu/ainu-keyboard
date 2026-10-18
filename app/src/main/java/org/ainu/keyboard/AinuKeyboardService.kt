package org.ainu.keyboard

import android.inputmethodservice.InputMethodService
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import kotlin.math.abs

class AinuKeyboardService : InputMethodService() {

    private var latinMode = false
    private val undoHistory = mutableListOf<Pair<String, String>>()

    override fun onCreateInputView(): View {
        return KeyboardView().apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(if (latinMode) 300 else 250)
            )
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private inner class KeyboardView : View(this@AinuKeyboardService) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var startX = 0f
        private var startY = 0f
        private var startRow = 0
        private var startCol = 0

        private val kanaRows = listOf(
            listOf("UNDO", "ア", "カ", "サ", "⌫"),
            listOf("←", "タ", "ナ", "ハ", "→"),
            listOf("😀", "マ", "ヤ", "ラ", "SPACE"),
            listOf("ABC", "記", "ワ", "，", "↵")
        )

        private val latinRows = listOf(
            listOf("Q", "W", "E"),
            listOf("R", "T", "Y"),
            listOf("U", "I", "O"),
            listOf("P", "A", "S"),
            listOf("D", "F", "G"),
            listOf("H", "J", "K"),
            listOf("L", "Z", "X"),
            listOf("C", "V", "B"),
            listOf("N", "M", "⌫"),
            listOf("←", "→", "KATA"),
            listOf("😀", "SPACE", "↵")
        )

        private fun rows() = if (latinMode) latinRows else kanaRows

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val desiredHeight = dp(if (latinMode) 300 else 250)
            val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(measuredWidth, desiredHeight)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.rgb(224, 229, 237))

            val currentRows = rows()
            val cellW = width.toFloat() / 5
            val cellH = height.toFloat() / currentRows.size

            for (r in currentRows.indices) {
                for (c in 0 until 5) {
                    val rect = RectF(
                        c * cellW + dp(2).toFloat(),
                        r * cellH + dp(2).toFloat(),
                        (c + 1) * cellW - dp(2).toFloat(),
                        (r + 1) * cellH - dp(2).toFloat()
                    )

                    val label = currentRows[r][c]
                    val displayLabel = when (label) {
                        "UNDO" -> "↶"
                        "SPACE" -> "␣"
                        "記" -> "゛"
                        else -> label
                    }
                    paint.color = when (label) {
                        "SPACE", "↵", "→", "⌫", "UNDO", "←", "😀", "ABC", "KATA" ->
                            Color.rgb(204, 222, 241)
                        else -> Color.WHITE
                    }
                    canvas.drawRoundRect(rect, dp(7).toFloat(), dp(7).toFloat(), paint)

                    paint.color = Color.rgb(35, 43, 55)
                    paint.textAlign = Paint.Align.CENTER
                    paint.textSize = when {
                        label == "SPACE" -> dp(13).toFloat()
                        label.length > 2 -> dp(14).toFloat()
                        else -> dp(22).toFloat()
                    }
                    canvas.drawText(
                        displayLabel,
                        rect.centerX(),
                        rect.centerY() - (paint.ascent() + paint.descent()) / 2,
                        paint
                    )
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val cellW = width.toFloat() / 5
            val cellH = height.toFloat() / rows().size

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    startCol = (event.x / cellW).toInt().coerceIn(0, 4)
                    startRow = (event.y / cellH).toInt().coerceIn(0, rows().lastIndex)
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    val dx = event.x - startX
                    val dy = event.y - startY
                    val threshold = dp(18).toFloat()

                    val direction = when {
                        abs(dx) > abs(dy) && abs(dx) > threshold ->
                            if (dx < 0) "left" else "right"
                        abs(dy) > threshold ->
                            if (dy < 0) "up" else "down"
                        else -> ""
                    }

                    handleKey(rows()[startRow][startCol], direction)
                    invalidate()
                    return true
                }
            }
            return true
        }
    }

    private fun insertText(text: String) {
        val ic = currentInputConnection ?: return
        undoHistory.add("" to text)
        ic.commitText(text, 1)
    }

    
    private fun replacePreviousText(previous: String, replacement: String) {
        val ic = currentInputConnection ?: return
        if (previous.isEmpty()) return

        undoHistory.add(previous to replacement)
        ic.deleteSurroundingText(previous.length, 0)
        if (replacement.isNotEmpty()) {
            ic.commitText(replacement, 1)
        }
    }


    private fun undo() {
        val ic = currentInputConnection ?: return
        if (undoHistory.isEmpty()) return

        val (deleted, inserted) = undoHistory.removeAt(undoHistory.lastIndex)
        if (inserted.isNotEmpty()) {
            ic.deleteSurroundingText(inserted.length, 0)
        }
        if (deleted.isNotEmpty()) {
            ic.commitText(deleted, 1)
        }
    }

    private fun handleKey(label: String, direction: String) {
        val ic = currentInputConnection ?: return

        when (label) {
            "ABC", "KATA" -> {
                latinMode = !latinMode
                setInputView(onCreateInputView())
            }

            "SPACE" -> insertText(" ")

            "↵" -> ic.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
            )

            "⌫" -> {
                val previous = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
                if (previous.isNotEmpty()) {
                    undoHistory.add(previous to "")
                    ic.deleteSurroundingText(1, 0)
                }
            }

            "←" -> ic.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)
            )

            "→" -> ic.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
            )

            "UNDO" -> undo()

            "😀" -> insertText("😀")

            "，" -> insertText(
                when (direction) {
                    "left" -> "。"
                    "up" -> "？"
                    "right" -> "！"
                    "down" -> "・"
                    else -> "，"
                }
            )

            "記" -> {
                when (direction) {
                    "left" -> transformPrevious("dakuten")
                    "right" -> transformPrevious("handakuten")
                    "up" -> transformPrevious("small")
                    "down" -> Unit
                    else -> cyclePrevious()
                }
            }

            else -> {
                if (latinMode) {
                    when (label) {
                        "⇧" -> Unit
                        else -> insertText(label.lowercase())
                    }
                } else {
                    val chars = kanaMap[label] ?: listOf(label)
                    val chosen = when (direction) {
                        "left" -> chars.getOrElse(1) { chars[0] }
                        "up" -> {
                            if (label == "ヤ") "ユ"
                            else chars.getOrElse(2) { chars[0] }
                        }
                        "right" -> {
                            if (label == "ヤ") ")"
                            else chars.getOrElse(3) { chars[0] }
                        }
                        "down" -> {
                            if (label == "ヤ") "ヨ"
                            else chars.getOrElse(4) { chars[0] }
                        }
                        else -> chars[0]
                    }

                    val output = if (label == "ヤ") {
                        when (direction) {
                            "left" -> "("
                            "up" -> "ユ"
                            "right" -> ")"
                            "down" -> "ヨ"
                            else -> "ヤ"
                        }
                    } else chosen

                    insertText(output)
                }
            }
        }
    }

    
    private fun transformPrevious(kind: String) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(8, 0)?.toString() ?: return
        if (before.isEmpty()) return

        val mapping = when (kind) {
            "dakuten" -> voicedKana
            "handakuten" -> semiVoicedKana
            "small" -> smallKana
            else -> return
        }

        val previous = mapping.keys
            .sortedByDescending { it.length }
            .firstOrNull { before.endsWith(it) }
            ?: return

        val replacement = mapping[previous] ?: return
        replacePreviousText(previous, replacement)
    }


    
    
    private fun cyclePrevious() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(8, 0)?.toString() ?: return
        if (before.isEmpty()) return

        val candidates = diacriticCycles
            .flatten()
            .distinct()
            .sortedByDescending { it.length }

        val previous = candidates.firstOrNull { before.endsWith(it) }
            ?: return

        val cycle = diacriticCycles.firstOrNull { previous in it }
            ?: return

        val index = cycle.indexOf(previous)
        val replacement = cycle[(index + 1) % cycle.size]

        replacePreviousText(previous, replacement)
    }



    private val kanaMap = mapOf(
        "ア" to listOf("ア", "イ", "ウ", "エ", "オ"),
        "カ" to listOf("カ", "キ", "ク", "ケ", "コ"),
        "サ" to listOf("サ", "シ", "ス", "セ", "ソ"),
        "タ" to listOf("タ", "チ", "ツ", "テ", "ト"),
        "ナ" to listOf("ナ", "ニ", "ヌ", "ネ", "ノ"),
        "ハ" to listOf("ハ", "ヒ", "フ", "ヘ", "ホ"),
        "マ" to listOf("マ", "ミ", "ム", "メ", "モ"),
        "ヤ" to listOf("ヤ", "ユ", "ヨ"),
        "ラ" to listOf("ラ", "リ", "ル", "レ", "ロ"),
        "ワ" to listOf("ワ", "ヰ", "ン", "ヱ", "ヲ")
    )

    private val diacriticCycles = listOf(
        listOf("ア", "ァ"),
        listOf("イ", "ィ"),
        listOf("ウ", "ゥ"),
        listOf("エ", "ェ"),
        listOf("オ", "ォ"),

        listOf("カ", "ガ"),
        listOf("キ", "ギ"),
        listOf("ク", "ㇰ", "グ"),
        listOf("ケ", "ゲ"),
        listOf("コ", "ゴ"),

        listOf("サ", "ザ"),
        listOf("シ", "ㇱ", "ジ"),
        listOf("ス", "ㇲ", "ズ"),
        listOf("セ", "セ゚", "ゼ"),
        listOf("ソ", "ゾ"),

        listOf("タ", "ダ"),
        listOf("チ", "ヂ"),
        listOf("ツ", "ッ", "ツ゚", "ヅ"),
        listOf("テ", "デ"),
        listOf("ト", "ㇳ", "ト゚", "ド"),

        listOf("ナ", "ナ"),
        listOf("ニ", "ニ"),
        listOf("ヌ", "ㇴ"),
        listOf("ネ", "ネ"),
        listOf("ノ", "ノ"),

        listOf("ハ", "パ", "ㇵ", "バ"),
        listOf("ヒ", "ピ", "ㇶ", "ビ"),
        listOf("フ", "プ", "ㇷ゚", "ㇷ", "ブ"),
        listOf("ヘ", "ペ", "ㇸ", "ベ"),
        listOf("ホ", "ポ", "ㇹ", "ボ"),

        listOf("マ", "マ"),
        listOf("ミ", "ミ"),
        listOf("ム", "ㇺ"),
        listOf("メ", "メ"),
        listOf("モ", "モ"),

        listOf("ヤ", "ャ"),
        listOf("ユ", "ュ"),
        listOf("ヨ", "ョ"),

        listOf("ラ", "ㇻ"),
        listOf("リ", "ㇼ"),
        listOf("ル", "ㇽ"),
        listOf("レ", "ㇾ"),
        listOf("ロ", "ㇿ")
    )

    private val voicedKana = mapOf(
        "カ" to "ガ", "キ" to "ギ", "ク" to "グ",
        "ケ" to "ゲ", "コ" to "ゴ",
        "サ" to "ザ", "シ" to "ジ", "ス" to "ズ",
        "セ" to "ゼ", "ソ" to "ゾ",
        "タ" to "ダ", "チ" to "ヂ", "ツ" to "ヅ",
        "テ" to "デ", "ト" to "ド",
        "ハ" to "バ", "ヒ" to "ビ", "フ" to "ブ",
        "ヘ" to "ベ", "ホ" to "ボ"
    )

    private val semiVoicedKana = mapOf(
        "ハ" to "パ", "ヒ" to "ピ", "フ" to "プ",
        "ヘ" to "ペ", "ホ" to "ポ",
        "ㇷ" to "ㇷ゚",
        "セ" to "セ゚", "ツ" to "ツ゚", "ト" to "ト゚"
    )

    private val smallKana = mapOf(
        "ア" to "ァ", "イ" to "ィ", "ウ" to "ゥ",
        "エ" to "ェ", "オ" to "ォ",
        "ヤ" to "ャ", "ユ" to "ュ", "ヨ" to "ョ",
        "ツ" to "ッ",
        "ク" to "ㇰ", "シ" to "ㇱ", "ス" to "ㇲ",
        "ト" to "ㇳ", "ヌ" to "ㇴ",
        "ハ" to "ㇵ", "ヒ" to "ㇶ", "フ" to "ㇷ",
        "プ" to "ㇷ゚",
        "ヘ" to "ㇸ", "ホ" to "ㇹ",
        "ム" to "ㇺ",
        "ラ" to "ㇻ", "リ" to "ㇼ", "ル" to "ㇽ",
        "レ" to "ㇾ", "ロ" to "ㇿ"
    )
}
