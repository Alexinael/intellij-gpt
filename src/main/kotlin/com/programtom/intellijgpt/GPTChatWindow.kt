package com.programtom.intellijgpt

import com.google.gson.Gson
import com.google.gson.JsonObject // ✅ Импорт JsonObject исправлен
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.programtom.intellijgpt.models.ChatRequest
import com.programtom.intellijgpt.models.ChatResponse
import org.apache.http.HttpStatus
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import java.awt.BorderLayout
import java.awt.GridLayout
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import javax.swing.*
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED


//private const val model = "lmstudio-community/Meta-Llama-3.1-8B-Instruct-GGUF/Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf"
private const val model =
    "qwen2.5-7b-instruct-1m" //""lmstudio-community/Meta-Llama-3.1-8B-Instruct-GGUF/Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf"
private const val server: String = "localhost"

private const val JUMP_SCROLL_SKIPS = 70

@Service(Service.Level.PROJECT)
class GPTChatWindow {


    private var prompt: JTextArea
    private var chat: JButton
    private var editor: JTextPane
    private var systemPrompt: JTextField
    private var scrollPane: JBScrollPane

    private lateinit var modelSelector: JComboBox<String> // ✅ Используем lateinit, чтобы избежать проблем инициализации
    val content: JPanel
//    val map = HashMap<String, String>()

    private fun sendTextToEndpoint() {
        var currentFileName = "Unknown File"

        SwingUtilities.invokeLater { // ✅ Оборачиваем в UI-тред
//            val project = ApplicationManager.getApplication().currentProject ?: return@invokeLater
//            val project = FileEditorManager.getInstance(null)?.project ?: return@invokeLater
            val project = com.intellij.openapi.project.ProjectManager.getInstance().openProjects.firstOrNull()
                ?: return@invokeLater

            val editorManager = FileEditorManager.getInstance(project)
            val files = editorManager.selectedFiles

            if (files.isNotEmpty()) {
                val file = files[0]
                val fileType = file.fileType.name
                currentFileName = file.name
//                println("Current file type: $fileType")
            }
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            val objectMapper = Gson()
            val urlStr = "http://$server:1234/v1/chat/completions"
            val url = URI.create(urlStr).toURL()
            val connection = url.openConnection() as HttpURLConnection

            connection.setRequestMethod("POST")
            connection.setRequestProperty("Content-Type", "application/json")

            connection.setDoOutput(true)
            val os = connection.outputStream

            val chatRequest = ChatRequest()
            chatRequest.messages = (
                    listOf(
                        ChatRequest.Message(
                            "system",
                            systemPrompt.text
                        ), ChatRequest.Message("user", "Проект: ${currentFileName}\n\n  ${prompt.text}")
                    )
                    )
            chatRequest.stream = true
            chatRequest.model =
//                model
                modelSelector.selectedItem as String


            os.write(objectMapper.toJson(chatRequest).encodeToByteArray())
            os.flush()

            val flavour: MarkdownFlavourDescriptor = GFMFlavourDescriptor()
            val parsedTree = MarkdownParser(flavour)


            val responseCode = connection.getResponseCode()
            if (responseCode == HttpStatus.SC_OK) {
                val sb = StringBuilder()
                var skipJumpingScroll = 0
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    var line: String?
                    while (true) {
                        line = reader.readLine()
                        if (line == null || line.contains("[DONE]")) {
                            ApplicationManager.getApplication().runReadAction {
//                                val html = toHTML(parsedTree, sb, flavour)
//                                editor.text = addCopy(html)
                                scrollPane.verticalScrollBar.value = scrollPane.verticalScrollBar.maximum
                            }
                            break
                        }
                        val replace = line.replace("data: ", "")
                        val chatResponse = objectMapper.fromJson(replace, ChatResponse::class.java)
                        if (chatResponse?.choices != null && chatResponse.choices!!.isNotEmpty()) {
                            if (chatResponse.choices!!.first().delta?.content != null) {
                                sb.append(chatResponse.choices!!.first().delta?.content)

                                val html = toHTML(parsedTree, sb, flavour)
                                ApplicationManager.getApplication().runReadAction {
                                    editor.text = html
                                    skipJumpingScroll++
                                    if (skipJumpingScroll > JUMP_SCROLL_SKIPS) {
                                        skipJumpingScroll = 0
                                        scrollPane.verticalScrollBar.value = scrollPane.verticalScrollBar.maximum
                                    }
                                }
                            }
                        }
                    }
                }

            }


        }
    }

    private fun toHTML(
        parsedTree: MarkdownParser,
        sb: StringBuilder,
        flavour: MarkdownFlavourDescriptor,
    ): String {
        val tree = parsedTree.buildMarkdownTreeFromString(sb.toString())
        val html = (HtmlGenerator(sb.toString(), tree, flavour, false).generateHtml())
        return html
    }


    init {
        this.content = JPanel().apply {
            systemPrompt = JTextField("Helpful Coding Assistant")
            systemPrompt.toolTipText = "System Prompt"

            modelSelector = JComboBox()
            modelSelector.toolTipText = "Select AI Model"
            fetchModels() // ✅ Загружаем список моделей из API

            layout = GridLayout(2, 1)
            prompt = JTextArea("")
            prompt.lineWrap = true

            editor = JTextPane()
            val build = HTMLEditorKitBuilder().build()
            editor.editorKit = build
            chat = JButton("Ask")
            chat.addActionListener {
                if (prompt.text.isNotEmpty() && systemPrompt.text.isNotEmpty()) {
                    editor.text = ""
                    sendTextToEndpoint()
                } else {
                    JOptionPane.showMessageDialog(
                        prompt,
                        "Text area or system prompt is empty!",
                        "Warning",
                        JOptionPane.WARNING_MESSAGE
                    )
                }
            }
            val questionPanel = JPanel(BorderLayout())
            questionPanel.add(JLabel("Chat with GPT"), BorderLayout.NORTH)
            questionPanel.add(modelSelector, BorderLayout.NORTH)
            questionPanel.add(
                JBScrollPane(prompt, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER),
                BorderLayout.CENTER
            )
            questionPanel.add(systemPrompt, BorderLayout.SOUTH)
            add(questionPanel)

            val answerPanel = JPanel(BorderLayout())
            answerPanel.add(chat, BorderLayout.NORTH)
            scrollPane = JBScrollPane(editor, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER)
            answerPanel.add(scrollPane, BorderLayout.CENTER)
            add(answerPanel)
        }
    }


    private fun fetchModels() {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val url = URI.create("http://localhost:1234/v1/models").toURL()
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val objectMapper = Gson()
                    val jsonObject = objectMapper.fromJson(response, JsonObject::class.java)
                    val models = jsonObject.getAsJsonArray("data")
                        .mapNotNull { it.asJsonObject["id"]?.asString }

                    SwingUtilities.invokeLater {
                        modelSelector.removeAllItems()
                        models.forEach { modelSelector.addItem(it) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}