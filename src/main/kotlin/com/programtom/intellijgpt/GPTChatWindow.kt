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

private const val JUMP_SCROLL_SKIPS = 70

@Service(Service.Level.PROJECT)
class GPTChatWindow {


    private var prompt: JTextArea
    private var chat: JButton
    private var editor: JTextPane
    private var systemPrompt: JTextField
    private var scrollPane: JBScrollPane
    val content: JPanel
//    val map = HashMap<String, String>()

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

//    private fun addCopy(generateHtml: String): String {
//        map.clear()
//        val sb = StringBuilder()
//        val split = generateHtml.split("\n")
//        val tempCode = StringBuilder()
//
//        for (i in 0 until (split.size)) {
//            if (tempCode.isEmpty()) {
//                if (split[i].contains("<code")) {
//                    tempCode.append(split[i].substring(split[i].lastIndexOf(">") + 1)).append("\n")
//                }
//            } else {
//                if (split[i].contains("</code>")) {
//
//                    val i1 = split[i].indexOf("</code>") + "</code>".length
//                    val s = split[i].substring(0, i1) + tempCode.toString() + split[i].substring(i1)
//
//                    val time = System.currentTimeMillis().toString()
//                    map[time] = s
//                    sb.append("<a href=\"").append(time).append("\">Copy</a>").append("\n")
//
//                    tempCode.clear()
//                    continue
//                }
//                if (tempCode.isNotEmpty()) {
//                    tempCode.append(split[i]).append("\n")
//                }
//            }
//
//            sb.append(split[i]).append("\n")
//        }
//        return sb.toString()
//    }

    init {
        this.content = JPanel().apply {

            layout = GridLayout(2, 1)
            prompt = JTextArea("")
            prompt.lineWrap = true
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

}
