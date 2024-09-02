package com.programtom.intellijgpt

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class GptWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = GPTChatWindow()
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(myToolWindow.content, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
