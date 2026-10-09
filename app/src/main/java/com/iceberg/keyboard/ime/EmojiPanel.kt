package com.iceberg.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Emoji picker shown in place of the keys. Category tabs scroll the grid; recents come first. */
@SuppressLint("ViewConstructor")
class EmojiPanel(context: Context, private val onEmoji: (String) -> Unit, private val onBackspace: () -> Unit, private val onClose: () -> Unit) :
    LinearLayout(context) {

    private val columns = 8
    private val grid = RecyclerView(context)
    private val tabs = LinearLayout(context)
    private val bottom = LinearLayout(context)
    private val adapter = Adapter()
    private var sections: List<Pair<String, List<String>>> = emptyList()
    private var theme: KeyboardTheme = KeyboardTheme.resolve(context, "system")

    init {
        orientation = VERTICAL
        tabs.orientation = HORIZONTAL
        bottom.orientation = HORIZONTAL
        grid.layoutManager = GridLayoutManager(context, columns)
        grid.adapter = adapter
        addView(tabs, LayoutParams(LayoutParams.MATCH_PARENT, dp(40)))
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, dp(46)))
        bottom.addView(button("ABC", 2f) { onClose() })
        bottom.addView(button("space", 5f) { onEmoji(" ") })
        bottom.addView(button("⌫", 2f) { onBackspace() })
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun button(text: String, weight: Float, onClick: () -> Unit) = TextView(context).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 16f
        setOnClickListener { onClick() }
        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, weight)
    }

    fun bind(theme: KeyboardTheme, recents: List<String>) {
        this.theme = theme
        setBackgroundColor(theme.background)
        for (i in 0 until bottom.childCount) (bottom.getChildAt(i) as TextView).setTextColor(theme.text)
        sections = buildList {
            if (recents.isNotEmpty()) add("🕘" to recents)
            addAll(EmojiData.supported())
        }
        tabs.removeAllViews()
        sections.forEachIndexed { i, (icon, _) ->
            tabs.addView(button(icon, 1f) { scrollToSection(i) })
        }
        adapter.rebuild()
    }

    private fun scrollToSection(i: Int) {
        (grid.layoutManager as GridLayoutManager).scrollToPositionWithOffset(adapter.sectionStart[i], 0)
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        val flat = ArrayList<String>()
        val sectionStart = ArrayList<Int>()

        @SuppressLint("NotifyDataSetChanged")
        fun rebuild() {
            flat.clear(); sectionStart.clear()
            for ((_, list) in sections) {
                // Pad each section to a full row so tabs land on a row boundary.
                sectionStart.add(flat.size)
                flat.addAll(list)
                while (flat.size % columns != 0) flat.add("")
            }
            notifyDataSetChanged()
        }

        override fun getItemCount() = flat.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val tv = TextView(parent.context).apply {
                gravity = Gravity.CENTER
                textSize = 26f
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
            }
            return object : RecyclerView.ViewHolder(tv) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val tv = holder.itemView as TextView
            val e = flat[position]
            tv.text = e
            tv.setOnClickListener(if (e.isEmpty()) null else View.OnClickListener { onEmoji(e) })
        }
    }
}

object EmojiData {
    private fun s(str: String) = str.split(' ').filter { it.isNotEmpty() }

    private var supportedCache: List<Pair<String, List<String>>>? = null

    /** Drops emoji the system font can't draw (e.g. Emoji 13+ on the S9+'s Android 10), avoiding tofu boxes. */
    fun supported(): List<Pair<String, List<String>>> = supportedCache ?: run {
        val paint = android.graphics.Paint()
        CATEGORIES.map { (icon, list) -> icon to list.filter { paint.hasGlyph(it) } }.also { supportedCache = it }
    }

    val CATEGORIES: List<Pair<String, List<String>>> = listOf(
        "😀" to s("😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 💩 🤡 👻 👽 🤖"),
        "👍" to s("👍 👎 👌 🤌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐️ 🖖 👋 👏 🙌 👐 🤲 🙏 ✍️ 💪 🦾 👀 👁️ 👅 👄 🧠 🫶 🤝 💅 🙋 🙆 🙅 🤷 🤦 🙇 💁 🧑‍💻 🏃 🚶 💃 🕺 👶 🧒 👦 👧 🧑 👨 👩 🧓 👴 👵"),
        "❤️" to s("❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 💯 💢 💥 💫 💦 💨 🔥 ✨ ⭐ 🌟 ⚡ 💤 💬 💭"),
        "🐶" to s("🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🐺 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐢 🐍 🐙 🦑 🦀 🐠 🐟 🐬 🐳 🦈 🐊 🐘 🦒 🐄 🐑 🐕 🐈 🌵 🌲 🌴 🌱 🌿 🍀 🍁 🌸 🌹 🌻 🌷 🌞 🌙 ☀️ ⛅ 🌧️ ❄️ 🌈 🌊"),
        "🍔" to s("🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🥑 🥦 🌽 🥕 🧄 🥔 🍞 🥐 🧀 🥚 🍳 🥓 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥗 🍝 🍜 🍣 🍱 🍤 🍚 🍦 🍩 🍪 🎂 🍰 🧁 🍫 🍿 ☕ 🍵 🥤 🍺 🍻 🥂 🍷 🍸"),
        "⚽" to s("⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🥊 ⛳ 🎣 🎽 🎿 🏂 🏋️ 🚴 🏊 🏆 🥇 🥈 🥉 🎮 🕹️ 🎲 🧩 🎯 🎳 🎸 🎹 🎺 🎻 🥁 🎤 🎧 🎬 🎨 🎉 🎊 🎈 🎁 🎄 🎃"),
        "🚗" to s("🚗 🚕 🚙 🚌 🏎️ 🚓 🚑 🚒 🚚 🚜 🛵 🏍️ 🚲 🛴 🚂 🚆 🚇 ✈️ 🛫 🛬 🚀 🛸 🚁 ⛵ 🚤 🚢 ⚓ 🗺️ 🏠 🏡 🏢 🏥 🏦 🏨 🏫 ⛪ 🏰 🗽 🗼 ⛰️ 🏖️ 🏝️ 🌋 🏕️ 🌍"),
        "💡" to s("⌚ 📱 💻 ⌨️ 🖥️ 🖨️ 🖱️ 💾 📷 📸 🎥 📺 📻 ⏰ ⏳ 💡 🔦 🔋 🔌 💸 💵 💳 💎 🔧 🔨 🛠️ 🔩 ⚙️ 🧲 🔑 🗝️ 🔒 🔓 📦 📫 ✉️ 📧 📝 📎 📌 📍 ✂️ 📁 📅 📈 📉 📊 📋 🗑️ 🔍 🔔 🛒 🎓 💊 🩺"),
        "✅" to s("✅ ☑️ ✔️ ❌ ❎ ➕ ➖ ➗ ✖️ ♾️ ‼️ ⁉️ ❓ ❔ ❗ ❕ ⚠️ 🚫 ⛔ 🔞 ♻️ 🔄 🔃 ⬆️ ⬇️ ⬅️ ➡️ ↩️ ↪️ 🆗 🆕 🆓 🆒 🆘 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🟥 🟩 🟦 ⬛ ⬜ 🔶 🔷 ©️ ®️ ™️ #️⃣ 🔢 🏳️ 🏴 🏁 🚩 🏳️‍🌈"),
    )
}
