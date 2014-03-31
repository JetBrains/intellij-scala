package org.jetbrains.plugins.scala
package chat

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.psi.PsiFile
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.util.Ref
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.google.code.chatterbotapi.ChatterBotSession
import com.intellij.util.DocumentUtil
import scala.util.Random

/**
 * @author Alefas
 * @since 31/03/14.
 */
class ChatEnterHandler extends EnterHandlerDelegate {
  override def postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): Result = {
    if (!ChatProjectComponent.isOk || ChatProjectComponent.fileName != file.getName) return Result.Continue
    val document: Document = editor.getDocument
    val split: Array[String] = document.getText.trim.split('\n')
    split.lastOption match {
      case Some(text) if text.startsWith("you>") =>
        val processedString = text.substring(4)
        val session: ChatterBotSession = ChatProjectComponent.getSession(document)
        val result: String = try {
          session.think(processedString)
        } catch {
          case e: Throwable => "Service is temporary unavailable..."
        }
        val r: String = s"idea>"
        document.insertString(document.getTextLength, r)
        editor.getCaretModel.moveToOffset(document.getTextLength)
        document.setReadOnly(true)
        new Thread(new Runnable {
          override def run(): Unit = {
            def process(body: => Unit) {
              extensions.invokeAndWait {
                extensions.inWriteAction {
                  DocumentUtil.writeInRunUndoTransparentAction(new Runnable {
                    override def run(): Unit = {
                      body
                    }
                  })
                }
              }
            }

            def connect() {
              var connection = false

              process {
                document.setReadOnly(false)
                if (document.getText.count(_ == '>') <= 2) {
                  connection = true
                  document.insertString(document.getTextLength, s"Connecting to the interlocutor...")
                }
                document.setReadOnly(true)
              }

              if (connection) {
                val cycles = Random.nextInt(5) + 5
                for (i <- 1 to cycles) {
                  Thread.sleep(1000)
                  process {
                    document.setReadOnly(false)
                    document.insertString(document.getTextLength, s".")
                    document.setReadOnly(true)
                  }
                }

                process {
                  document.setReadOnly(false)
                  val index = document.getText.lastIndexOf("idea>") + 5
                  document.deleteString(index, document.getTextLength)
                  document.setReadOnly(true)
                }
              }
            }

            connect()

            def typing() {
              process {
                document.setReadOnly(false)
                document.insertString(document.getTextLength, s"Typing...")
                document.setReadOnly(true)
              }

              val cycles = 2 + (result.length * 60) / 400
              for (i <- 1 to cycles) {
                Thread.sleep(1000)
                process {
                  document.setReadOnly(false)
                  document.insertString(document.getTextLength, s".")
                  document.setReadOnly(true)
                }
              }

              process {
                document.setReadOnly(false)
                val index = document.getText.lastIndexOf("idea>") + 5
                document.deleteString(index, document.getTextLength)
                document.setReadOnly(true)
              }
            }

            typing()

            process {
              document.setReadOnly(false)
              document.insertString(document.getTextLength, s"$result\nyou>")
              editor.getCaretModel.moveToOffset(document.getTextLength)
            }
          }
        }).start()
        Result.Stop
      case _ => Result.Continue
    }
  }

  override def preprocessEnter(file: PsiFile, editor: Editor, caretOffset: Ref[Integer],
                               caretAdvance: Ref[Integer], dataContext: DataContext, originalHandler: EditorActionHandler): Result = {
    if (!ChatProjectComponent.isOk || ChatProjectComponent.fileName != file.getName) return Result.Continue
    val offset: Int = caretOffset.get()
    if (editor.getDocument.getTextLength != offset) {
      caretOffset.set(editor.getDocument.getTextLength)
    }
    Result.Continue
  }
}
