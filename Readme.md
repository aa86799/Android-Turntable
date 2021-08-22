
![Demo](turntable.gif)
```kotlin
val view = findViewById<TurntableView>(R.id.turntable)
val textList = listOf("中国", "美国", "新加坡", "泰国", "老挝", "Administrator", "Unbelievable")
view
    .setColors(listOf(Color.RED, Color.GRAY, Color.BLUE, Color.MAGENTA))
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
```