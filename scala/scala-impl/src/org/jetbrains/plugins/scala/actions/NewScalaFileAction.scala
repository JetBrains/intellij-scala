package org.jetbrains.plugins.scala.actions

import com.intellij.ide.actions.{CreateFileFromTemplateDialog, CreateTemplateInPackageAction}
import com.intellij.ide.fileTemplates.{FileTemplate, FileTemplateManager, JavaTemplateUtil}
import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx
import com.intellij.openapi.module.{Module, ModuleType}
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.annotations.{NonNls, Nullable, TestOnly}
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaFileType}
import org.jetbrains.sbt.project.module.SbtModuleType

import java.util.Properties
import java.{util => ju}

//TODO: cover this feature with tests. It might require writing custom base test classes
/**
 * @see [[NewPackageObjectAction]]
 */
final class NewScalaFileAction extends CreateTemplateInPackageAction[ScalaPsiElement](
  ScalaBundle.message("newclassorfile.menu.action.text"),
  ScalaBundle.message("newclassorfile.menu.action.description"),
  Icons.CLASS,
  JavaModuleSourceRootTypes.SOURCES
) with DumbAware {

  override def getActionName(directory: PsiDirectory, newName: String, templateName: String): String =
    ScalaBundle.message("newclassorfile.menu.action.text")

  override protected def buildDialog(
    project: Project,
    directory: PsiDirectory,
    builder: CreateFileFromTemplateDialog.Builder
  ): Unit = {

    val module = ScalaFileTemplateUtil.getModuleForDir(project, directory)
    val isInScala3Module = module.exists(_.hasScala3)

    //noinspection ScalaExtractStringToBundle
    {
      builder.addKind("Class", Icons.CLASS, ScalaFileTemplateUtil.SCALA_CLASS)
      builder.addKind("Trait", Icons.TRAIT, ScalaFileTemplateUtil.SCALA_TRAIT)
      builder.addKind("Case Class", Icons.CASE_CLASS, ScalaFileTemplateUtil.SCALA_CASE_CLASS)
      if (isInScala3Module) {
        builder.addKind("Enum", Icons.ENUM, ScalaFileTemplateUtil.SCALA_ENUM)
      }
      builder.addKind("Object", Icons.OBJECT, ScalaFileTemplateUtil.SCALA_OBJECT)
      builder.addKind("Case Object", Icons.CASE_OBJECT, ScalaFileTemplateUtil.SCALA_CASE_OBJECT)
      builder.addKind("File", Icons.SCALA_FILE, ScalaFileTemplateUtil.SCALA_FILE)
    }

    addUserDefinedScalaFileTemplates(project, directory, builder)

    builder.setTitle(ScalaBundle.message("newclassorfile.menu.action.dialog.title"))
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

  private def addUserDefinedScalaFileTemplates(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder): Unit = {
    for {
      template <- FileTemplateManager.getInstance(project).getAllTemplates

      fileType = FileTypeManagerEx.getInstanceEx.getFileTypeByExtension(template.getExtension)
      if fileType == ScalaFileType.INSTANCE && checkPackageExists(directory)

      templateName = template.getName
    } builder.addKind(templateName, fileType.getIcon, templateName)
  }

  @Nullable
  override def getNavigationElement(createdElement: ScalaPsiElement): PsiElement =
    createdElement match {
      case typeDefinition: ScTypeDefinition => typeDefinition.extendsBlock
      case _ => null
    }

  override def postProcess(createdElement: ScalaPsiElement, templateName: String, customProperties: ju.Map[String, String]): Unit = {
    super.postProcess(createdElement, templateName, customProperties)
    createdElement match {
      case file: ScalaFile =>
        moveTheCaretToTheLastLine(file)
      case _ =>
    }
  }

  private def moveTheCaretToTheLastLine(file: ScalaFile): Unit = {
    val project = file.getProject
    val editor = FileEditorManager.getInstance(project).getSelectedTextEditor
    if (editor != null) {
      val document = editor.getDocument
      if (document == file.getViewProvider.getDocument) {
        val lineCount = document.getLineCount
        if (lineCount > 0) {
          // move the caret to the beginning of the last line
          editor.getCaretModel.moveToLogicalPosition(new LogicalPosition(lineCount - 1, 0))
        }
      }
    }
  }

  override def doCreate(directory: PsiDirectory, newName: String, templateName: String): ScalaPsiElement = {
    NewScalaFileAction.createFromTemplate(directory, newName, templateName) match {
      case scalaFile: ScalaFile =>
        scalaFile.typeDefinitions.headOption.getOrElse(scalaFile)
      case _ => null
    }
  }

  override def isAvailable(dataContext: DataContext): Boolean = {
    val module = dataContext.getData(PlatformCoreDataKeys.MODULE)
    module != null && (
      (super.isAvailable(dataContext) && hasScalaInstalledInModule(module)) ||
        NewScalaFileActionExtension.isAvailable(dataContext)
      )
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

  override def checkPackageExists(directory: PsiDirectory): Boolean = JavaDirectoryService.getInstance.getPackage(directory) != null
}

object NewScalaFileAction {
  val ID: String = "Scala.NewClass"

  @NonNls private[actions] val NAME_TEMPLATE_PROPERTY: String = "NAME"
  @NonNls private[actions] val LOW_CASE_NAME_TEMPLATE_PROPERTY: String = "lowCaseName"

  @TestOnly
  def createFromTemplate(
    directory: PsiDirectory,
    name: String,
    templateName: String,
    parameters: String*
  ): PsiFile = {
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
      //TODO: delete it in Nightly or EAP, the issue is fixed in the platform
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

    ScalaFileTemplateUtil.removeBracesIfIndentationBasedSyntaxIsEnabled(project, directory, file)

    CodeStyleManager.getInstance(project).reformat(file)
    directory.add(file).asInstanceOf[PsiFile]
  }
}