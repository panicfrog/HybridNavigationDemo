package com.hybridnavigationdemo.router

import android.view.View
import java.util.UUID

data class RouteEntry(
    val id: UUID,
    val routePath: String,
    val module: String,
    val paramsJSON: String?,
    val view: View?
)
