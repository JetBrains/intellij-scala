package org.jetbrains.plugins.scala.actions

import com.intellij.ide.IdeView
import com.intellij.ide.actions.CreateTemplateInPackageAction
import com.intellij.ide.fileTemplates.{FileTemplateManager, JavaTemplateUtil}
import com.intellij.openapi.actionSystem.{CommonDataKeys, DataContext, LangDataKeys, PlatformCoreDataKeys}
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.annotations.Nls
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

import java.util.Properties
import javax.swing.Icon

abstract class NewTypeDefinitionBase[T <: ScTemplateDefinition](@Nls txt: String, @Nls description: String, icon: Icon)
  extends CreateTemplateInPackageAction[T](txt, description, icon, JavaModuleSourceRootTypes.SOURCES) {
  override def checkPackageExists(psiDirectory: PsiDirectory): Boolean = JavaDirectoryService.getInstance.getPackage(psiDirectory) != null

  override def getNavigationElement(t: T): PsiElement = t.extendsBlock

  override def isAvailable(dataContext: DataContext): Boolean = super.isAvailable(dataContext) && isUnderSourceRoots(dataContext)

  def createFromTemplate(directory: PsiDirectory, name: String, fileName: String, templateName: String,
                         parameters: String*): PsiFile = {
    val templateManager = FileTemplateManager.getDefaultInstance
    val template = templateManager getInternalTemplate templateName
    val project = directory.getProject
    val properties = new Properties(templateManager.getDefaultProperties)

    JavaTemplateUtil.setPackageNameAttribute(properties, directory)
    properties.setProperty(NewTypeDefinitionBase.NAME_TEMPLATE_PROPERTY, name)
    properties.setProperty(NewTypeDefinitionBase.LOW_CASE_NAME_TEMPLATE_PROPERTY, name.substring(0, 1).toLowerCase + name.substring(1))

    for (j <- 0.until(parameters.length, 2)) {
      properties.setProperty(parameters(j), parameters(j + 1))
    }

    var text: String = null

    try text = template getText properties catch {
      case c: ControlFlowException => throw c
      case e: Exception =>
        throw new RuntimeException("Unable to load template for " + templateManager.internalTemplateToSubject(templateName), e)
    }

    val file = createFile(fileName, text, project)
    CodeStyleManager.getInstance(project).reformat(file)
    directory.add(file).asInstanceOf[PsiFile]
  }

  protected def isUnderSourceRoots(dataContext: DataContext): Boolean =
    (dataContext getData PlatformCoreDataKeys.MODULE.getName, dataContext getData LangDataKeys.IDE_VIEW.getName,
     dataContext getData CommonDataKeys.PROJECT.getName) match {
      case (module: Module, view: IdeView, project: Project) =>
        if (!Option(module).exists(checkModule)) return false

        val projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex
        val dirs = view.getDirectories

        for (dir <- dirs) {
          if (projectFileIndex.isInSourceContent(dir.getVirtualFile) &&
            JavaDirectoryService.getInstance.getPackage(dir) != null) return true
        }

        false
      case _ => false
    }

  protected def checkModule(module: Module): Boolean

  protected def getFileType: FileType

  protected def createFile(name: String, text: String, project: Project): PsiFile =
    PsiFileFactory.getInstance(project).createFileFromText(name, getFileType, text)
}

object NewTypeDefinitionBase {
  val NAME_TEMPLATE_PROPERTY: String = "NAME"
  val LOW_CASE_NAME_TEMPLATE_PROPERTY: String = "lowCaseName"
}
