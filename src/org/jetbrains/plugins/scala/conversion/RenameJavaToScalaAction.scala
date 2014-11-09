package org.jetbrains.plugins.scala
package conversion

import com.intellij.notification.{NotificationDisplayType, NotificationType}
import com.intellij.openapi.actionSystem.{CommonDataKeys, AnAction, AnActionEvent, LangDataKeys}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.codeStyle.{CodeStyleManager, CodeStyleSettingsManager}
import com.intellij.psi.{PsiDocumentManager, PsiJavaFile}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.util.{NotificationUtil, ScalaUtils}
import org.jetbrains.plugins.scala.project._

/**
 * @author Alexander Podkhalyuzin
 */
class RenameJavaToScalaAction extends AnAction {
  override def update(e: AnActionEvent) {
    val presentation = e.getPresentation
    def enable() {
      presentation.setEnabled(true)
      presentation.setVisible(true)
    }
    def disable() {
      presentation.setEnabled(false)
      presentation.setVisible(false)
    }
    try {
      var elements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(e.getDataContext)
      if (elements == null) {
        val file = CommonDataKeys.PSI_FILE.getData(e.getDataContext)
        if (file != null) elements = Array(file)
        else elements = Array.empty
      }
      for (element <- elements) {
        element.getContainingFile match {
          case j: PsiJavaFile if j.isInScalaModule =>
            val dir = j.getContainingDirectory
            if (!dir.isWritable) {
              disable()
              return
            }
          case _ =>
            disable()
            return
        }
      }
      enable()
    }
    catch {
      case e: Exception => disable()
    }

  }

  def actionPerformed(e: AnActionEvent) {
    var elements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(e.getDataContext)
    if (elements == null) {
      val file = CommonDataKeys.PSI_FILE.getData(e.getDataContext)
      if (file != null) elements = Array(file)
      else elements = Array.empty
    }
    for (element <- elements) {
      element.getContainingFile match {
        case jFile: PsiJavaFile if jFile.isInScalaModule =>
          val dir = jFile.getContainingDirectory
          if (dir.isWritable) {
            ScalaUtils.runWriteAction(new Runnable {
              def run() {
                val directory = jFile.getContainingDirectory
                val name = jFile.getName.substring(0, jFile.getName.length - 5)
                val nameWithExtension: String = name + ".scala"
                val existingFile: VirtualFile = directory.getVirtualFile.findChild(nameWithExtension)
                if (existingFile != null) {
                  NotificationUtil.builder(directory.getProject, s"File $nameWithExtension already exists").
                    setDisplayType(NotificationDisplayType.BALLOON).
                    setNotificationType(NotificationType.ERROR).
                    setGroup("rename.java.to.scala").
                    setTitle("Cannot create file").
                    show()
                  return
                }
                val file = directory.createFile(name + ".scala")
                val newText = JavaToScala.convertPsiToText(jFile).trim
                val document = PsiDocumentManager.getInstance(file.getProject).getDocument(file)
                document.insertString(0, newText)
                PsiDocumentManager.getInstance(file.getProject).commitDocument(document)
                val manager: CodeStyleManager = CodeStyleManager.getInstance(file.getProject)
                val settings = CodeStyleSettingsManager.getSettings(file.getProject).getCommonSettings(ScalaFileType.SCALA_LANGUAGE)
                val keep_blank_lines_in_code = settings.KEEP_BLANK_LINES_IN_CODE
                val keep_blank_lines_in_declarations = settings.KEEP_BLANK_LINES_IN_DECLARATIONS
                val keep_blank_lines_before_rbrace = settings.KEEP_BLANK_LINES_BEFORE_RBRACE
                settings.KEEP_BLANK_LINES_IN_CODE = 0
                settings.KEEP_BLANK_LINES_IN_DECLARATIONS = 0
                settings.KEEP_BLANK_LINES_BEFORE_RBRACE = 0
                try {
                  manager.reformatText(file, 0, file.getTextLength)
                } finally {
                  settings.KEEP_BLANK_LINES_IN_CODE = keep_blank_lines_in_code
                  settings.KEEP_BLANK_LINES_IN_DECLARATIONS = keep_blank_lines_in_declarations
                  settings.KEEP_BLANK_LINES_BEFORE_RBRACE = keep_blank_lines_before_rbrace
                }
                file.navigate(true)
              }
            }, jFile.getProject, "Convert to Scala")
          }
        case _ =>
      }
    }
  }
}