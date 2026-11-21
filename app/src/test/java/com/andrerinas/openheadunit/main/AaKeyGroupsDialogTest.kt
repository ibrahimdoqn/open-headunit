package com.andrerinas.openheadunit.main

import android.app.Application
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.andrerinas.openheadunit.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.os.Looper

/**
 * The Send Keys To Android Auto choice, as the settings screen shows it (the same builder and
 * theme): every group's name is shown whole in every language, on a portrait phone and on a
 * small head unit.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34])
class AaKeyGroupsDialogTest {

    private val locales = listOf("", "ar", "cs", "de", "es", "es-rES", "fr", "hi", "hu", "in", "it", "ja", "ka", "ko",
        "nl", "pl", "pt-rBR", "pt-rPT", "ro", "ru", "th", "tr", "uk", "vi", "zh-rCN", "zh-rTW")

    private val groups = listOf(R.string.aa_key_group_select, R.string.aa_key_group_direction, R.string.aa_key_group_back,
        R.string.aa_key_group_phone, R.string.aa_key_group_assistant, R.string.aa_key_group_other)

    private fun assertWholeIn(screen: String) {
        for (locale in locales) {
            RuntimeEnvironment.setQualifiers(if (locale.isEmpty()) screen else "$locale-$screen")
            val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
            controller.get().setTheme(R.style.AppTheme)
            val activity = controller.setup().get()
            try {
                val labels = groups.map { activity.getString(it) }.toTypedArray()
                val dialog = MaterialAlertDialogBuilder(activity, R.style.DarkAlertDialog)
                    .setTitle(R.string.aa_key_groups)
                    .setMultiChoiceItems(labels, BooleanArray(labels.size) { true }) { _, _, _ -> }
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                shadowOf(Looper.getMainLooper()).idle()
                val list = dialog.listView as ListView
                val where = "${locale.ifEmpty { "en" }} on $screen"
                // A short screen scrolls the list: check what shows, then scroll to the end and check again.
                val seen = mutableSetOf<String>()
                for (position in listOf(0, labels.size - 1)) {
                    list.setSelection(position)
                    shadowOf(Looper.getMainLooper()).idle()
                    for (i in 0 until list.childCount) {
                        val row = list.getChildAt(i) as TextView
                        val layout = row.layout
                        val whole = layout != null &&
                            layout.getLineEnd(layout.lineCount - 1) == row.text.length &&
                            (0 until layout.lineCount).none { layout.getEllipsisCount(it) > 0 }
                        assertTrue("$where: '${row.text}' is cut", whole)
                        seen.add(row.text.toString())
                    }
                }
                assertEquals(where, labels.toSet(), seen)
                dialog.dismiss()
            } finally {
                controller.destroy()
            }
        }
    }

    @Test fun `a portrait phone`() = assertWholeIn("w360dp-h780dp-port-xxhdpi")
    @Test fun `an 800x450 head unit`() = assertWholeIn("w533dp-h300dp-land-hdpi")
}
