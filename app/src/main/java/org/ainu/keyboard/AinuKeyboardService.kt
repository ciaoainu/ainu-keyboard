package org.ainu.keyboard

import android.inputmethodservice.InputMethodService
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.RectF
import android.view.View
import android.view.MotionEvent
import android.view.inputmethod.InputConnection
import kotlin.math.abs

class AinuKeyboardService : InputMethodService() {

    private var latinMode = false
    private var lastText = ""

    override fun onCreateInputView(): View {
        return KeyboardView()
    }

    private inner class KeyboardView : View(this@AinuKeyboardService) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var startX = 0f
        private var startY = 0f
        private var startRow = -1
        private var startCol = -1

        private val kanaRows = listOf(
            listOf("ア", "カ", "サ"),
            listOf("タ", "ナ", "ハ"),
            listOf("マ", "ヤ", "ラ"),
            listOf("ワ", "゛", "゜"),
            listOf("←", "→", "UNDO"),
            listOf("😀", "ABC", "⌫"),
            listOf("。", "SPACE", "↵")
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

        private fun rows() =
            if (latinMode) latinRows else kanaRows

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            canvas.drawColor(Color.rgb(225, 229, 235))

            val currentRows = rows()
            val cellW = width.toFloat() / 3
            val cellH = height.toFloat() / currentRows.size

            for (r in currentRows.indices) {
                for (c in 0..2) {
                    val left = c * cellW + 3f
                    val top = r * cellH + 3f
                    val rect = RectF(
                        left, top,
                        left + cellW - 6f,
                        top + cellH - 6f
                    )

                    paint.color = Color.WHITE
                    canvas.drawRoundRect(rect, 12f, 12f, paint)

                    val label = currentRows[r][c]
                    paint.color = Color.DKGRAY
                    paint.textAlign = Paint.Align.CENTER
                    paint.textSize = cellH.coerceAtMost(48f) * 0.36f

                    canvas.drawText(
                        label,
                        rect.centerX(),
                        rect.centerY() -
                            (paint.ascent() + paint.descent()) / 2,
                        paint
                    )
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val cellW = width.toFloat() / 3
            val cellH = height.toFloat() / rows().size

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    startCol = (event.x / cellW).toInt()
                        .coerceIn(0, 2)
                    startRow = (event.y / cellH).toInt()
                        .coerceIn(0, rows().lastIndex)
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    val dx = event.x - startX
                    val dy = event.y - startY

                    val threshold = cellW * 0.22f
                    var direction = ""

                    if (abs(dx) > abs(dy) && abs(dx) > threshold) {
                        direction = if (dx < 0) "left" else "right"
                    } else if (abs(dy) > threshold) {
                        direction = if (dy < 0) "up" else "down"
                    }

                    val label = rows()[startRow][startCol]
                    handleKey(label, direction)
                    invalidate()
                    return true
                }
            }
            return true
        }
    }

    private fun send(text: String) {
        currentInputConnection?.commitText(text, 1)
        lastText = text
    }

    private fun handleKey(label: String, direction: String) {
        val ic = currentInputConnection ?: return

        when (label) {
            "ABC", "KATA" -> {
                latinMode = !latinMode
                currentInputView?.invalidate()
            }

            "SPACE" -> send(" ")

            "↵" -> ic.sendKeyEvent(
                android.view.KeyEvent(
                    android.view.KeyEvent.ACTION_DOWN,
                    android.view.KeyEvent.KEYCODE_ENTER
                )
            )

            "⌫" -> {
                ic.deleteSurroundingText(1, 0)
                lastText = ""
            }

            "←" -> ic.sendKeyEvent(
                android.view.KeyEvent(
                    android.view.KeyEvent.ACTION_DOWN,
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT
                )
            )

            "→" -> ic.sendKeyEvent(
                android.view.KeyEvent(
                    android.view.KeyEvent.ACTION_DOWN,
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                )
            )

            "UNDO" -> {
                if (lastText.isNotEmpty()) {
                    ic.deleteSurroundingText(lastText.length, 0)
                    lastText = ""
                }
            }

            "😀" -> send("😀")
            "。" -> send(
                when (direction) {
                    "left" -> ","
                    "up" -> "?"
                    "right" -> "!"
                    "down" -> "・"
                    else -> "。"
                }
            )

            "゛" -> modifyPrevious(direction, "dakuten")
            "゜" -> modifyPrevious(direction, "handakuten")

            else -> {
                if (latinMode) {
                    send(label.lowercase())
                } else {
                    val chars = kanaMap[label] ?: listOf(label)
                    val chosen = when (direction) {
                        "left" -> chars.getOrElse(1) { chars[0] }
                        "up" -> chars.getOrElse(2) { chars[0] }
                        "right" -> chars.getOrElse(3) { chars[0] }
                        "down" -> chars.getOrElse(4) { chars[0] }
                        else -> chars[0]
                    }
                    send(chosen)
                }
            }
        }
    }

    private fun modifyPrevious(direction: String, kind: String) {
        val ic = currentInputConnection ?: return

        val previous = ic.getTextBeforeCursor(1, 0)?.toString()
            ?: return
        if (previous.isEmpty()) return

        val replacement = when {
            direction == "up" -> smallKana[previous]
            kind == "dakuten" || direction == "left" ->
                voicedKana[previous]
            kind == "handakuten" || direction == "right" ->
                semiVoicedKana[previous]
            else -> null
        } ?: return

        ic.deleteSurroundingText(1, 0)
        ic.commitText(replacement, 1)
        lastText = replacement
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
        "セ" to "セ゚", "ツ" to "ツ゚",
        "ト" to "ト゚"
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
