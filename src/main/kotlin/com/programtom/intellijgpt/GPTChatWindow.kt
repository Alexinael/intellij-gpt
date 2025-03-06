package com.programtom.intellijgpt

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.editor.actions.PasteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.programtom.intellijgpt.models.ChatRequest
import com.programtom.intellijgpt.models.ChatResponse
import org.apache.http.HttpStatus
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import javax.swing.*

private const val server: String = "localhost"
private const val JUMP_SCROLL_SKIPS = 70

@Service(Service.Level.PROJECT)
class GPTChatWindow {
    private fun setupKeyBindings() {
        val inputMap = prompt.inputMap
        val actionMap = prompt.actionMap
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "send")
        actionMap.put("send", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                sendTextToEndpoint()
            }
        })
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clear")
        actionMap.put("clear", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                prompt.text = ""
            }
        })
    }
    private fun sendTextToEndpoint() {
        stop.isEnabled = true
        chat.isEnabled = false

        var currentFileName = "Unknown File"
        val project = com.intellij.openapi.project.ProjectManager.getInstance().openProjects.firstOrNull()
            ?: return
        val editorManager = FileEditorManager.getInstance(project)
        val files = editorManager.selectedFiles
        if (files.isNotEmpty()) {
            currentFileName = files[0].name
        }

        chatWorker = object : SwingWorker<Void, String>() {
            override fun doInBackground(): Void? {
                try {
                    val objectMapper = Gson()
                    val urlStr = "http://$server:1234/v1/chat/completions"
                    val url = URI.create(urlStr).toURL()
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.doOutput = true
                    val os = connection.outputStream

                    val chatRequest = ChatRequest().apply {
                        messages = listOf(
                            ChatRequest.Message("system", systemPrompt.text),
                            ChatRequest.Message("user", "Текущий файл: $currentFileName\n\n${prompt.text}")
                        )
                        stream = true
                        model = modelSelector.selectedItem as String
                    }

                    os.write(objectMapper.toJson(chatRequest).encodeToByteArray())
                    os.flush()

                    if (connection.responseCode == HttpStatus.SC_OK) {
                        val reader = BufferedReader(InputStreamReader(connection.inputStream))
                        var line: String?
                        val sb = StringBuilder()
                        while (reader.readLine().also { line = it } != null) {
                            if (line!!.contains("[DONE]")) break
                            val chatResponse = objectMapper.fromJson(line!!.replace("data: ", ""), ChatResponse::class.java)
                            chatResponse?.choices?.firstOrNull()?.delta?.content?.let {
                                sb.append(it)
                                publish(sb.toString())
                            }
                        }
                        reader.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return null
            }

            override fun process(chunks: List<String>) {
                editor.text = chunks.last()
                scrollPane.verticalScrollBar.value = scrollPane.verticalScrollBar.maximum
            }

            override fun done() {
                stop.isEnabled = false
                chat.isEnabled = true
            }
        }
        chatWorker?.execute()
    }
    private var prompt: JTextArea
    private var chat: JButton
    private var stop: JButton
    private var editor: JTextPane
    private var systemPrompt: JTextField
    private var scrollPane: JBScrollPane
    private lateinit var modelSelector: JComboBox<String>
    private var chatWorker: SwingWorker<Void, String>? = null
    val content: JPanel

    private fun fetchModels() {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val url = URI.create("http://localhost:1234/v1/models").toURL()
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()
                    val objectMapper = Gson()
                    val jsonObject = objectMapper.fromJson(response, JsonObject::class.java)
                    val models = jsonObject.getAsJsonArray("data").mapNotNull { it.asJsonObject["id"]?.asString }
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

    init {
        content = JPanel(BorderLayout())
        systemPrompt = JTextField("Helpful Coding Assistant").apply {
            toolTipText = "System Prompt"
        }
        modelSelector = JComboBox<String>().apply {
            toolTipText = "Select AI Model"
            fetchModels()
        }

        prompt = JTextArea().apply {
            lineWrap = true
            border = BorderFactory.createLineBorder(Color.GRAY, 1, true)
            font = Font("SansSerif", Font.PLAIN, 14)
        }

        chat = JButton("Ask").apply {
            addActionListener { sendTextToEndpoint() }
        }
        stop = JButton("Stop").apply {
            isEnabled = false
            addActionListener { chatWorker?.cancel(true) }
        }

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(chat)
            add(stop)
        }

        val questionPanel = JPanel(BorderLayout()).apply {
            add(JLabel("Chat with GPT"), BorderLayout.NORTH)
            add(modelSelector, BorderLayout.NORTH)
            add(JBScrollPane(prompt, javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
            add(systemPrompt, BorderLayout.SOUTH)
        }

        editor = JTextPane().apply {
            editorKit = HTMLEditorKitBuilder().build()
        }
        scrollPane = JBScrollPane(editor, javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)

        content.add(questionPanel, BorderLayout.NORTH)
        content.add(scrollPane, BorderLayout.CENTER)

        setupKeyBindings()
    }
}
