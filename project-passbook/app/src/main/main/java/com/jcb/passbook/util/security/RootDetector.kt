// RootDetector.kt
package com.jcb.passbook.util

import android.content.Context
import com.scottyab.rootbeer.RootBeer

object RootDetector {
    fun isDeviceRooted(context: Context): Boolean {
        val rootBeer = RootBeer(context)
        return rootBeer.isRooted
    }
}
