package org.jetbrains.plugins.scala
package actions

import java.util.Properties

import com.intellij.ide.IdeView
import com.intellij.ide.actions.{CreateFileFromTemplateDialog, CreateTemplateInPackageAction}
import com.intellij.ide.fileTemplates.{FileTemplate, FileTemplateManager, JavaTemplateUtil}
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx
import com.intellij.openapi.module.{Module, ModuleType}
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.annotations.NonNls
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.project._
import org.jetbrains.sbt.project.module.SbtModuleType

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.09.2009
 */

class NewScalaTypeDefinitionAction extends CreateTemplateInPackageAction[ScTypeDefinition](
  ScalaBundle.message("newclass.menu.action.text"),
  ScalaBundle.message("newclass.menu.action.description"),
  Icons.CLASS,
  JavaModuleSourceRootTypes.SOURCES
) with DumbAware {
  override protected def buildDialog(project: Project, directory: PsiDirectory,
                                     builder: CreateFileFromTemplateDialog.Builder): Unit = {

    //noinspection ScalaExtractStringToBundle
    {
      builder.addKind("Class", Icons.CLASS, ScalaFileTemplateUtil.SCALA_CLASS)
      builder.addKind("Case Class", Icons.CASE_CLASS, ScalaFileTemplateUtil.SCALA_CASE_CLASS)
      builder.addKind("Object", Icons.OBJECT, ScalaFileTemplateUtil.SCALA_OBJECT)
      builder.addKind("Case Object", Icons.CASE_OBJECT, ScalaFileTemplateUtil.SCALA_CASE_OBJECT)
      builder.addKind("Trait", Icons.TRAIT, ScalaFileTemplateUtil.SCALA_TRAIT)
    }

    for {
      template <- FileTemplateManager.getInstance(project).getAllTemplates

      fileType = FileTypeManagerEx.getInstanceEx.getFileTypeByExtension(template.getExtension)
      if fileType == ScalaFileType.INSTANCE && checkPackageExists(directory)

      templateName = template.getName
    } builder.addKind(templateName, fileType.getIcon, templateName)

    builder.setTitle(ScalaBundle.message("create.new.scala.class"))
    builder.setValidator(new InputValidatorEx {
      override def getErrorText(inputString: String): String = {
        if (inputString.length > 0 && !ScalaNamesUtil.isQualifiedName(inputString)) {
          return ScalaBundle.message("this.is.not.a.valid.scala.qualified.name")
        }
        null
      }

      override def checkInput(inputString: String): Boolean = {
        true
      }

      override def canClose(inputString: String): Boolean = {
        !StringUtil.isEmptyOrSpaces(inputString) && getErrorText(inputString) == null
      }
    })
  }

  override def getActionName(directory: PsiDirectory, newName: String, templateName: String): String = {
    ScalaBundle.message("newclass.menu.action.text")
  }

  override def getNavigationElement(createdElement: ScTypeDefinition): PsiElement = createdElement.extendsBlock

  override def doCreate(directory: PsiDirectory, newName: String, templateName: String): ScTypeDefinition = {
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
    val validModule =
      if (module == null) false
      else
        ModuleType.get(module) match {
          case _: SbtModuleType => true
          case _ => module.hasScala
        }

    validModule && isUnderSourceRoots0(dataContext)
  }

  private def isUnderSourceRoots0(dataContext: DataContext) = {
    val view = dataContext.getData(LangDataKeys.IDE_VIEW.getName).asInstanceOf[IdeView]
    val project = dataContext.getData(CommonDataKeys.PROJECT.getName).asInstanceOf[Project]
    if (view != null && project != null) {
      val projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex
      val dirs = view.getDirectories
      dirs.exists { dir =>
        val aPackage = JavaDirectoryService.getInstance.getPackage(dir)
        projectFileIndex.isInSourceContent(dir.getVirtualFile) && aPackage != null
      }
    } else false
  }

  private def createClassFromTemplate(directory: PsiDirectory, className: String, templateName: String,
                                      parameters: String*): PsiFile = {
    NewScalaTypeDefinitionAction.createFromTemplate(directory, className, templateName, parameters: _*)
  }

  override def checkPackageExists(directory: PsiDirectory): Boolean = JavaDirectoryService.getInstance.getPackage(directory) != null
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
        throw new RuntimeException("Unable to load template for " + FileTemplateManager.getInstance(project).internalTemplateToSubject(templateName), e)
    }
    val factory: PsiFileFactory = PsiFileFactory.getInstance(project)
    val scalaFileType = ScalaFileType.INSTANCE
    val file: PsiFile = factory.createFileFromText(s"$name.${scalaFileType.getDefaultExtension}", scalaFileType, text)
    CodeStyleManager.getInstance(project).reformat(file)
    directory.add(file).asInstanceOf[PsiFile]
  }
}
