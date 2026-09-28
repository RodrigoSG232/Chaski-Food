package com.chaskifood.app.feature.catalog.domain

import com.chaskifood.app.core.common.ApiResult
import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    fun getCategories(businessId: String): Flow<ApiResult<List<ProductCategory>>>
    suspend fun saveCategory(category: ProductCategory): ApiResult<ProductCategory>
    suspend fun toggleCategoryStatus(categoryId: String, isActive: Boolean): ApiResult<Unit>
    fun getProducts(businessId: String): Flow<ApiResult<List<Product>>>
    suspend fun saveProduct(product: Product): ApiResult<Product>
    suspend fun toggleProductStatus(productId: String, isActive: Boolean): ApiResult<Unit>
}