package com.stone.sample

import android.app.AlertDialog
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.content.ContentProviderCompat.requireContext
import com.stone.turntable.R
import com.stone.turntable.lib.TurntableView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val view = findViewById<TurntableView>(R.id.turntable)
        val textList = listOf("中国", "美国", "新加坡", "泰国", "老挝", "Administrator", "Unbelievable")
        view
//            .setTimeInterpolator(LinearInterpolator())
//            .setTimeInterpolator(DecelerateInterpolator())
            .setAnimEndDuration(3000L)
            .setTouchToEndDelay(2000L)
            .setTextList(textList)
        view.mOnRotateEndListener = {
            AlertDialog.Builder(this).setMessage(textList[it]).create().show()
        }
        view.mOnPartClickListener = {
            AlertDialog.Builder(this).setMessage(textList[it]).create().show()
        }
        view.post {
            view.drawingTurntable()
        }
    }

}