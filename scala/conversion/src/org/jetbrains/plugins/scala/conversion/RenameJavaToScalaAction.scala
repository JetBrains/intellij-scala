package org.jetbrains.plugins.scala.conversion

import com.intellij.application.options.CodeStyle
import com.intellij.notification.{NotificationDisplayType, NotificationType}
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys, PlatformCoreDataKeys}
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile, PsiJavaFile}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.executeWriteActionCommand
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.util.NotificationUtil

//TODO: it's not "rename" it's "convert", rename the action!
class RenameJavaToScalaAction extends AnAction(
  ScalaConversionBundle.message("convert.java.to.scala.action.text"),
  ScalaConversionBundle.message("convert.java.to.scala.action.description"),
  /* icon = */ null
) {

  override def update(e: AnActionEvent): Unit = {
    val presentation = e.getPresentation

    def setEnabled(value: Boolean): Unit = {
      presentation.setEnabled(value)
      presentation.setVisible(value)
    }

    try {
      val elements = getElementsFromContext(e)
      val allElementsOkForConversion = elements.forall(isElementOkForConversion)
      setEnabled(allElementsOkForConversion)
    }
    catch {
      case c: ControlFlowException =>
        throw c
      case _: Exception =>
        setEnabled(false)
    }
  }

  private def isElementOkForConversion(element: PsiElement): Boolean =
    element.getContainingFile match {
      case javaFile: PsiJavaFile if javaFile.isInScalaModule =>
        val dir = javaFile.getContainingDirectory
        dir.isWritable
      case _ =>
        false
    }

  private def getElementsFromContext(e: AnActionEvent): Array[PsiElement] = {
    val elements = PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.getData(e.getDataContext)
    if (elements != null) elements else {
      val file = CommonDataKeys.PSI_FILE.getData(e.getDataContext)
      if (file != null) Array(file)
      else Array.empty
    }
  }

  override def actionPerformed(e: AnActionEvent): Unit = {
    val elements = getElementsFromContext(e)
    for (element <- elements) {
      element.getContainingFile match {
        case javaFile: PsiJavaFile if javaFile.isInScalaModule =>
          val dir = javaFile.getContainingDirectory
          if (dir.isWritable) {
            executeWriteActionCommand(ScalaConversionBundle.message("convert.to.scala")) {
              convertToScalaFile(javaFile)
            }(javaFile.getProject)
          }
        case _ =>
      }
    }
  }

  private def convertToScalaFile(javaFile: PsiJavaFile): Unit = {
    val project = javaFile.getProject

    val directory = javaFile.getContainingDirectory
    val javaFilenameWithoutExtension = javaFile.getName.substring(0, javaFile.getName.length - 5)
    val scalaFileName: String = javaFilenameWithoutExtension + ".scala"

    val existingScalaFile: VirtualFile = directory.getVirtualFile.findChild(scalaFileName)
    if (existingScalaFile != null) {
      showFileAlreadyExistsNotification(project, scalaFileName)
      return
    }

    val scalaFileText = JavaToScala.convertPsiToText(javaFile).trim

    val scalaFile = directory.createFile(scalaFileName)
    updateDocumentTextAndCommit(scalaFile, scalaFileText)

    withoutKeepingBlankLines(project) {
      CodeStyleManager.getInstance(project).reformatText(scalaFile, 0, scalaFile.getTextLength)
    }

    scalaFile.navigate(true)
  }

  private def showFileAlreadyExistsNotification(project: Project, scalaFileName: String): Unit =  {
    NotificationUtil.builder(project, ScalaConversionBundle.message("file.already.exists", scalaFileName))
      .setDisplayType(NotificationDisplayType.BALLOON)
      .setNotificationType(NotificationType.ERROR)
      .setGroup("rename.java.to.scala")
      .setTitle(ScalaConversionBundle.message("cannot.create.file"))
      .show()
  }

  private def updateDocumentTextAndCommit(scalaFile: PsiFile, convertedScalaText: String): Unit = {
    val project = scalaFile.getProject
    val document = PsiDocumentManager.getInstance(project).getDocument(scalaFile)
    document.insertString(0, convertedScalaText)
    document.commit(project)
  }

  private def withoutKeepingBlankLines(project: Project)(body: => Unit): Unit = {
    val settings = CodeStyle.getSettings(project).getCommonSettings(ScalaLanguage.INSTANCE)
    val keep_blank_lines_in_code = settings.KEEP_BLANK_LINES_IN_CODE
    val keep_blank_lines_in_declarations = settings.KEEP_BLANK_LINES_IN_DECLARATIONS
    val keep_blank_lines_before_rbrace = settings.KEEP_BLANK_LINES_BEFORE_RBRACE
    settings.KEEP_BLANK_LINES_IN_CODE = 0
    settings.KEEP_BLANK_LINES_IN_DECLARATIONS = 0
    settings.KEEP_BLANK_LINES_BEFORE_RBRACE = 0
    try {
      body
    } finally {
      settings.KEEP_BLANK_LINES_IN_CODE = keep_blank_lines_in_code
      settings.KEEP_BLANK_LINES_IN_DECLARATIONS = keep_blank_lines_in_declarations
      settings.KEEP_BLANK_LINES_BEFORE_RBRACE = keep_blank_lines_before_rbrace
    }
  }
}
