package com.chaskifood.app.feature.discover.domain

import com.chaskifood.app.core.common.ApiResult

interface DiscoverRepository {
    suspend fun getNearbyRestaurants(): ApiResult<List<Restaurant>>
}