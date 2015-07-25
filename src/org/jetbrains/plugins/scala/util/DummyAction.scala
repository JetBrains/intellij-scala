package org.jetbrains.plugins.scala.util

import java.io.{EOFException, ObjectInputStream, FileInputStream}

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugin.scala.util.MacroExpansion
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile


// Stub for lazy people, who want to test some stuff on a key press
// but too lazy to implement new action/modify existing one.
class DummyAction extends AnAction {

  override def actionPerformed(e: AnActionEvent): Unit = {
    val editor = FileEditorManager.getInstance(e.getProject).getSelectedTextEditor
    if (editor == null) return

    val psiFile: PsiFile = PsiDocumentManager.getInstance(e.getProject).getPsiFile(editor.getDocument)
    psiFile match {
      case file: ScalaFile =>
      case _ =>
    }

    val expansions = deserializeExpansions(e)
    ""
  }

  def deserializeExpansions(event: AnActionEvent): Seq[MacroExpansion] = {
    val fs = new FileInputStream(PathManager.getSystemPath + s"/expansion-${event.getProject.getName}")
    val os = new ObjectInputStream(fs)
    val res = scala.collection.mutable.ListBuffer[MacroExpansion]()
    while (true) {
      try {
        res += os.readObject().asInstanceOf[MacroExpansion]
      } catch {
        case _:EOFException => return res
      }
    }
    res
  }
}
