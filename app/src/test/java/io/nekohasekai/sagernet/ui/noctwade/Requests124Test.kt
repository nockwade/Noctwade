package io.nekohasekai.sagernet.ui.noctwade

import android.app.Application
import android.graphics.Color
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.widget.noctwade.OrbitConnectView
import java.time.Duration
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/** The seven requests of 2026-11-04: the RGB picker, the hand-spun halo, picking servers, the held tun. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "ru-w392dp-h850dp-notnight-xhdpi")
class Requests124Test {
    private val app = RuntimeEnvironment.getApplication()

    private fun views(view: View, into: MutableList<View> = ArrayList()): List<View> {
        into += view
        if (view is ViewGroup) for (i in 0 until view.childCount) views(view.getChildAt(i), into)
        return into
    }

    private fun host() = Robolectric.buildActivity(DesignScreensTest.Host::class.java).setup().get()

    private fun idle() {
        repeat(6) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            Thread.sleep(20)
        }
    }

    // --- 4: block traffic while switching servers -------------------------------------------------------------------

    @Test
    fun theTunIsHeldOnlyWhenMovingToAnotherServerWithTheSettingOn() {
        assertTrue(BaseService.holdOnSwitch(true, 1L, 2L))
        assertFalse("same server: a routing change, not a switch", BaseService.holdOnSwitch(true, 1L, 1L))
        assertFalse("setting off", BaseService.holdOnSwitch(false, 1L, 2L))
        assertFalse("nothing running", BaseService.holdOnSwitch(true, null, 2L))
    }

    // --- 1: full RGB colours in gradients ---------------------------------------------------------------------------

    @Test
    fun colourEditorTakesAnyRgbValue() {
        val a = host()
        var last = 0
        val editor = ColorEditor(a, 0xFF336699.toInt()) { last = it }
        editor.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        editor.layout(0, 0, 800, editor.measuredHeight)
        assertEquals(0xFF336699.toInt(), editor.color)
        val hex = views(editor).filterIsInstance<EditText>().single()
        assertEquals("#336699", hex.text.toString())

        hex.setText("#FF8800")
        assertEquals(0xFFFF8800.toInt(), last)
        last = 0
        hex.setText("#FF88") // not a whole colour yet: nothing changes
        assertEquals(0, last)
        hex.setText("00FF7F") // the # is optional
        assertEquals(0xFF00FF7F.toInt(), last)

        // The plane: its top right corner is the hue at full saturation and brightness.
        hex.setText("#FF8800")
        val plane = views(editor).first { it.javaClass.simpleName == "Plane" }
        plane.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN, plane.width - 1f, 1f))
        assertTrue("red ${Color.red(last)}", Color.red(last) > 250)
        assertTrue("green ${Color.green(last)}", Color.green(last) in 100..170)
        // The hue bar: its far left is red.
        val bar = views(editor).first { it.javaClass.simpleName == "HueBar" }
        bar.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN, 1f, bar.height / 2f))
        assertTrue(Color.red(last) > 250 && Color.green(last) < 25 && Color.blue(last) < 25)
    }

    private fun touch(action: Int, x: Float, y: Float, at: Long = SystemClock.uptimeMillis()) = MotionEvent.obtain(at, at, action, x, y, 0)

    @Test
    fun gradientPickerKeepsExactColoursAddsAndRemoves() {
        val a = host()
        var got: IntArray? = null
        var direction = -1
        val start = intArrayOf(0xFF123456.toInt(), 0xFF654321.toInt(), 0xFFABCDEF.toInt())
        AppearancePickers.gradient(a, start, 45) { colors, angle ->
            got = colors
            direction = angle
        }
        val window = ShadowDialog.getLatestDialog().window!!.decorView
        fun click(text: String) = views(window).filterIsInstance<TextView>().first { it.text.toString() == text }.performClick()
        // The colours come back exactly, not snapped to the nearest ready swatch.
        click(a.getString(R.string.nw_done))
        assertArrayEquals(start, got)
        assertEquals(45, direction)

        // "+" adds a fourth colour (a copy of the chosen one); "Remove this colour" takes one away.
        AppearancePickers.gradient(a, start, 90) { colors, _ -> got = colors }
        val again = ShadowDialog.getLatestDialog().window!!.decorView
        views(again).first { it.contentDescription == a.getString(R.string.nw_colors_add) }.performClick()
        assertEquals(4, views(again).count { it.contentDescription?.startsWith("#") == true })
        assertTrue("no more room for a plus", views(again).none { it.contentDescription == a.getString(R.string.nw_colors_add) })
        views(again).filterIsInstance<TextView>().first { it.text.toString() == a.getString(R.string.nw_gradient_remove_stop) }.performClick()
        assertEquals(3, views(again).count { it.contentDescription?.startsWith("#") == true })
        views(again).filterIsInstance<TextView>().first { it.text.toString() == a.getString(R.string.nw_done) }.performClick()
        assertEquals(3, got?.size)
    }

    // --- 2: the halo spins by hand ----------------------------------------------------------------------------------

    private fun spinner(): OrbitConnectView {
        NoctwadeTheme.prefs(app).edit().clear().putBoolean("animations", false).putBoolean("live", false).commit()
        val a = host()
        return OrbitConnectView(a).apply {
            spinnable = true
            // A view that is not on a window never runs the click it posts.
            a.addContentView(this, ViewGroup.LayoutParams(624, 624))
            measure(View.MeasureSpec.makeMeasureSpec(624, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(624, View.MeasureSpec.EXACTLY))
            layout(0, 0, 624, 624)
        }
    }

    @Test
    fun haloTurnsAlongTheFingerAndATapStillClicks() {
        val view = spinner()
        var clicks = 0
        view.setOnClickListener { clicks++ }

        // A tap on the disc is a click and turns nothing.
        val t0 = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN, 312f, 312f, t0))
        view.dispatchTouchEvent(touch(MotionEvent.ACTION_UP, 312f, 312f, t0 + 40))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, clicks)
        assertEquals(0f, view.handTurn, 0.01f)

        // A drag along the ring, a quarter turn clockwise from the right-hand side: no click, the halo follows.
        fun at(deg: Double) = floatArrayOf(312f + 290f * cos(Math.toRadians(deg)).toFloat(), 312f + 290f * sin(Math.toRadians(deg)).toFloat())
        val t1 = SystemClock.uptimeMillis() + 1000
        var p = at(0.0)
        view.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN, p[0], p[1], t1))
        for (step in 1..18) {
            p = at(step * 5.0)
            view.dispatchTouchEvent(touch(MotionEvent.ACTION_MOVE, p[0], p[1], t1 + step * 16L))
        }
        view.dispatchTouchEvent(touch(MotionEvent.ACTION_UP, p[0], p[1], t1 + 19 * 16L))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("a drag is not a click", 1, clicks)
        assertTrue("turned ${view.handTurn}", abs(view.handTurn - 90f) < 12f)

        // The other way round brings it back.
        val t2 = t1 + 5000
        p = at(90.0)
        view.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN, p[0], p[1], t2))
        for (step in 1..18) {
            p = at(90.0 - step * 5.0)
            view.dispatchTouchEvent(touch(MotionEvent.ACTION_MOVE, p[0], p[1], t2 + step * 16L))
        }
        view.dispatchTouchEvent(touch(MotionEvent.ACTION_UP, p[0], p[1], t2 + 19 * 16L))
        assertTrue("back to ${view.handTurn}", abs(view.handTurn) < 12f)
    }

    @Test
    fun aTouchOnTheRingKeepsScrollingParentsOut() {
        // At the sides of the ring the finger moves up or down: a scrolling parent must not get the gesture first.
        var kept = false
        val parent = object : android.widget.FrameLayout(host()) {
            override fun requestDisallowInterceptTouchEvent(disallow: Boolean) {
                kept = disallow
                super.requestDisallowInterceptTouchEvent(disallow)
            }
        }
        val view = spinner()
        (view.parent as ViewGroup).removeView(view)
        parent.addView(view, ViewGroup.LayoutParams(624, 624))
        val t = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN, 312f, 312f, t))
        assertTrue("the disc leaves parents alone", !kept)
        view.dispatchTouchEvent(touch(MotionEvent.ACTION_UP, 312f, 312f, t + 40))
        view.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN, 600f, 312f, t + 500))
        assertTrue("the ring side keeps parents out from the first touch", kept)
        view.dispatchTouchEvent(touch(MotionEvent.ACTION_UP, 600f, 312f, t + 540))
    }

    @Test
    fun aHaloThatIsNotSpinnableIgnoresDrags() {
        val view = spinner().apply { spinnable = false }
        val t = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN, 600f, 312f, t))
        for (step in 1..10) view.dispatchTouchEvent(touch(MotionEvent.ACTION_MOVE, 600f, 312f + step * 20f, t + step * 16L))
        view.dispatchTouchEvent(touch(MotionEvent.ACTION_UP, 600f, 512f, t + 200))
        assertEquals(0f, view.handTurn, 0.01f)
    }

    // --- 6: picking servers to ping ---------------------------------------------------------------------------------

    @Test
    fun pickingModeShowsChecksAndTapsTogglePicks() {
        val a = host()
        var chosen = 0L
        var toggled = 0L
        val adapter = NoctwadeServersAdapter({ chosen = it.id }, { _, _ -> }, {}, {}, { _, _ -> }, {}, {}, { toggled = it.id })
        val list = RecyclerView(a).apply { layoutManager = LinearLayoutManager(a) }
        list.adapter = adapter
        adapter.submitList(
            listOf(
                ServerListItem.Server(1, 1, "A", "detail", "NL", 40, "a"),
                ServerListItem.Server(2, 1, "B", "detail", "DE", 50, "b"),
                ServerListItem.Server(3, 1, "C", "detail", "FR", 60, "c"),
            )
        )
        repeat(40) {
            if (adapter.currentList.isNotEmpty()) return@repeat
            idle()
        }
        assertEquals(3, adapter.currentList.size)
        fun layout() {
            list.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY))
            list.layout(0, 0, 800, 1600)
        }
        layout()
        fun checks() = views(list).filterIsInstance<CheckBox>()
        assertEquals(3, checks().size)
        assertTrue("no checks outside the picking mode", checks().none { it.visibility == View.VISIBLE })

        // A normal tap chooses the server.
        list.getChildAt(0).performClick()
        assertEquals(1L, chosen)

        // While picking, rows show checks and a tap toggles the pick instead of choosing.
        chosen = 0L
        adapter.picking(true, setOf(2L))
        idle()
        layout()
        assertTrue(checks().all { it.visibility == View.VISIBLE })
        assertEquals(listOf(false, true, false), checks().map { it.isChecked })
        list.getChildAt(2).performClick()
        assertEquals(3L, toggled)
        assertEquals("a tap while picking does not choose the server", 0L, chosen)

        adapter.picking(false, emptySet())
        idle()
        layout()
        assertTrue(checks().none { it.visibility == View.VISIBLE })
        assertNotNull(list)
    }
}
