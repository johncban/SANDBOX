package com.jcb.passbook.security.detection

import android.content.Context
import com.scottyab.rootbeer.RootBeer

object RootDetector {
    fun isDeviceRooted(context: Context): Boolean {
        val rootBeer = RootBeer(context)
        return rootBeer.isRooted
    }
}