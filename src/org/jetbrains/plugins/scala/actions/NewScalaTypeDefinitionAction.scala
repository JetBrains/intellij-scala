package org.jetbrains.plugins.scala.actions

import java.lang.String
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.DumbAware
import com.intellij.ide.actions.{CreateFileFromTemplateDialog, CreateTemplateInPackageAction}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.psi._
import org.jetbrains.annotations.NonNls
import com.intellij.openapi.module.Module
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaBundle}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.ide.IdeView
import java.util.Properties
import org.jetbrains.plugins.scala.config.ScalaFacet
import com.intellij.ide.fileTemplates.{FileTemplateManager, FileTemplate, JavaTemplateUtil}
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx
import com.intellij.openapi.fileTypes.FileType

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.09.2009
 */

class NewScalaTypeDefinitionAction extends CreateTemplateInPackageAction[ScTypeDefinition](
  ScalaBundle.message("newclass.menu.action.text"), ScalaBundle.message("newclass.menu.action.description"), Icons.CLASS, true) with DumbAware {
  protected def buildDialog(project: Project, directory: PsiDirectory,
                            builder: CreateFileFromTemplateDialog.Builder) {
    builder.addKind("Class", Icons.CLASS, "Scala Class");
    builder.addKind("Object", Icons.OBJECT, "Scala Object");
    builder.addKind("Trait", Icons.TRAIT, "Scala Trait");



    for (template <- FileTemplateManager.getInstance.getAllTemplates) {
      if (isScalaTemplate(template) && checkPackageExists(directory)) {
        builder.addKind(template.getName, Icons.FILE_TYPE_LOGO, template.getName)
      }
    }

    builder.setTitle("Create New Scala Class")
  }

  private def isScalaTemplate(template: FileTemplate): Boolean = {
    val fileType: FileType = FileTypeManagerEx.getInstanceEx.getFileTypeByExtension(template.getExtension)
    fileType == ScalaFileType.SCALA_FILE_TYPE
  }

  def getActionName(directory: PsiDirectory, newName: String, templateName: String): String = {
    ScalaBundle.message("newclass.menu.action.text")
  }

  def getNavigationElement(createdElement: ScTypeDefinition): PsiElement = createdElement.extendsBlock

  def doCreate(directory: PsiDirectory, newName: String, templateName: String): ScTypeDefinition = {
    val file: PsiFile = createClassFromTemplate(directory, newName, templateName);
    if (file.isInstanceOf[ScalaFile]) {
      val scalaFile = file.asInstanceOf[ScalaFile]
      val classes = scalaFile.getClasses
      if (classes.length == 1 && classes(0).isInstanceOf[ScTypeDefinition]) {
        val definition = classes(0).asInstanceOf[ScTypeDefinition]
        return definition
      }
    }
    null
  }

  override def isAvailable(dataContext: DataContext): Boolean = {
    super.isAvailable(dataContext) && isUnderSourceRoots(dataContext)
  }

  private def isUnderSourceRoots(dataContext: DataContext): Boolean = {
    val module: Module = dataContext.getData(LangDataKeys.MODULE.getName).asInstanceOf[Module]
    if (!ScalaFacet.isPresentIn(module)) {
      return false
    }
    val view = dataContext.getData(LangDataKeys.IDE_VIEW.getName).asInstanceOf[IdeView]
    val project = dataContext.getData(PlatformDataKeys.PROJECT.getName).asInstanceOf[Project]
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
    NewScalaTypeDefinitionAction.createFromTemplate(directory, className, className + SCALA_EXTENSIOIN, templateName, parameters: _*)
  }

  private val SCALA_EXTENSIOIN = ".scala";

  override def doCheckCreate(dir: PsiDirectory, className: String, templateName: String) {
    if (!ScalaNamesUtil.isIdentifier(className)) {
      throw new IncorrectOperationException(PsiBundle.message("0.is.not.an.identifier", className))
    }
    val fileName: String = className + "." + ScalaFileType.DEFAULT_EXTENSION
    dir.checkCreateFile(fileName)
    val helper: PsiNameHelper = JavaPsiFacade.getInstance(dir.getProject).getNameHelper
    var aPackage: PsiPackage = JavaDirectoryService.getInstance.getPackage(dir)
    val qualifiedName: String = if (aPackage == null) null else aPackage.getQualifiedName
    if (!StringUtil.isEmpty(qualifiedName) && !helper.isQualifiedName(qualifiedName)) {
      throw new IncorrectOperationException("Cannot create class in invalid package: '" + qualifiedName + "'")
    }
  }

  def checkPackageExists(directory: PsiDirectory) = {
    JavaDirectoryService.getInstance.getPackage(directory) != null
  }
}

object NewScalaTypeDefinitionAction {
  @NonNls private[actions] val NAME_TEMPLATE_PROPERTY: String = "NAME"
  @NonNls private[actions] val LOW_CASE_NAME_TEMPLATE_PROPERTY: String = "lowCaseName"

  def createFromTemplate(directory: PsiDirectory, name: String, fileName: String, templateName: String,
                         parameters: String*): PsiFile = {
    val template: FileTemplate = FileTemplateManager.getInstance.getInternalTemplate(templateName)
    val properties: Properties = new Properties(FileTemplateManager.getInstance.getDefaultProperties)
    JavaTemplateUtil.setPackageNameAttribute(properties, directory)
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
      case e: Exception => {
        throw new RuntimeException("Unable to load template for " + FileTemplateManager.getInstance.internalTemplateToSubject(templateName), e)
      }
    }
    val factory: PsiFileFactory = PsiFileFactory.getInstance(directory.getProject)
    val file: PsiFile = factory.createFileFromText(fileName, text)
    directory.add(file).asInstanceOf[PsiFile]
  }
}