package au.com.greektv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private val bg = Color.rgb(10, 20, 34)
    private val card = Color.rgb(25, 48, 76)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun showHome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(64, 42, 64, 42)
            setBackgroundColor(bg)
        }
        root.addView(TextView(this).apply {
            text = "🇬🇷  GREEK TV"
            textSize = 34f
            setTextColor(Color.WHITE)
            setPadding(10, 0, 0, 28)
        })
        val rows = listOf(
            "📺  LIVE TV" to { openPlaylist() },
            "🍷  ΜΠΡΟΥΣΚΟ" to { openBrousko() },
            "🎬  ΤΑΙΝΙΕΣ" to { openPlaylist() },
            "📺  ΣΕΙΡΕΣ" to { showMessage("ΣΕΙΡΕΣ", "Περισσότερες ελληνικές σειρές σύντομα") },
            "🎵  ΜΟΥΣΙΚΗ" to { openPlaylist() },
            "🧸  ΠΑΙΔΙΚΑ" to { openPlaylist() }
        )
        rows.forEach { (title, action) ->
            root.addView(Button(this).apply {
                text = title
                textSize = 24f
                isAllCaps = false
                setTextColor(Color.WHITE)
                setBackgroundColor(card)
                isFocusable = true
                setPadding(28, 20, 28, 20)
                setOnClickListener { action() }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 82
                ).apply { setMargins(0, 7, 0, 7) }
                setOnFocusChangeListener { v, focused ->
                    v.alpha = if (focused) 1f else 0.78f
                    v.scaleX = if (focused) 1.025f else 1f
                    v.scaleY = if (focused) 1.025f else 1f
                }
            })
        }
        setContentView(root)
        root.post {
            root.findViewById<View?>(root.getChildAt(1).id)?.requestFocus()
            root.getChildAt(1).requestFocus()
        }
    }

    private fun openPlaylist() {
        // Phase 1: hand the existing M3U to a compatible installed handler.
        openUri("https://raw.githubusercontent.com/NikitaAttendSure/Greek-TV-Australia/main/greek-tv.m3u")
    }

    private fun openBrousko() {
        // Authorised ANT1 catalogue landing point. We deliberately do not extract protected media.
        openUri("https://www.antenna.gr/mprousko")
    }

    private fun openUri(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        else showMessage("GREEK TV", "Δεν βρέθηκε εφαρμογή για άνοιγμα.")
    }

    private fun showMessage(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }
}
