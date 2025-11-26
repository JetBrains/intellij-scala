package org.jetbrains.plugins.scala.codeInspection.suppression

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.actions.SuppressByCommentFix
import com.intellij.codeInspection.{InspectionsBundle, SuppressionUtil, SuppressionUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiElement}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.extensions.{ObjectExt, OptionExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScDocCommentOwner, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createNewLine
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil

import java.util
import scala.jdk.CollectionConverters._

abstract class ScalaSuppressByLineCommentFix(key: HighlightDisplayKey) extends SuppressByCommentFix(key, classOf[ScalaPsiElement]) {

  override def getPriority: Int = {
    // I chose a random "large enough" number as a baseline
    val DefaultPriority = 1000

    // Reminder:
    //   Quick-fixes with BIGGER priorities will appear LAST
    //   Quick-fixes with the same priority will be sorted alphabetically
    this match {
      case _: ScalaSuppressForFileFix =>
        DefaultPriority
      case _: ScalaSuppressForClassFix =>
        DefaultPriority - 10
      case _: ScalaSuppressForDefinitionFix =>
        DefaultPriority - 20
      case _: ScalaSuppressForStatementFix =>
        DefaultPriority - 30
      case _ =>
        DefaultPriority
    }
  }

  //noinspection ApiStatus,UnstableApiUsage
  protected final def createComment(project: Project): PsiComment = {
    val text: String = SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME + " " + key.getID
    SuppressionUtil.createComment(project, text, ScalaLanguage.INSTANCE)
  }

  override def createSuppression(project: Project, element: PsiElement, container: PsiElement): Unit = {
    val comment = createComment(project)
    val newLine = createNewLine()(element.getManager)
    container match {
      case owner: ScDocCommentOwner if owner.docComment.isDefined =>
        val docComment = owner.docComment.get
        container.addAfter(comment, docComment)
        container.addAfter(newLine, docComment)
      case owner: ScCommentOwner =>
        val firstChild = owner.getFirstChild
        owner.addBefore(comment, firstChild)
        owner.addBefore(newLine, firstChild)
      case _ =>
        val parent = container.getParent
        parent.addBefore(comment, container)
        parent.addBefore(newLine, container)
    }
  }

  override def getCommentsFor(container: PsiElement): util.List[_ <: PsiElement] = {
    ScalaSuppressableInspectionTool.commentsFor(container).asJava
  }
}

class ScalaSuppressForStatementFix(key: HighlightDisplayKey) extends ScalaSuppressByLineCommentFix(key) {

  override def getText: String = InspectionsBundle.message("suppress.inspection.statement")

  override def getContainer(context: PsiElement): PsiElement = ScalaRefactoringUtil.findEnclosingBlockStatement(context) match {
    case None => null
    case Some(_: ScDefinitionWithAssignment) => null
    case Some(stmt) => stmt
  }
}

abstract class ScalaSuppressForDefinitionFix(key: HighlightDisplayKey, @Nls text: String, defClasses: Class[_ <: PsiElement]*)
  extends ScalaSuppressByLineCommentFix(key) {

  override def getText: String = text

  override def getContainer(context: PsiElement): PsiElement = PsiTreeUtil.getParentOfType(context, defClasses: _*)
}

final class ScalaSuppressForFileFix(key: HighlightDisplayKey) extends ScalaSuppressByLineCommentFix(key) {
  override def getText: String = ScalaInspectionBundle.message("suppress.inspection.file")

  override def getContainer(context: PsiElement): PsiElement = context.containingScalaFile.orNull

  override def getCommentsFor(container: PsiElement): util.List[_ <: PsiElement] =
    container.asOptionOf[ScalaFile]
      .flatMap(_.firstChild.filterByType[PsiComment])
      .toList
      .asJava

  override def createSuppression(project: Project, element: PsiElement, container: PsiElement): Unit = container match {
    case file: ScalaFile =>
      val comment = createComment(project)
      val newLine = createNewLine()(element.getManager)
      file.firstChild.foreach { anchor =>
        file.addBefore(comment, anchor)
        file.addBefore(newLine, anchor)
      }
    case _ =>
  }
}

final class ScalaSuppressForClassFix(key: HighlightDisplayKey)
  extends ScalaSuppressForDefinitionFix(key, InspectionsBundle.message("suppress.inspection.class"), classOf[ScTypeDefinition])

final class ScalaSuppressForFunctionFix(key: HighlightDisplayKey)
  extends ScalaSuppressForDefinitionFix(key, ScalaInspectionBundle.message("suppress.inspection.function"), classOf[ScFunctionDefinition], classOf[ScMacroDefinition])

final class ScalaSuppressForVariableFix(key: HighlightDisplayKey)
  extends ScalaSuppressForDefinitionFix(key, ScalaInspectionBundle.message("suppress.inspection.variable"), classOf[ScVariableDefinition], classOf[ScPatternDefinition])

final class ScalaSuppressForTypeAliasFix(key: HighlightDisplayKey)
  extends ScalaSuppressForDefinitionFix(key, ScalaInspectionBundle.message("suppress.inspection.typeAlias"), classOf[ScTypeAliasDefinition])
