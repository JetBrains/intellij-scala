package org.jetbrains.plugins.scala.actions

import com.intellij.ide.actions.{CreateFileFromTemplateDialog, CreateTemplateInPackageAction}
import com.intellij.ide.fileTemplates.{FileTemplate, FileTemplateManager, JavaTemplateUtil}
import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx
import com.intellij.openapi.module.{Module, ModuleType}
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.annotations.NonNls
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.plugins.scala.actions.NewScalaFileAction._
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaFileType}
import org.jetbrains.sbt.project.module.SbtModuleType

import java.util.Properties

final class NewScalaFileAction extends CreateTemplateInPackageAction[ScalaPsiElement](
  () => actionText,
  () => actionDescription,
  Icons.CLASS,
  JavaModuleSourceRootTypes.SOURCES
) with DumbAware {
  override def update(e: AnActionEvent): Unit = {
    super.update(e)

    isInScala3Module =
      Option(e.getDataContext.getData(PlatformCoreDataKeys.MODULE.getName))
        .exists(_.asInstanceOf[Module].hasScala3)
  }

  override protected def buildDialog(project: Project, directory: PsiDirectory,
                                     builder: CreateFileFromTemplateDialog.Builder): Unit = {

    //noinspection ScalaExtractStringToBundle
    {
      builder.addKind("Class", Icons.CLASS, ScalaFileTemplateUtil.SCALA_CLASS)
      builder.addKind("Case Class", Icons.CASE_CLASS, ScalaFileTemplateUtil.SCALA_CASE_CLASS)
      if (isInScala3Module) {
        builder.addKind("File", Icons.SCALA_FILE, ScalaFileTemplateUtil.SCALA_FILE)
      }
      builder.addKind("Object", Icons.OBJECT, ScalaFileTemplateUtil.SCALA_OBJECT)
      builder.addKind("Case Object", Icons.CASE_OBJECT, ScalaFileTemplateUtil.SCALA_CASE_OBJECT)
      builder.addKind("Trait", Icons.TRAIT, ScalaFileTemplateUtil.SCALA_TRAIT)
      //place enum in the very end of the list SCL-20749
      if (isInScala3Module) {
        builder.addKind("Enum", Icons.ENUM, ScalaFileTemplateUtil.SCALA_ENUM)
      }
    }

    for {
      template <- FileTemplateManager.getInstance(project).getAllTemplates

      fileType = FileTypeManagerEx.getInstanceEx.getFileTypeByExtension(template.getExtension)
      if fileType == ScalaFileType.INSTANCE && checkPackageExists(directory)

      templateName = template.getName
    } builder.addKind(templateName, fileType.getIcon, templateName)

    builder.setTitle(
      if (isInScala3Module)
        ScalaBundle.message("create.new.scala.class.or.file")
      else
        ScalaBundle.message("create.new.scala.class")
    )
    builder.setValidator(new InputValidatorEx {
      override def getErrorText(inputString: String): String = {
        if (inputString.nonEmpty && !ScalaNamesUtil.isQualifiedName(inputString)) {
          return ScalaBundle.message("this.is.not.a.valid.scala.qualified.name")
        }

        // Specifically make sure that the input string doesn't repeat an existing package prefix (twice).
        // Technically, "org.example.application.org.example.application.Main" is not an error, but most likely it's so (and there's no way to display a warning).
        for (sourceFolder <- Option(ProjectRootsUtil.getModuleSourceRoot(directory.getVirtualFile, project));
             packagePrefix = sourceFolder.getPackagePrefix if packagePrefix.nonEmpty
             if (inputString + ".").startsWith(packagePrefix + ".")) {
          return ScalaInspectionBundle.message("package.names.does.not.correspond.to.directory.structure.package.prefix", sourceFolder.getFile.getName, packagePrefix)
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

  override def getActionName(directory: PsiDirectory, newName: String, templateName: String): String = actionText

  override def getNavigationElement(createdElement: ScalaPsiElement): PsiElement =
    createdElement match {
      case typeDefinition: ScTypeDefinition => typeDefinition.extendsBlock
      case _ => createdElement
    }

  override def doCreate(directory: PsiDirectory, newName: String, templateName: String): ScalaPsiElement = {
    createClassFromTemplate(directory, newName, templateName) match {
      case scalaFile: ScalaFile =>
        scalaFile.typeDefinitions.headOption.getOrElse(scalaFile)
      case _ => null
    }
  }

  override def isAvailable(dataContext: DataContext): Boolean =
    super.isAvailable(dataContext) && hasScalaInstalledInModule(dataContext) ||
      NewScalaFileActionExtension.isAvailable(dataContext)

  private def hasScalaInstalledInModule(dataContext: DataContext): Boolean = {
    val module = dataContext.getData(PlatformCoreDataKeys.MODULE)
    module != null && hasScalaInstalledInModule(module)
  }

  private def hasScalaInstalledInModule(module: Module): Boolean = {
    val moduleType = ModuleType.get(module)
    moduleType match {
      // Note: technically an sbt module might not have Scala SDK (scalaInstance) configured,
      // but we still allow creating the files in such modules
      // (it's some legacy behavior, which shouldn't hurt anyone)
      case _: SbtModuleType => true
      case _ => module.hasScala
    }
  }

  private def createClassFromTemplate(directory: PsiDirectory, className: String, templateName: String,
                                      parameters: String*): PsiFile = {
    NewScalaFileAction.createFromTemplate(directory, className, templateName, parameters: _*)
  }

  override def checkPackageExists(directory: PsiDirectory): Boolean = JavaDirectoryService.getInstance.getPackage(directory) != null
}

object NewScalaFileAction {
  @NonNls private[actions] val NAME_TEMPLATE_PROPERTY: String = "NAME"
  @NonNls private[actions] val LOW_CASE_NAME_TEMPLATE_PROPERTY: String = "lowCaseName"

  private var isInScala3Module = false

  def actionText: String =
    if (isInScala3Module)
      ScalaBundle.message("newclassorfile.menu.action.text")
    else
      ScalaBundle.message("newclass.menu.action.text")

  def actionDescription: String =
    if (isInScala3Module)
      ScalaBundle.message("newclassorfile.menu.action.description")
    else
      ScalaBundle.message("newclass.menu.action.description")

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
      //workaround for IDEA-295002 (can remove when it's fixed)
      if (text.contains('\r')) {
        text = StringUtil.convertLineSeparators(text)
      }
    }
    catch {
      case c: ControlFlowException => throw c
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
