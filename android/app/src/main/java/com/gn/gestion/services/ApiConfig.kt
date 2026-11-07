package com.gn.gestion.services;

import com.gn.gestion.BuildConfig

object ApiConfig {

    val BASE_URL: String = BuildConfig.SERVER_URL

    const val SYNC_ENABLED: Boolean = true

    const val LOGS: Boolean = false

}