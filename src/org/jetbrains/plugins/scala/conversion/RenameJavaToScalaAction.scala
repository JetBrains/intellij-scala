package org.jetbrains.plugins.scala
package conversion


import com.intellij.openapi.actionSystem.{AnActionEvent, DataConstants, AnAction}
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.{PsiDocumentManager, PsiJavaFile}
import util.ScalaUtils
/**
 * Created by IntelliJ IDEA.
 * User: Alexander
 * Date: 28.07.2009
 * Time: 20:13:48
 * To change this template use File | Settings | File Templates.
 */

class RenameJavaToScalaAction extends AnAction {
  override def update(e: AnActionEvent): Unit = {
    val presentation = e.getPresentation
    def enable {
      presentation.setEnabled(true)
      presentation.setVisible(true)
    }
    def disable {
      presentation.setEnabled(false)
      presentation.setVisible(false)
    }
    try {
      val dataContext = e.getDataContext
      val file = dataContext.getData(DataConstants.PSI_FILE)
      file match {
        case _: PsiJavaFile => enable
        case _ => disable
      }
    }
    catch {
      case e: Exception => disable
    }

  }

  def actionPerformed(e: AnActionEvent): Unit = {
    val file = e.getDataContext.getData(DataConstants.PSI_FILE)
    file match {
      case jFile: PsiJavaFile => {
        org.jetbrains.plugins.scala.util.ScalaUtils.runWriteAction(new Runnable {
          def run {
            val directory = jFile.getContainingDirectory
            val name = jFile.getName.substring(0, jFile.getName.length - 5)
            val file = directory.createFile(name + ".scala")
            val newText = JavaToScala.convertPsiToText(jFile)
            val document = PsiDocumentManager.getInstance(file.getProject).getDocument(file)
            document.insertString(0, newText)
            PsiDocumentManager.getInstance(file.getProject).commitDocument(document)
            //CodeStyleManager.getInstance(file.getProject).reformat(file)
          }
        }, jFile.getProject, "Convert Java to Scala")
      }
      case _ =>
    }
  }
}