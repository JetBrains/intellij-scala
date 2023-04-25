package org.jetbrains.plugins.scala.codeInspection.varCouldBeValInspection

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.Context
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.SearchMethodsWithProjectBoundCache
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.{HighlightingPassInspection, ProblemInfo}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{isOnlyVisibleInLocalFile, isPossiblyAssignment}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createValFromVarDefinition

import scala.jdk.CollectionConverters._

class VarCouldBeValInspection extends HighlightingPassInspection {

  import VarCouldBeValInspection._

  override def invoke(element: PsiElement, isOnTheFly: Boolean): Seq[ProblemInfo] = element match {
    case variable: ScVariableDefinition
      if variable.declaredElements.forall(if (isOnTheFly) hasNoWriteUsagesOnTheFly else hasNoWriteUsages) =>
      Seq(ProblemInfo(variable.keywordToken, ScalaInspectionBundle.message("var.could.be.a.val"), Seq(new VarToValFix(variable))))
    case _ => Seq.empty
  }

  override def shouldProcessElement(element: PsiElement): Boolean = element match {
    case variable: ScVariableDefinition =>
      isOnlyVisibleInLocalFile(variable)
    case _ => false
  }

  override def isEnabledByDefault: Boolean = true
}

object VarCouldBeValInspection {

  val ID: String = "VarCouldBeVal"

  class VarToValFix(variable: ScVariableDefinition) extends LocalQuickFixAndIntentionActionOnPsiElement(variable) {
    override def invoke(project: Project, file: PsiFile, editor: Editor, startElement: PsiElement, endElement: PsiElement): Unit =
      startElement match {
        case variable: ScVariableDefinition =>
          val file = variable.getContainingFile
          if (file != null && IntentionPreviewUtils.prepareElementForWrite(file)) {
            val replacement = createValFromVarDefinition(variable)
            variable.replace(replacement)
          }
        case _ =>
      }

    override def getText: String = ScalaInspectionBundle.message("convert.var.to.val")

    override def getFamilyName: String = getText
  }

  private def hasNoWriteUsagesOnTheFly(element: ScBindingPattern): Boolean = {
    val ctx = new Context(element, _ => false)
    val results = SearchMethodsWithProjectBoundCache(element.getProject).resolveBasedLocalRefSearch.searchForUsages(ctx)
    !results.usages.exists(_.assignment) && results.usages.exists(!_.assignment)
  }

  private def hasNoWriteUsages(element: ScBindingPattern): Boolean = {
    val (write, read) = findReferences(element)
    read.nonEmpty && write.isEmpty
  }

  private def findReferences(element: PsiElement): (Iterable[PsiElement], Iterable[PsiElement]) = {
    val references = ReferencesSearch.search(element, element.getUseScope)
      .findAll().asScala
      .map(_.getElement)

    references.partition(isPossiblyAssignment)
  }
}
