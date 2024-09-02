package com.programtom.intellijgpt.models


class ChatRequest {
    var messages: List<Message>? = null
    var temperature = 0.0
    var maxTokens = 0
    var stream = false
    var model: String? = null

    class Message(role: String, content: String) {

        var role: String? = role
        var content: String? = content

    }
}