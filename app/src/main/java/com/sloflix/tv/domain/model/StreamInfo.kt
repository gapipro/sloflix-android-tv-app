package com.sloflix.tv.domain.model

data class StreamInfo(val url: String, val headers: Map<String, String> = emptyMap())
