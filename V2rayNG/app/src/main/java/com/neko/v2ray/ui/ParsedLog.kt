package com.neko.v2ray.ui

data class ParsedLog(
    val original: String,
    val tag: String,
    val content: String,
    val level: LogLevel
)
