package org.jetbrains.plugins.scala.actions

import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.{PsiClass, PsiDirectory, PsiFile, PsiMethod}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScOptionalBracesOwner
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectExt, ScalaFeatures}

import java.util.Properties

object ScalaFileTemplateUtil {
  val SCALA_IMPLEMENTED_METHOD_TEMPLATE = "Implemented Scala Method Body.scala"
  val SCALA_OVERRIDDEN_METHOD_TEMPLATE = "Overridden Scala Method Body.scala"

  val SCALA_OBJECT = "Scala Object"
  val SCALA_TRAIT = "Scala Trait"
  val SCALA_CLASS = "Scala Class"
  val SCALA_CASE_CLASS = "Scala CaseClass"
  val SCALA_CASE_OBJECT = "Scala CaseObject"
  val SCALA_FILE = "Scala File"
  val SCALA_ENUM = "Scala Enum"

  def setClassAndMethodNameProperties(properties: Properties, aClass: PsiClass, method: PsiMethod): Unit = {
    var className: String = aClass.qualifiedName
    if (className == null) className = ""
    properties.setProperty(FileTemplate.ATTRIBUTE_CLASS_NAME, className)
    var classSimpleName: String = aClass.name
    if (classSimpleName == null) classSimpleName = ""
    properties.setProperty(FileTemplate.ATTRIBUTE_SIMPLE_CLASS_NAME, classSimpleName)
    val methodName: String = method.name
    properties.setProperty(FileTemplate.ATTRIBUTE_METHOD_NAME, methodName)
  }

  private[actions]
  def getModuleForDir(project: Project, directory: PsiDirectory): Option[Module] =
    Option(ProjectRootManager.getInstance(project).getFileIndex.getModuleForFile(directory.getVirtualFile))

  private[actions]
  def removeBracesIfIndentationBasedSyntaxIsEnabled(project: Project, targetDirectory: PsiDirectory, file: PsiFile): Unit = {
    if (!file.isPhysical) {
      //We need to attach scala features to the synthetic file in order the proper settings for indentation are used
      //TODO: this s still enough. The created temporary file does not belong to the directory yet.
      // So this won't work with overridden settings in .editorconfig (see comment in ProjectExt$#indentationBasedSyntaxEnabled)
      // We might remove the braces on the added file.
      // But ATM I couldn't make it work, the issue is that Undo action removes the braces and not the file removal
      // I couldn't make this change transparent to "UnDo.
      // `CommandProcessor.runUndoTransparentAction` doesn't seem to help for some reason
      val features = ScalaFileTemplateUtil.getModuleForDir(project, targetDirectory).map(_.features).getOrElse(ScalaFeatures.default)
      ScalaFeatures.setAttachedScalaFeatures(file, features)
    }

    val features = ScalaFeatures.forPsiOrDefault(file)
    if (file.getProject.indentationBasedSyntaxEnabled(features)) {
      WriteCommandAction
        .writeCommandAction(file.getProject)
        .shouldRecordActionForActiveDocument(false)
        .run { () =>
          val optionalBracesOwners = file.elements.filterByType[ScOptionalBracesOwner]
          //TODO (minor) we should handle all kind of code with braces from potential user-defined file templates
          optionalBracesOwners.foreach(removeEmptyBraces)
        }
    }
  }

  private def removeEmptyBraces(bracesOwner: ScOptionalBracesOwner): Unit = {
    for {
      lBrace <- bracesOwner.getLBrace
      rBrace <- bracesOwner.getRBrace
    } {
      if (lBrace.getNextSiblingNotWhitespace eq rBrace) {
        val spaceBefore = lBrace.prevLeaf.map(_.getNode)

        val bracesParent = lBrace.getNode.getTreeParent
        bracesParent.removeRange(lBrace.getNode, rBrace.getNode.getTreeNext)

        //remove space between the left brace and the previous code
        spaceBefore.foreach(space => space.getTreeParent.removeChild(space))
      }
    }
  }
}
