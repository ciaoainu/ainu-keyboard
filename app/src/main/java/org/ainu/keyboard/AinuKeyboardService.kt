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
            listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            listOf("⇧", "Z", "X", "C", "V", "B", "N", "M", "⌫"),
            listOf("KATA", "SPACE", "↵")
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
                        label,
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

    private fun replacePrevious(replacement: String) {
        val ic = currentInputConnection ?: return
        val previous = ic.getTextBeforeCursor(1, 0)?.toString() ?: return
        if (previous.isEmpty()) return

        undoHistory.add(previous to replacement)
        ic.deleteSurroundingText(1, 0)
        if (replacement.isNotEmpty()) ic.commitText(replacement, 1)
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
        val previous = ic.getTextBeforeCursor(1, 0)?.toString() ?: return
        if (previous.isEmpty()) return

        val replacement = when (kind) {
            "dakuten" -> voicedKana[previous]
            "handakuten" -> semiVoicedKana[previous]
            "small" -> smallKana[previous]
            else -> null
        } ?: return

        replacePrevious(replacement)
    }

    private fun cyclePrevious() {
        val ic = currentInputConnection ?: return
        val previous = ic.getTextBeforeCursor(1, 0)?.toString() ?: return
        if (previous.isEmpty()) return

        val sequence = diacriticCycles[previous] ?: return
        val replacement = sequence.firstOrNull() ?: return
        replacePrevious(replacement)
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

    private val diacriticCycles = mapOf(
        "ア" to listOf("ア"),
        "イ" to listOf("イ"),
        "ウ" to listOf("ウ"),
        "エ" to listOf("エ"),
        "オ" to listOf("オ"),
        "カ" to listOf("ガ"),
        "キ" to listOf("ギ"),
        "ク" to listOf("グ"),
        "ケ" to listOf("ゲ"),
        "コ" to listOf("ゴ"),
        "サ" to listOf("ザ"),
        "シ" to listOf("ジ"),
        "ス" to listOf("ㇲ", "ズ"),
        "セ" to listOf("ゼ"),
        "ソ" to listOf("ゾ"),
        "タ" to listOf("ダ"),
        "チ" to listOf("ヂ"),
        "ツ" to listOf("ッ", "ツ゚", "ヅ"),
        "テ" to listOf("デ"),
        "ト" to listOf("ㇳ", "ト゚", "ド"),
        "ヌ" to listOf("ヌ"),
        "ハ" to listOf("パ", "ㇵ", "バ"),
        "ヒ" to listOf("ピ", "ㇶ", "ビ"),
        "フ" to listOf("プ", "ㇷ゚", "ㇷ", "ブ"),
        "ヘ" to listOf("ペ", "ㇸ", "ベ"),
        "ホ" to listOf("ポ", "ㇹ", "ボ"),
        "ム" to listOf("ム"),
        "ヤ" to listOf("ヤ"),
        "ユ" to listOf("ユ"),
        "ヨ" to listOf("ヨ"),
        "ラ" to listOf("ラ"),
        "リ" to listOf("リ"),
        "ル" to listOf("ル"),
        "レ" to listOf("レ"),
        "ロ" to listOf("ロ")
    )

    private val voicedKana = mapOf(
        "カ" to "ガ", "キ" to "ギ", "ク" to "グ",
        "ケ" to "ゲ", "コ" to "ゴ",
        "サ" to "ザ", "シ" to "ジ", "ス" to "ズ",
        "セ" to "ゼ", "ソ" to "ゾ",
        "タ" to "ダ", "チ" to "ヂ", "ツ" to "ヅ",
        "テ" to "デ", "ト" to "ド",
        "ハ" to "バ", "ヒ" to "ビ", "フ" to "ブ",
        "ヘ" to "ベ", "ホ" to "ボ",
        "ㇷ" to "ㇷ゙"
    )

    private val semiVoicedKana = mapOf(
        "ハ" to "パ", "ヒ" to "ピ", "フ" to "プ",
        "ヘ" to "ペ", "ホ" to "ポ",
        "プ" to "ㇷ゚", "ㇷ" to "ㇷ゚",
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
        "ヘ" to "ㇸ", "ホ" to "ㇹ",
        "ム" to "ㇺ",
        "ラ" to "ㇻ", "リ" to "ㇼ", "ル" to "ㇽ",
        "レ" to "ㇾ", "ロ" to "ㇿ"
    )
}
