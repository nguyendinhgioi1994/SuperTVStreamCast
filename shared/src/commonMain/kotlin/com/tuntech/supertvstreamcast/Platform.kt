package com.tuntech.supertvstreamcast

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform