package com.google.photochoice.sample

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar

class DemoPreviewActivity : AppCompatActivity() {

    private lateinit var pagerAdapter: DemoPreviewPagerAdapter
    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_demo_preview)

        val uris = IntentCompat.getParcelableArrayListExtra(intent, EXTRA_URIS, Uri::class.java)
            .orEmpty()
        if (uris.isEmpty()) {
            finish()
            return
        }
        val startIndex = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, uris.lastIndex)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val indicator = findViewById<android.widget.TextView>(R.id.textPageIndicator)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.toolbar)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        toolbar.setNavigationOnClickListener { finish() }
        pagerAdapter = DemoPreviewPagerAdapter(uris)
        viewPager.adapter = pagerAdapter
        viewPager.setCurrentItem(startIndex, false)
        updateIndicator(indicator, startIndex, uris.size)

        pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicator(indicator, position, uris.size)
                pauseAllVideos(viewPager)
                viewPager.post { playVideoAt(viewPager, position, uris) }
            }
        }.also { viewPager.registerOnPageChangeCallback(it) }

        viewPager.post {
            (viewPager.getChildAt(0) as? RecyclerView)?.overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            playVideoAt(viewPager, startIndex, uris)
        }
    }

    override fun onPause() {
        pauseAllVideos(findViewById(R.id.viewPager))
        super.onPause()
    }

    override fun onDestroy() {
        pageChangeCallback?.let { findViewById<ViewPager2>(R.id.viewPager).unregisterOnPageChangeCallback(it) }
        super.onDestroy()
    }

    private fun updateIndicator(textView: android.widget.TextView, index: Int, total: Int) {
        textView.text = getString(R.string.demo_preview_page, index + 1, total)
    }

    private fun pauseAllVideos(viewPager: ViewPager2) {
        val recycler = viewPager.getChildAt(0) as? RecyclerView ?: return
        for (i in 0 until recycler.childCount) {
            val holder = recycler.getChildViewHolder(recycler.getChildAt(i))
            if (holder is DemoPreviewPagerAdapter.PageViewHolder) {
                holder.pauseVideo()
            }
        }
    }

    private fun playVideoAt(viewPager: ViewPager2, position: Int, uris: List<Uri>) {
        val uri = uris.getOrNull(position) ?: return
        if (!uri.isVideo(this)) return
        val recycler = viewPager.getChildAt(0) as? RecyclerView ?: return
        for (i in 0 until recycler.childCount) {
            val holder = recycler.getChildViewHolder(recycler.getChildAt(i))
            if (holder is DemoPreviewPagerAdapter.PageViewHolder &&
                holder.bindingAdapterPosition == position
            ) {
                holder.startVideo()
                return
            }
        }
    }

    companion object {
        private const val EXTRA_URIS = "extra_uris"
        private const val EXTRA_INDEX = "extra_index"

        fun start(context: Context, uris: List<Uri>, index: Int) {
            val intent = Intent(context, DemoPreviewActivity::class.java).apply {
                putParcelableArrayListExtra(EXTRA_URIS, ArrayList(uris))
                putExtra(EXTRA_INDEX, index)
            }
            context.startActivity(intent)
        }
    }
}
