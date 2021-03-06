package com.aning.coolweather.db

import org.litepal.crud.LitePalSupport

/**
 * 城市
 */
data class City(
        var id: Int = 0,
        var cityName: String,
        var cityCode: Int,
        var provinceId: Int
) : LitePalSupport()