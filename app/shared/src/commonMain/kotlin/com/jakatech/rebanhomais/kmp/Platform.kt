package com.jakatech.rebanhomais.kmp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform