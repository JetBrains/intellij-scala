package org.jetbrains.plugins.scala.codeInspection.varCouldBeValInspection

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeInspection.{InspectionManager, LocalInspectionTool, LocalQuickFixAndIntentionActionOnPsiElement, ProblemDescriptor, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.codeInspection.{ProblemInfo, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.Pipeline
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.SearchMethodsWithProjectBoundCache
import org.jetbrains.plugins.scala.codeInspection.suppression.ScalaInspectionSuppressor
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{isOnlyVisibleInLocalFile, isPossiblyAssignment}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createValFromVarDefinition

import scala.jdk.CollectionConverters._

class VarCouldBeValInspection extends LocalInspectionTool {

  import VarCouldBeValInspection._

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    e: PsiElement =>
      if (shouldProcessElement(e)) {
        val ourInfos = invoke(e, isOnTheFly)
        val problemDescriptors = mapOurInfosToPlatformProblemDescriptors(holder.getFile.getProject, ourInfos, isOnTheFly)
        problemDescriptors.foreach(holder.registerProblem)
      }
  }

  private def isEnabled(element: PsiElement): Boolean =
    InspectionProjectProfileManager.getInstance(element.getProject)
      .getCurrentProfile.isToolEnabled(HighlightDisplayKey.find(getShortName), element) &&
      !inspectionSuppressor.isSuppressedFor(element, getShortName)

  private def mapOurInfosToPlatformProblemDescriptors(project: Project, problemInfos: Seq[ProblemInfo], isOnTheFly: Boolean): Seq[ProblemDescriptor] = {
    val inspectionManager = InspectionManager.getInstance(project)
    problemInfos.map { problemInfo =>
      inspectionManager.createProblemDescriptor(
        problemInfo.element,
        problemInfo.message,
        isOnTheFly,
        problemInfo.fixes.toArray,
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
    }
  }

  private def invoke(element: PsiElement, isOnTheFly: Boolean): Seq[ProblemInfo] = element match {
    case variable: ScVariableDefinition
      if variable.declaredElements.forall(if (isOnTheFly) hasNoWriteUsagesOnTheFly else hasNoWriteUsages) =>
      Seq(ProblemInfo(variable.keywordToken, ScalaInspectionBundle.message("var.could.be.a.val"), Seq(new VarToValFix(variable))))
    case _ => Seq.empty
  }

  private def shouldProcessElement(element: PsiElement): Boolean =
    HighlightingLevelManager.getInstance(element.getProject).shouldInspect(element) && {
      element match {
        case variable: ScVariableDefinition =>
          isOnlyVisibleInLocalFile(variable)
        case _ => false
      }
    }

  override def isEnabledByDefault: Boolean = true
}

object VarCouldBeValInspection {

  val ID: String = "VarCouldBeVal"

  private val inspectionSuppressor = new ScalaInspectionSuppressor

  private class VarToValFix(variable: ScVariableDefinition) extends LocalQuickFixAndIntentionActionOnPsiElement(variable) {
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
    val search = SearchMethodsWithProjectBoundCache(element.getProject).resolveBasedLocalRefSearch
    val pipeline = new Pipeline(Seq(search), _ => false)
    val usages = pipeline.runSearchPipeline(element, isOnTheFly = true)
    !usages.exists(_.assignment) && usages.exists(!_.assignment)
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
