package com.embylite.presentation.navigation

// 导航路由：所有页面只传 itemId（字符串），禁止传 Parcelable
sealed class Screen(val route: String) {
    // 底部导航 4 个 Tab（在 MainActivity 内部嵌套 NavHost）
    object Home : Screen("home")
    object Search : Screen("search")
    object Library : Screen("library")
    object Settings : Screen("settings")

    // 跳转页面
    object LibraryItems : Screen("library_items/{parentId}/{parentName}") {
        fun createRoute(parentId: String, parentName: String): String =
            "library_items/$parentId/${parentName.encode()}"
    }

    object Detail : Screen("detail/{itemId}") {
        fun createRoute(itemId: String): String = "detail/$itemId"
    }

    // 通用列表页：按标签 / 按演员筛选复用
    object FilterItems : Screen("filter/{type}/{id}/{name}") {
        // type: tag | person
        fun createRoute(type: String, id: String, name: String): String =
            "filter/$type/$id/${name.encode()}"
    }

    object Player : Screen("player/{itemId}") {
        fun createRoute(itemId: String): String = "player/$itemId"
    }

    // 设置子页面
    object Update : Screen("update")
    object Logs : Screen("logs")
}

// 简单 URL 编码（避免中文 name 破坏路由）
private fun String.encode(): String =
    android.net.Uri.encode(this)
