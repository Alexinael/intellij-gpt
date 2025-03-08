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
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBSplitter
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
import javax.swing.text.html.HTMLEditorKit

private const val server: String = "localhost"
private const val JUMP_SCROLL_SKIPS = 70

@Service(Service.Level.PROJECT)
class GPTChatWindow {
    fun addContextMenuToEditor(project: Project) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val selectionModel: SelectionModel = editor.selectionModel

        editor.contentComponent.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseReleased(e: java.awt.event.MouseEvent) {
                if (e.isPopupTrigger) {
                    val existingMenu = editor.contentComponent.componentPopupMenu ?: JPopupMenu()

                    // Добавляем только если их еще нет
                    if (!existingMenu.components.any { (it as? JMenuItem)?.text == "Send to AI-ASSIST" }) {
                        existingMenu.add(JMenuItem("Send to AI-ASSIST").apply {
                            addActionListener {
                                ToolWindowManager.getInstance(project).getToolWindow("AI-ASSIST")?.activate(null)
                            }
                        })
                    }

                    if (!existingMenu.components.any { (it as? JMenuItem)?.text == "Отправить в AI-ASSIST" }) {
                        existingMenu.add(JMenuItem("Отправить в AI-ASSIST").apply {
                            addActionListener {
                                val selectedText = selectionModel.selectedText ?: return@addActionListener
                                ToolWindowManager.getInstance(project).getToolWindow("AI-ASSIST")?.activate(null)
                                prompt.text = selectedText
                            }
                        })
                    }

                    // Добавляем дополнительные пункты
                    if (!existingMenu.components.any { (it as? JMenuItem)?.text == "Дополнительный пункт 1" }) {
                        existingMenu.add(JMenuItem("Дополнительный пункт 1").apply {
                            addActionListener { println("Выбран пункт 1") }
                        })
                    }

                    if (!existingMenu.components.any { (it as? JMenuItem)?.text == "Дополнительный пункт 2" }) {
                        existingMenu.add(JMenuItem("Дополнительный пункт 2").apply {
                            addActionListener { println("Выбран пункт 2") }
                        })
                    }

                    // НЕ перезаписываем стандартное меню, а добавляем в него наши пункты
                    if (false && editor.contentComponent.componentPopupMenu == null) {
                        editor.contentComponent.componentPopupMenu = existingMenu
                    } else {
                        editor.contentComponent.componentPopupMenu?.components?.forEach {
                            existingMenu.add(it)
                        }
                    }

                    existingMenu.show(e.component, e.x, e.y)
                }
            }
        })


//        editor.contentComponent.addMouseListener(object : java.awt.event.MouseAdapter() {
//            override fun mouseReleased(e: java.awt.event.MouseEvent) {
//                if (e.isPopupTrigger) {
//                    val existingMenu = editor.contentComponent.componentPopupMenu ?: JPopupMenu()
//                    existingMenu.add(JMenuItem("send to AI-ASSIST").apply {
//                        addActionListener {
//                            ToolWindowManager.getInstance(project).getToolWindow("AI-ASSIST")?.activate(null)
//                        }
//                    })
////                    if (!existingMenu.components.any { (it as? JMenuItem)?.text == "Отправить в AI-ASSIST" }) {
////                        existingMenu.add(JMenuItem("Отправить в AI-ASSIST").apply {
////                            addActionListener {
////                                val selectedText = selectionModel.selectedText ?: return@addActionListener
////                                ToolWindowManager.getInstance(project).getToolWindow("AI-ASSIST")?.activate(null)
////                                prompt.text = selectedText
////                            }
////                        })
////                    }
//                    existingMenu.show(e.component, e.x, e.y)
//                }
//            }
//        })
    }

    val htmlPane = javax.swing.JEditorPane("text/html", "").apply {
        editorKit = HTMLEditorKit()
        isEditable = false
    }

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
        var currentFileExtension = ""
        val project = com.intellij.openapi.project.ProjectManager.getInstance().openProjects.firstOrNull()
            ?: return
        val editorManager = FileEditorManager.getInstance(project)
        val files = editorManager.selectedFiles
        if (files.isNotEmpty()) {
            currentFileName = files[0].name
            currentFileExtension= files[0].extension ?: ""
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
                            ChatRequest.Message("system", systemPrompt.text +" [current '${currentFileName}' file has extension: '${currentFileExtension}'] "),
                            ChatRequest.Message("user", "(У меня открыт файл $currentFileName). ${prompt.text}")
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
                            if (isCancelled) break
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
                if (chunks.isEmpty()) return // Предотвращаем ошибку, если список пуст

                val formattedHtml = "<html><body style='font-family: sans-serif;'>" +
                        chunks.last().replace("\n", "<br>") +
                        "</body></html>"

                SwingUtilities.invokeLater {
                    htmlPane.editorKit = HTMLEditorKit() // Убеждаемся, что HTML рендерится корректно
                    htmlPane.text = formattedHtml
                    scrollPane.verticalScrollBar.value = scrollPane.verticalScrollBar.maximum
                }
            }

//            override fun process(chunks: List<String>) {
////                editor.text = chunks.last()
////                scrollPane.verticalScrollBar.value = scrollPane.verticalScrollBar.maximum
//                val formattedHtml = "<html><body style='font-family: sans-serif;'>" +
//                        chunks.last().replace("\n", "<br>") +
//                        "</body></html>"
//                SwingUtilities.invokeLater {
//                    htmlPane.text = "" // Очищаем перед обновлением
//                    htmlPane.text = formattedHtml
//                    scrollPane.verticalScrollBar.value = scrollPane.verticalScrollBar.maximum
//                }
//            }

            override fun done() {
                stop.isEnabled = false
                chat.isEnabled = true
            }
        }
        chatWorker?.execute()
        stop.addActionListener {
            chatWorker?.cancel(true) // Останавливаем поток
            stop.isEnabled = false
            chat.isEnabled = true
        }
    }


    private var prompt: JTextArea
    private var chat: JButton
    private var stop: JButton
    private var editor: JTextPane
    private var systemPrompt: JTextField
    private var scrollPane: JBScrollPane
    private lateinit var modelSelector: JComboBox<String>
//    private lateinit var htmlPane : javax.swing.JEditorPane

    private var chatWorker: SwingWorker<Void, String>? = null
    val content: JPanel

    private fun fetchModels() {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val url = URI.create("http://$server:1234/v1/models").toURL()
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

//        ApplicationManager.getApplication().invokeLater {
//            val project = com.intellij.openapi.project.ProjectManager.getInstance().openProjects.firstOrNull()
//            if (project != null) {
//                addContextMenuToEditor(project)
//            }
//        }




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

        val questionPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS) // Вертикальное размещение компонентов

            add(JLabel("Chat with GPT"))
            add(modelSelector)
            add(systemPrompt)
            add(JBScrollPane(prompt))
            add(buttonPanel) // ASK и STOP теперь точно видны
        }



        editor = JTextPane().apply {
            editorKit = HTMLEditorKitBuilder().build()
        }

//        scrollPane = JBScrollPane(editor, javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
//        scrollPane = JBScrollPane(htmlPane, javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
//        res = JBSplitter()
//        content.add(questionPanel, BorderLayout.NORTH)
//        content.add(scrollPane, BorderLayout.CENTER)

        scrollPane = JBScrollPane(htmlPane, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER)
// Добавляем сплиттер для регулирования высоты полей


        val splitter = JSplitPane(JSplitPane.VERTICAL_SPLIT, JBScrollPane(prompt), scrollPane).apply {
            resizeWeight = 0.5 // Делит экран 50/50 при старте
            isOneTouchExpandable = true // Показывает кнопку свернуть-развернуть
        }
//        content.add(questionPanel, BorderLayout.NORTH)
//        content.add(splitter, BorderLayout.CENTER)
        // Добавляем HTML-панель
        content.add(questionPanel, BorderLayout.NORTH)
        content.add(splitter, BorderLayout.CENTER)


//        content.add(scrollPane, BorderLayout.SOUTH)

        val project = com.intellij.openapi.project.ProjectManager.getInstance().openProjects.firstOrNull()
        val editorComponent = project?.let { FileEditorManager.getInstance(it).selectedTextEditor }

        if (editorComponent != null) {
            editorComponent.contentComponent.addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseReleased(e: java.awt.event.MouseEvent) {
                    if (e.isPopupTrigger) {
                        val actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance()
                        val editorPopupMenu = actionManager.getAction("EditorPopupMenu") as? com.intellij.openapi.actionSystem.DefaultActionGroup ?: return

                        // Добавляем пункты, если их еще нет
                        if (!editorPopupMenu.getChildren(null).any { it.templateText == "Оптимизировать в AI-ASSIST" }) {
                            editorPopupMenu.add(object : com.intellij.openapi.actionSystem.AnAction("Оптимизировать в AI-ASSIST") {
                                override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                                    val selectedText = editorComponent.selectionModel.selectedText ?: return
                                    ToolWindowManager.getInstance(project).getToolWindow("AI-ASSIST")?.activate(null)
                                    prompt.text = "Оптимизировать это участок кода: \n"+selectedText
                                    SwingUtilities.invokeLater {
                                        prompt.text = selectedText
                                    }
                                }
                            })
                        }

                        if (!editorPopupMenu.getChildren(null).any { it.templateText == "Объяснить в AI-ASSIST" }) {
                            editorPopupMenu.add(object : com.intellij.openapi.actionSystem.AnAction("Объяснить в AI-ASSIST") {
                                override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                                    val selectedText = editorComponent.selectionModel.selectedText ?: return
                                    ToolWindowManager.getInstance(project).getToolWindow("AI-ASSIST")?.activate(null)
                                    prompt.text = selectedText
                                    SwingUtilities.invokeLater {
                                        prompt.text = "Объясни эту часть кода:\n ${selectedText}"
//                                        htmlPane.text = "<html><body><p>$selectedText</p></body></html>"
                                    }
                                }
                            })
                        }

//                        // Добавляем дополнительные пункты
//                        if (!editorPopupMenu.getChildren(null).any { it.templateText == "Дополнительный пункт 1" }) {
//                            editorPopupMenu.add(object : com.intellij.openapi.actionSystem.AnAction("Дополнительный пункт 1") {
//                                override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
//                                    println("Выбран пункт 1")
//                                }
//                            })
//                        }

                    }
                }
            })
        }





        setupKeyBindings()
    }
    // **Используем htmlPane для отображения HTML-ответов**


}