package au.com.greektv

import android.content.Context

class Favourites(context: Context) {
    private val prefs=context.getSharedPreferences("greek_tv",Context.MODE_PRIVATE)
    fun has(url:String)=prefs.getStringSet("favourites",emptySet())?.contains(url)==true
    fun toggle(url:String):Boolean{
        val s=prefs.getStringSet("favourites",emptySet())!!.toMutableSet()
        val added=if(s.contains(url)){s.remove(url);false}else{s.add(url);true}
        prefs.edit().putStringSet("favourites",s).apply()
        return added
    }
}
