package org.jetbrains.plugins.scala
package codeInspection
package varCouldBeValInspection

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.annotator.importsTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.codeInspection.unusedInspections.{HighlightingPassInspection, ProblemInfo}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{isLocalOrPrivate, isPossiblyAssignment}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createValFromVarDefinition

import scala.collection.JavaConverters

class VarCouldBeValInspection extends HighlightingPassInspection {

  import VarCouldBeValInspection._

  override def invoke(element: PsiElement, isOnTheFly: Boolean): Seq[ProblemInfo] = element match {
    case variable: ScVariableDefinition if couldBeVal(variable.declaredElements, isOnTheFly) =>
      Seq(ProblemInfo(variable.keywordToken, DESCRIPTION, Seq(new VarToValFix(variable))))
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
  val DESCRIPTION: String = "var could be a val"

  class VarToValFix(variable: ScVariableDefinition) extends LocalQuickFixAndIntentionActionOnPsiElement(variable) {

    import VarToValFix._

    override def invoke(project: Project, file: PsiFile, editor: Editor, startElement: PsiElement, endElement: PsiElement): Unit = {
      val fileModificationService = FileModificationService.getInstance
      startElement match {
        case variable: ScVariableDefinition if fileModificationService.prepareFileForWrite(variable.getContainingFile) =>
          val replacement = createValFromVarDefinition(variable)
          variable.replace(replacement)
        case _ =>
      }
    }

    override def getText: String = HINT

    override def getFamilyName: String = getText
  }

  object VarToValFix {

    val HINT: String = "Convert 'var' to 'val'"
  }

  def findReferences(element: PsiElement): (Iterable[PsiElement], Iterable[PsiElement]) = {
    import JavaConverters._
    val references = ReferencesSearch.search(element, element.getUseScope)
      .findAll().asScala
      .map(_.getElement)

    references.partition(isPossiblyAssignment)
  }

  private def couldBeVal(elements: Seq[ScBindingPattern], isOnTheFly: Boolean): Boolean =
    elements.exists {
      case pattern if isOnTheFly => hasNoWriteUsagesOnTheFly(pattern)
      case pattern => hasNoWriteUsages(pattern)
    }

  private[this] def hasNoWriteUsagesOnTheFly(pattern: ScBindingPattern): Boolean = {
    var hasWriteUsages = false
    var used = false
    val holder = ScalaRefCountHolder.getInstance(pattern.getContainingFile)
    holder.retrieveUnusedReferencesInfo { () =>
      hasWriteUsages = holder.isValueWriteUsed(pattern)
      used = holder.isValueReadUsed(pattern)
    }
    !hasWriteUsages && used // has no write usages but is used
  }

  private[this] def hasNoWriteUsages(pattern: ScBindingPattern): Boolean = {
    val (write, read) = findReferences(pattern)
    read.nonEmpty && write.isEmpty
  }
}
