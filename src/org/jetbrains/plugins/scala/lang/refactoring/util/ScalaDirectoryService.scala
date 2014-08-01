package org.jetbrains.plugins.scala
package lang.refactoring.util

import java.util.Properties

import com.intellij.ide.fileTemplates.impl.FileTemplateBase
import com.intellij.ide.fileTemplates.ui.CreateFromTemplateDialog
import com.intellij.ide.fileTemplates.{FileTemplate, FileTemplateManager, FileTemplateUtil}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.{PsiBundle, PsiClass, PsiDirectory, PsiElement}
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.actions.ScalaFileTemplateUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
* Pavel Fatin
*/
object ScalaDirectoryService {
  private final val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.lang.refactoring.move.ScalaDirectoryService")

  def createClassFromTemplate(@NotNull dir: PsiDirectory, name: String, templateName: String, askToDefineVariables: Boolean): PsiClass = {
    val template =
      if (ApplicationManager.getApplication.isUnitTestMode) templateForUnitTest(templateName, name)
      else FileTemplateManager.getInstance.getInternalTemplate(templateName)
    val defaultProperties = FileTemplateManager.getInstance.getDefaultProperties
    val properties = new Properties(defaultProperties)
    properties.setProperty(FileTemplate.ATTRIBUTE_NAME, name)
    val fileName: String = name
    val element: PsiElement = try {
      if (askToDefineVariables) new CreateFromTemplateDialog(dir.getProject, dir, template, null, properties).create
      else FileTemplateUtil.createFromTemplate(template, fileName, properties, dir)
    }
    catch {
      case e: IncorrectOperationException => throw e
      case e: Exception =>
        LOG.error(e)
        return null
    }
    val file = element.getContainingFile.asInstanceOf[ScalaFile]
    val classes = file.typeDefinitionsArray
    if (classes.length < 1) throw new IncorrectOperationException(getIncorrectTemplateMessage(templateName))
    classes(0)
  }

  private def getIncorrectTemplateMessage(templateName: String): String = {
    PsiBundle.message("psi.error.incorroect.class.template.message", FileTemplateManager.getInstance.internalTemplateToSubject(templateName), templateName)
  }

  private def templateForUnitTest(templateName: String, name: String): FileTemplate = {
    val kind = templateName match {
      case ScalaFileTemplateUtil.SCALA_CLASS => "class "
      case ScalaFileTemplateUtil.SCALA_TRAIT => "trait "
      case ScalaFileTemplateUtil.SCALA_OBJECT => "object "
      case _ => ""
    }
    val packageLine = "#if ((${PACKAGE_NAME} && ${PACKAGE_NAME} != \"\"))package ${PACKAGE_NAME} #end"
    val nameAndBraces = name + " {\n\n}"
    val templateText = packageLine + "\n" + kind + nameAndBraces

    val template = new FileTemplateBase {
      def setExtension(extension: String) {}
      def setName(name: String) {}
      def getName: String = templateName
      def isDefault: Boolean = true
      def getDescription: String = ""
      def getExtension: String = "scala"
    }

    template.setText(templateText)
    template
  }
}
