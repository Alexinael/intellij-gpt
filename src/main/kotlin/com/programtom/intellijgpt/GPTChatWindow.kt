package com.programtom.intellijgpt

import com.google.gson.Gson
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
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
import java.awt.Component
import java.awt.GridLayout
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import javax.swing.*
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED


private const val model = "lmstudio-community/Meta-Llama-3.1-8B-Instruct-GGUF/Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf"
private const val server: String = "localhost"

@Service(Service.Level.PROJECT)
class GPTChatWindow {


    private var prompt: JTextArea
    private var chat: JButton
    private var editor: JTextPane
    private var systemPrompt: JTextField
    val content: JPanel

    private fun sendTextToEndpoint() {
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
                        ), ChatRequest.Message("user", prompt.text)
                    )
                    )
            chatRequest.stream = true
            chatRequest.model =
                model

            os.write(objectMapper.toJson(chatRequest).encodeToByteArray())
            os.flush()

            val flavour: MarkdownFlavourDescriptor = GFMFlavourDescriptor()
            val parsedTree = MarkdownParser(flavour)


            val responseCode = connection.getResponseCode()
            if (responseCode == HttpStatus.SC_OK) {

                val sb = StringBuilder()
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    var line: String?
                    while (true) {

                        line = reader.readLine()

                        if (line == null || line.contains("[DONE]")) {


                            break
                        }
                        val replace = line.replace("data: ", "")
                        val chatResponse = objectMapper.fromJson(replace, ChatResponse::class.java)
                        if (chatResponse?.choices != null && chatResponse.choices!!.isNotEmpty()) {
                            if (chatResponse.choices!!.first().delta?.content != null) {

                                ApplicationManager.getApplication().runReadAction {

                                    sb.append(chatResponse.choices!!.first().delta?.content)
                                    val tree = parsedTree.buildMarkdownTreeFromString(sb.toString())
                                    val html = HtmlGenerator(sb.toString(), tree, flavour, false).generateHtml()
                                    editor.text = html
                                }
                            }
                        }
                    }
                }

            }


        }
    }

    init {
        this.content = JPanel().apply {

            layout = GridLayout(2, 1)
            prompt = JTextArea("Could you give me minimal code to create a window inside visual studio code")
            systemPrompt = JTextField("Helpful Coding Assistant")
            systemPrompt.toolTipText = "System Prompt"
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
            questionPanel.add(scrollPane(prompt), BorderLayout.CENTER)
            questionPanel.add(systemPrompt, BorderLayout.SOUTH)
            add(questionPanel)

            val answerPanel = JPanel(BorderLayout())
            answerPanel.add(chat, BorderLayout.NORTH)

            answerPanel.add(scrollPane(editor), BorderLayout.CENTER)
            add(answerPanel)
        }
    }

    private fun scrollPane(code: Component): JBScrollPane {
        val scroll = JBScrollPane(code, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER)
        return scroll
    }
}
