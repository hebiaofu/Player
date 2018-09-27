package com.hbf.player

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.view.MenuItemCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.app.MediaRouteActionProvider
import android.support.v7.media.MediaControlIntent
import android.support.v7.media.MediaRouteSelector
import android.support.v7.media.MediaRouteSelector.Builder
import android.support.v7.media.MediaRouter
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.*
import android.widget.MediaController
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.layout_remote_view.*
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var mMediaRoute : MediaRouter
    private lateinit var mRouteSelecter : MediaRouteSelector
    private var mVideoPresentation : VideoPresentation ?= null
    private lateinit var mVideo : ArrayList<EntityVideo>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mVideo = getList(baseContext)
        mMediaRoute = MediaRouter.getInstance(baseContext)
        mRouteSelecter = Builder().addControlCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO)
                .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
                .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK).build()
        initLayout()
    }

    override fun onStart() {
        super.onStart()
        mMediaRoute.addCallback(mRouteSelecter, mMediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
    }

    override fun onStop() {
        super.onStop()
        mMediaRoute.removeCallback(mMediaRouterCallback)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.abc, menu)
        val mediaRouteMenuItem = menu.findItem(R.id.menu_media_route)
        var provider = MenuItemCompat.getActionProvider(mediaRouteMenuItem) as MediaRouteActionProvider
        provider.routeSelector =mRouteSelecter
        Log.d("hbf", "provider.routeSelector: " + provider.isVisible)
        return true
    }

    private fun initLayout(){
        val gridLayoutManager = GridLayoutManager(baseContext, 3)
        gridLayoutManager.orientation = GridLayoutManager.VERTICAL
        rv_video.layoutManager = gridLayoutManager
        rv_video.adapter = mVideoAdapter
    }

    private fun getList(context: Context): ArrayList<EntityVideo> {
        val sysVideoList = ArrayList<EntityVideo>()
        // MediaStore.Video.Thumbnails.DATA:视频缩略图的文件路径
        val thumbColumns = arrayOf(MediaStore.Video.Thumbnails.DATA, MediaStore.Video.Thumbnails.VIDEO_ID)
        // 视频其他信息的查询条件
        val mediaColumns = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DATA, MediaStore.Video.Media.DURATION)

        val cursor = context.contentResolver.query(MediaStore.Video.Media
                .EXTERNAL_CONTENT_URI,
                mediaColumns, null, null, null) ?: return sysVideoList

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor
                        .getColumnIndex(MediaStore.Video.Media._ID))
                val thumbCursor = context.contentResolver.query(
                        MediaStore.Video.Thumbnails.EXTERNAL_CONTENT_URI,
                        thumbColumns, MediaStore.Video.Thumbnails.VIDEO_ID
                        + "=" + id, null, null)
                var thumbPath : String ?= null
                if (thumbCursor!!.moveToFirst()) {
                    Log.d("RemotePlayActivity", "thumbCursor is not null")
                    thumbPath = thumbCursor.getString(thumbCursor.getColumnIndex(MediaStore.Video.Thumbnails.DATA))
                }
                var path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA))
                var duration = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
                sysVideoList.add(EntityVideo(path, thumbPath, duration))
            } while (cursor.moveToNext())
        }
        return sysVideoList
    }

    data class EntityVideo(val path: String?, val thumbPath: String?, val duration: Int?)

    private val mVideoAdapter = object : RecyclerView.Adapter<MyHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyHolder {
            return MyHolder(LayoutInflater.from(baseContext).inflate(R.layout.layout_video, parent, false))
        }

        override fun getItemCount(): Int {
            return mVideo.size
        }

        override fun onBindViewHolder(holder: MyHolder, position: Int) {
            holder.mTextView.text = mVideo[position].duration.toString()
            val icon = createVideoThumbnail(mVideo[position].path)
            val drawable = BitmapDrawable(resources, icon)
            drawable.bounds = Rect(0, 0, 150, 150)
            holder.mTextView.setCompoundDrawables(null, drawable, null, null)
        }
    }

    fun createVideoThumbnail(path: String?) : Bitmap {
        val media = MediaMetadataRetriever()
        media.setDataSource(path)
        var bitmap = media.frameAtTime
        if (bitmap == null){
            bitmap = media.getFrameAtTime(0)
        }

        return bitmap
    }

    class MyHolder(itemView : View) : RecyclerView.ViewHolder(itemView){
        val mTextView = itemView.findViewById(R.id.tv_preview) as TextView
    }

    private fun updatePresentation(routeInfo : MediaRouter.RouteInfo?) {
        val display = routeInfo?.presentationDisplay

        if (display == null) {
            return
        } else{
            if (display != null && mVideoPresentation?.display != display) {
                mVideoPresentation?.dismiss()
            }

            mVideoPresentation = VideoPresentation(baseContext, display)
            mVideoPresentation?.show()
        }
    }

    private val mMediaRouterCallback = object : MediaRouter.Callback() {
        override fun onRouteUnselected(router: MediaRouter?, route: MediaRouter.RouteInfo?) {
            super.onRouteUnselected(router, route)
            updatePresentation(route)
        }

        override fun onRoutePresentationDisplayChanged(router: MediaRouter?, route: MediaRouter.RouteInfo?) {
            super.onRoutePresentationDisplayChanged(router, route)
            updatePresentation(route)
        }

        override fun onRouteSelected(router: MediaRouter?, route: MediaRouter.RouteInfo?) {
            super.onRouteSelected(router, route)
            updatePresentation(route)
        }
    }

    class VideoPresentation(context: Context, display: Display) : Presentation(context, display){
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.layout_remote_view)
            val mediaController = MediaController(context)
            videoView.setMediaController(mediaController)
            videoView.setOnPreparedListener {
                it.playbackParams.speed = 2.0f
            }
        }

        fun start(){
            videoView.start()
        }

        fun stop(){
            videoView.stopPlayback()
        }

        fun resume(){
            videoView.resume()
        }

        fun pause(){
            videoView.pause()
        }

        fun setUri(uri : Uri){
            videoView.setVideoURI(uri)
        }
    }
}
