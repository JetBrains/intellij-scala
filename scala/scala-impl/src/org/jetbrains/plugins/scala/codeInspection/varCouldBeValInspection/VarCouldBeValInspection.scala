package org.jetbrains.plugins.scala
package codeInspection
package varCouldBeValInspection

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.{LocalQuickFixAndIntentionActionOnPsiElement, ProblemHighlightType}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.annotator.usageTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.codeInspection.unusedInspections.{HighlightingPassInspection, ProblemInfo}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{isLocalOrPrivate, isPossiblyAssignment}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createValFromVarDefinition

import scala.jdk.CollectionConverters._

class VarCouldBeValInspection extends HighlightingPassInspection {

  import VarCouldBeValInspection._

  override def invoke(element: PsiElement, isOnTheFly: Boolean): Seq[ProblemInfo] = element match {
    case variable: ScVariableDefinition
      if variable.declaredElements.forall(if (isOnTheFly) hasNoWriteUsagesOnTheFly else hasNoWriteUsages) =>
      Seq(ProblemInfo(variable.keywordToken, ScalaInspectionBundle.message("var.could.be.a.val"), ProblemHighlightType.LIKE_UNUSED_SYMBOL, Seq(new VarToValFix(variable))))
    case _ => Seq.empty
  }

  override def shouldProcessElement(element: PsiElement): Boolean = element match {
    case variable: ScVariableDefinition => isLocalOrPrivate(variable)
    case _ => false
  }

  override def isEnabledByDefault: Boolean = true
}

object VarCouldBeValInspection {

  val ID: String = "VarCouldBeVal"

  class VarToValFix(variable: ScVariableDefinition) extends LocalQuickFixAndIntentionActionOnPsiElement(variable) {
    override def invoke(project: Project, file: PsiFile, editor: Editor, startElement: PsiElement, endElement: PsiElement): Unit = {
      val fileModificationService = FileModificationService.getInstance
      startElement match {
        case variable: ScVariableDefinition if fileModificationService.prepareFileForWrite(variable.getContainingFile) =>
          val replacement = createValFromVarDefinition(variable)
          variable.replace(replacement)
        case _ =>
      }
    }

    override def getText: String = ScalaInspectionBundle.message("convert.var.to.val")

    override def getFamilyName: String = getText
  }

  def findReferences(element: PsiElement): (Iterable[PsiElement], Iterable[PsiElement]) = {
    val references = ReferencesSearch.search(element, element.getUseScope)
      .findAll().asScala
      .map(_.getElement)

    references.partition(isPossiblyAssignment)
  }

  private def hasNoWriteUsagesOnTheFly(element: ScBindingPattern): Boolean = {
    var hasWriteUsages = false
    var used = false
    val holder = ScalaRefCountHolder(element)
    holder.retrieveUnusedReferencesInfo { () =>
      hasWriteUsages = holder.isValueWriteUsed(element)
      used = holder.isValueReadUsed(element)
    }
    !hasWriteUsages && used // has no write usages but is used
  }

  private def hasNoWriteUsages(element: ScBindingPattern): Boolean = {
    val (write, read) = findReferences(element)
    read.nonEmpty && write.isEmpty
  }
}
