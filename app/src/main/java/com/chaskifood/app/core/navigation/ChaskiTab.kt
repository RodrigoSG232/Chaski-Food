@file:Suppress("DEPRECATION")

package com.chaskifood.app.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

object ChaskiDestinations {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val SIGN_IN = "sign_in"
    const val SIGN_UP = "sign_up"
    const val SIGN_UP_PHONE = "sign_up_phone"
    const val VERIFY_PHONE = "verify_phone/{verificationId}"
    const val FORGOT_PASSWORD = "forgot_password"
    const val CHECK_EMAIL = "check_email/{email}"
    const val LOCATION = "location"
    const val LOCATION_SEARCH = "location_search"
    const val MAIN = "main"

    const val RESTAURANTS = "restaurants"
    const val RESTAURANT_DETAIL = "restaurant_detail"
    const val MENU_TOPPING = "menu_topping"
    const val YOUR_ORDER = "your_order"
    const val CHECKOUT = "checkout"
    const val PAYMENT = "payment"
    const val ADD_CARD = "add_card"
    const val ORDER_PLACED = "order_placed"
    const val ORDER_TRACKING = "order_tracking"
    const val ORDER_PROGRESS = "order_progress"
    const val ORDER_DELIVERED = "order_delivered"
    const val ORDER_RATING = "order_rating"
    const val PROFILE_PAYMENTS = "profile_payments"
    const val REFER_FRIEND = "refer_friend"
    const val SOCIAL_ACCOUNTS = "social_accounts"
    const val ADD_LOCATION = "add_location"
    const val LOCATIONS = "locations"
    const val PROFILE_INFO = "profile_info"
    const val REGISTER_BUSINESS = "register_business"
    const val BUSINESS_STATUS = "business_status"
    const val ADMIN_BUSINESS_REVIEW = "admin_business_review"

    const val FOODS_PER_CATEGORY = "foods/{category}"
    const val FILTER = "filter"
    const val SEARCH_CATEGORIES = "search_categories"
}

enum class ChaskiTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME(route = "main/home", label = "Inicio", icon = Icons.Filled.Home),
    SEARCH(route = "main/search", label = "Buscar", icon = Icons.Filled.Search),
    ORDERS(route = "main/orders", label = "Pedidos", icon = Icons.Filled.ShoppingCart),
    ACCOUNT(route = "main/account", label = "Cuenta", icon = Icons.Filled.Person),
}