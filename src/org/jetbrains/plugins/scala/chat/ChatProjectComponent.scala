package org.jetbrains.plugins.scala
package chat

import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import java.util.{Calendar, Date}
import org.jetbrains.plugins.scala.util.NotificationUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileChooser.{FileChooserDescriptor, FileChooser}
import com.intellij.openapi.fileEditor.{FileEditorManagerEvent, FileEditorManagerListener, FileEditorManager}
import org.jetbrains.plugins.scala.extensions
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.util.DocumentUtil
import com.intellij.openapi.util.Key
import com.google.code.chatterbotapi.{ChatterBotSession, ChatterBotFactory, ChatterBotType}

/**
 * @author Alefas
 * @since 31/03/14.
 */
class ChatProjectComponent(project: Project) extends ProjectComponent {
  import ChatProjectComponent._

  override def disposeComponent(): Unit = {}
  override def initComponent(): Unit = {}
  override def projectClosed(): Unit = {}
  override def projectOpened(): Unit = {
    if (isOk) {
      NotificationUtil.builder(project,
        s"""Are you ready to start chat now? It's available in any file with name $fileName.<br>
          |
          |<a href="ftp://start-chat">Let's choose directory for this file!</a><br>""".stripMargin).
        setGroup("ChatComponent").setTitle("IntelliJ IDEA is ready to answer for any of your question.").
        setNotificationType(NotificationType.INFORMATION).setHandler {
        case "start-chat" =>
          val dir = FileChooser.chooseFile(new FileChooserDescriptor(false, true, false, false, false, false), project, null)
          if (dir != null) {
            val file = extensions.inWriteAction {
              dir.findOrCreateChildData(null, fileName)
            }
            if (file != null) {
              FileEditorManager.getInstance(project).openFile(file, true)
            }
          }
        case _ =>
      }.show()
    }

    project.getMessageBus.connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener {
      override def selectionChanged(event: FileEditorManagerEvent): Unit = {}

      override def fileOpened(source: FileEditorManager, file: VirtualFile): Unit = {
        if (isOk && file.getName == fileName) {
          val editor: Editor = FileEditorManager.getInstance(project).getSelectedTextEditor
          if (editor != null) {
            val document = editor.getDocument
            DocumentUtil.writeInRunUndoTransparentAction(new Runnable {
              override def run(): Unit = {
                val text: String = document.getText
                if (!text.endsWith("you>")) {
                  extensions.inWriteAction {
                    val you: String = if (text.length == 0 || text.endsWith("\n")) "you>" else "\nyou"
                    document.insertString(text.length, you)
                    editor.getCaretModel.moveToOffset(text.length + you.length)
                  }
                }
              }
            })
          }
        }
      }

      override def fileClosed(source: FileEditorManager, file: VirtualFile): Unit = {}
    })
  }

  override def getComponentName: String = "ChatProjectComponent"
}

object ChatProjectComponent {
  val fileName = "chat-with-idea.txt"

  def isOk: Boolean = {
    val calendar: Calendar = Calendar.getInstance()
    calendar.setTime(new Date())
    calendar.get(Calendar.DAY_OF_YEAR) == 91
  }

  val SESSION_KEY: Key[ChatterBotSession] = Key.create("chat.session.key")

  def getSession(document: Document): ChatterBotSession = {
    var session: ChatterBotSession = document.getUserData(SESSION_KEY)
    if (session == null) {
      val factory = new ChatterBotFactory()

      val bot = factory.create(ChatterBotType.PANDORABOTS, "b0dafd24ee35a477")
      session = bot.createSession()
      document.putUserData(SESSION_KEY, session)
    }
    session
  }
}
