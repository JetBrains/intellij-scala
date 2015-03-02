package org.jetbrains.plugins.scala.codeInspection.suppression

import java.util

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.actions.SuppressByCommentFix
import com.intellij.codeInspection.{InspectionsBundle, SuppressionUtil, SuppressionUtilCore}
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiElement}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScDocCommentOwner, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil

import scala.collection.JavaConverters._

/**
 * @author Nikolay.Tropin
 */

abstract class ScalaSuppressByLineCommentFix(key: HighlightDisplayKey) extends SuppressByCommentFix(key, classOf[ScalaPsiElement]) {
  override def createSuppression(project: Project, element: PsiElement, container: PsiElement): Unit = {
    val text: String = SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME + " " + key.getID
    val comment: PsiComment = SuppressionUtil.createComment(project, text, ScalaLanguage.Instance)
    val newLine = ScalaPsiElementFactory.createNewLine(element.getManager)
    container match {
      case owner: ScDocCommentOwner if owner.docComment.isDefined =>
        val docComment = owner.docComment.get
        container.addAfter(comment, docComment)
        container.addAfter(newLine, docComment)
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
    case Some(_: ScFunctionDefinition | _: ScVariableDefinition | _: ScPatternDefinition | _: ScTypeAliasDefinition | _: ScMacroDefinition) => null
    case Some(stmt) => stmt
  }
}

abstract class ScalaSuppressForDefinitionFix(key: HighlightDisplayKey, text: String, defClasses: Class[_ <: PsiElement]*)
        extends ScalaSuppressByLineCommentFix(key) {

  override def getText: String = text

  override def getContainer(context: PsiElement): PsiElement = PsiTreeUtil.getParentOfType(context, defClasses: _*)
}

class ScalaSuppressForClassFix(key: HighlightDisplayKey) 
        extends ScalaSuppressForDefinitionFix(key, InspectionsBundle.message("suppress.inspection.class"), classOf[ScTypeDefinition])

class ScalaSuppressForFunctionFix(key: HighlightDisplayKey)
        extends ScalaSuppressForDefinitionFix(key, InspectionBundle.message("suppress.inspection.function"), classOf[ScFunctionDefinition], classOf[ScMacroDefinition])

class ScalaSuppressForVariableFix(key: HighlightDisplayKey)
        extends ScalaSuppressForDefinitionFix(key, InspectionBundle.message("suppress.inspection.variable"), classOf[ScVariableDefinition], classOf[ScPatternDefinition])

class ScalaSuppressForTypeAliasFix(key: HighlightDisplayKey)
        extends ScalaSuppressForDefinitionFix(key, InspectionBundle.message("suppress.inspection.typeAlias"), classOf[ScTypeAliasDefinition])