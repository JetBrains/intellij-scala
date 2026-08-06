package org.jetbrains.plugins.scala.conversion

import com.intellij.application.options.CodeStyle
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent, CommonDataKeys, PlatformCoreDataKeys}
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile, PsiJavaFile}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.conversion.ConvertJavaToScalaAction.convertToScalaFile
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.executeWriteActionCommand
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups

class ConvertJavaToScalaAction extends AnAction(
  ScalaConversionBundle.message("convert.java.to.scala.action.text"),
  ScalaConversionBundle.message("convert.java.to.scala.action.description"),
  /* icon = */ null
) {

  override def update(e: AnActionEvent): Unit = {
    val presentation = e.getPresentation

    try {
      val elements = getElementsFromContext(e)
      val allElementsOkForConversion = elements.forall(isElementOkForConversion)
      presentation.setEnabledAndVisible(allElementsOkForConversion)
    }
    catch {
      case c: ControlFlowException =>
        throw c
      case _: Exception =>
        presentation.setEnabledAndVisible(false)
    }
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

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
              val scalaFile = convertToScalaFile(javaFile)
              scalaFile.foreach(_.navigate(true))
            }(javaFile.getProject)
          }
        case _ =>
      }
    }
  }
}

object ConvertJavaToScalaAction {

  private[conversion]
  def convertToScalaFile(javaFile: PsiJavaFile): Option[PsiFile] = {
    val project = javaFile.getProject

    val directory = javaFile.getContainingDirectory
    val javaFilenameWithoutExtension = javaFile.getName.substring(0, javaFile.getName.length - 5)
    val scalaFileName: String = javaFilenameWithoutExtension + ".scala"

    val existingScalaFile: VirtualFile = directory.getVirtualFile.findChild(scalaFileName)
    if (existingScalaFile != null) {
      showFileAlreadyExistsNotification(project, scalaFileName)
      return None
    }

    val scalaFile = directory.createFile(scalaFileName)

    convertToScalaFile(javaFile, scalaFile)

    Some(scalaFile)
  }

  private[conversion]
  def convertToScalaFile(
    javaFile: PsiJavaFile,
    targetScalaFile: PsiFile
  ): Unit = {
    val project = javaFile.getProject
    val scalaFileText = JavaToScala.convertPsiToText(javaFile).trim
    updateDocumentTextAndCommit(targetScalaFile, scalaFileText)
    ConverterUtil.cleanCode(targetScalaFile, project, 0, targetScalaFile.getTextLength)
    withoutModifiedSettingsForConversion(project) {
      CodeStyleManager.getInstance(project).reformatText(targetScalaFile, 0, targetScalaFile.getTextLength)
    }
  }

  private def showFileAlreadyExistsNotification(project: Project, scalaFileName: String): Unit =
    ScalaNotificationGroups.javaToScalaConverter
      .createNotification(
        ScalaConversionBundle.message("cannot.create.file"),
        ScalaConversionBundle.message("file.already.exists", scalaFileName),
        NotificationType.ERROR
      )
      .notify(project)

  private def updateDocumentTextAndCommit(scalaFile: PsiFile, convertedScalaText: String): Unit = {
    val project = scalaFile.getProject
    val document = PsiDocumentManager.getInstance(project).getDocument(scalaFile)
    document.insertString(0, convertedScalaText)
    document.commit(project)
  }

  private def withoutModifiedSettingsForConversion(project: Project)(body: => Unit): Unit = {
    val codeStyle = CodeStyle.getSettings(project)
    val settings = codeStyle.getCommonSettings(ScalaLanguage.INSTANCE)
    val scalaSettings = codeStyle.getCustomSettings(classOf[ScalaCodeStyleSettings])

    val keep_blank_lines_in_code = settings.KEEP_BLANK_LINES_IN_CODE
    val keep_blank_lines_in_declarations = settings.KEEP_BLANK_LINES_IN_DECLARATIONS
    val keep_blank_lines_before_rbrace = settings.KEEP_BLANK_LINES_BEFORE_RBRACE
    val new_line_after_case_clause_arrow = scalaSettings.NEW_LINE_AFTER_CASE_CLAUSE_ARROW_WHEN_MULTILINE_BODY

    settings.KEEP_BLANK_LINES_IN_CODE = 0
    settings.KEEP_BLANK_LINES_IN_DECLARATIONS = 0
    settings.KEEP_BLANK_LINES_BEFORE_RBRACE = 0
    scalaSettings.NEW_LINE_AFTER_CASE_CLAUSE_ARROW_WHEN_MULTILINE_BODY = true

    try {
      body
    } finally {
      settings.KEEP_BLANK_LINES_IN_CODE = keep_blank_lines_in_code
      settings.KEEP_BLANK_LINES_IN_DECLARATIONS = keep_blank_lines_in_declarations
      settings.KEEP_BLANK_LINES_BEFORE_RBRACE = keep_blank_lines_before_rbrace
      scalaSettings.NEW_LINE_AFTER_CASE_CLAUSE_ARROW_WHEN_MULTILINE_BODY = new_line_after_case_clause_arrow
    }
  }
}
