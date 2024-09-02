package com.programtom.intellijgpt.models

class ChatResponse(private val error: String) {
     var created: Int? = null
     var usage: Usage? = null
     var model: String? = null
     var id: String? = null
     var choices: List<Choices>? = null
     var `object`: String? = null
}