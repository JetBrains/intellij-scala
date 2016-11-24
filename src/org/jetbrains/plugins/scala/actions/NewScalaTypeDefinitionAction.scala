package org.jetbrains.plugins.scala
package actions

import java.util.Properties

import com.intellij.ide.IdeView
import com.intellij.ide.actions.{CreateFileFromTemplateDialog, CreateTemplateInPackageAction}
import com.intellij.ide.fileTemplates.{FileTemplate, FileTemplateManager, JavaTemplateUtil}
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.annotations.NonNls
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.project._

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.09.2009
 */

class NewScalaTypeDefinitionAction extends CreateTemplateInPackageAction[ScTypeDefinition](
  ScalaBundle.message("newclass.menu.action.text"), ScalaBundle.message("newclass.menu.action.description"), Icons.CLASS,
  JavaModuleSourceRootTypes.SOURCES) with DumbAware {
  protected def buildDialog(project: Project, directory: PsiDirectory,
                            builder: CreateFileFromTemplateDialog.Builder) {
    builder.addKind("Class", Icons.CLASS, ScalaFileTemplateUtil.SCALA_CLASS)
    builder.addKind("Object", Icons.OBJECT, ScalaFileTemplateUtil.SCALA_OBJECT)
    builder.addKind("Trait", Icons.TRAIT, ScalaFileTemplateUtil.SCALA_TRAIT)

    for (template <- FileTemplateManager.getInstance(project).getAllTemplates) {
      if (isScalaTemplate(template) && checkPackageExists(directory)) {
        builder.addKind(template.getName, Icons.FILE_TYPE_LOGO, template.getName)
      }
    }

    builder.setTitle("Create New Scala Class")
    builder.setValidator(new InputValidatorEx {
      def getErrorText(inputString: String): String = {
        if (inputString.length > 0 && !ScalaNamesUtil.isQualifiedName(inputString)) {
          return "This is not a valid Scala qualified name"
        }
        null
      }

      def checkInput(inputString: String): Boolean = {
        true
      }

      def canClose(inputString: String): Boolean = {
        !StringUtil.isEmptyOrSpaces(inputString) && getErrorText(inputString) == null
      }
    })
  }

  private def isScalaTemplate(template: FileTemplate): Boolean = {
    val fileType: FileType = FileTypeManagerEx.getInstanceEx.getFileTypeByExtension(template.getExtension)
    fileType == ScalaFileType.INSTANCE
  }

  def getActionName(directory: PsiDirectory, newName: String, templateName: String): String = {
    ScalaBundle.message("newclass.menu.action.text")
  }

  def getNavigationElement(createdElement: ScTypeDefinition): PsiElement = createdElement.extendsBlock

  def doCreate(directory: PsiDirectory, newName: String, templateName: String): ScTypeDefinition = {
    createClassFromTemplate(directory, newName, templateName) match {
      case scalaFile: ScalaFile =>
        scalaFile.typeDefinitions.headOption.orNull
      case _ => null
    }
  }

  override def isAvailable(dataContext: DataContext): Boolean = {
    super.isAvailable(dataContext) && isUnderSourceRoots(dataContext)
  }

  private def isUnderSourceRoots(dataContext: DataContext): Boolean = {
    val module: Module = dataContext.getData(LangDataKeys.MODULE.getName).asInstanceOf[Module]
    if (!Option(module).exists(_.hasScala)) {
      return false
    }
    val view = dataContext.getData(LangDataKeys.IDE_VIEW.getName).asInstanceOf[IdeView]
    val project = dataContext.getData(CommonDataKeys.PROJECT.getName).asInstanceOf[Project]
    if (view != null && project != null) {
      val projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex
      val dirs = view.getDirectories
      for (dir <- dirs) {
        val aPackage = JavaDirectoryService.getInstance.getPackage(dir)
        if (projectFileIndex.isInSourceContent(dir.getVirtualFile) && aPackage != null) {
          return true
        }
      }
    }
    false
  }

  private def createClassFromTemplate(directory: PsiDirectory, className: String, templateName: String,
                                      parameters: String*): PsiFile = {
    NewScalaTypeDefinitionAction.createFromTemplate(directory, className, templateName, parameters: _*)
  }

  def checkPackageExists(directory: PsiDirectory): Boolean = {
    JavaDirectoryService.getInstance.getPackage(directory) != null
  }
}

object NewScalaTypeDefinitionAction {
  @NonNls private[actions] val NAME_TEMPLATE_PROPERTY: String = "NAME"
  @NonNls private[actions] val LOW_CASE_NAME_TEMPLATE_PROPERTY: String = "lowCaseName"

  def createFromTemplate(directory: PsiDirectory, name: String, templateName: String, parameters: String*): PsiFile = {
    val project = directory.getProject
    val template: FileTemplate = FileTemplateManager.getInstance(project).getInternalTemplate(templateName)
    val properties: Properties = new Properties(FileTemplateManager.getInstance(project).getDefaultProperties())

    properties.setProperty(FileTemplate.ATTRIBUTE_PACKAGE_NAME,
      ScalaNamesUtil.escapeKeywordsFqn(JavaTemplateUtil.getPackageName(directory)))

    properties.setProperty(NAME_TEMPLATE_PROPERTY, name)
    properties.setProperty(LOW_CASE_NAME_TEMPLATE_PROPERTY, name.substring(0, 1).toLowerCase + name.substring(1))

    var i: Int = 0
    while (i < parameters.length) {
      {
        properties.setProperty(parameters(i), parameters(i + 1))
      }
      i += 2
    }
    var text: String = null
    try {
      text = template.getText(properties)
    }
    catch {
      case e: Exception =>
        throw new RuntimeException("Unable to load template for " + FileTemplateManager.getInstance.internalTemplateToSubject(templateName), e)
    }
    val factory: PsiFileFactory = PsiFileFactory.getInstance(project)
    val scalaFileType = ScalaFileType.INSTANCE
    val file: PsiFile = factory.createFileFromText(s"$name.${scalaFileType.getDefaultExtension}", scalaFileType, text)
    CodeStyleManager.getInstance(project).reformat(file)
    directory.add(file).asInstanceOf[PsiFile]
  }
}