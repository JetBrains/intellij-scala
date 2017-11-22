package org.jetbrains.plugins.scala.codeInspection.typeAnnotation

import java.{util => ju}

import com.intellij.codeInspection._
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInsight.intention.types.AbstractTypeAnnotationIntention.complete
import org.jetbrains.plugins.scala.codeInsight.intention.types.AddOnlyStrategy
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.codeInspection.typeAnnotation.AddTypeAnnotationQuickFix._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.TypeAdjuster
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType

import scala.collection.mutable

class AddTypeAnnotationQuickFix(element: PsiElement)
  extends AbstractFixOnPsiElement(AddTypeAnnotationQuickFix.Name, element) with BatchQuickFix[CommonProblemDescriptor] {

  override protected def doApplyFix(element: PsiElement)
                                   (implicit project: Project): Unit = {
    complete(element)
  }

  override def applyFix(project: Project,
                        descriptors: Array[CommonProblemDescriptor],
                        psiElementsToIgnore: ju.List[PsiElement],
                        refreshViews: Runnable): Unit = {

    val quickFixes = descriptors.flatMap(_.getFixes).collect {
      case quickFix: AddTypeAnnotationQuickFix => quickFix
    }

    val strategy = new CollectTypesToAddStrategy

    ApplicationManagerEx.getApplicationEx
      .runProcessWithProgressSynchronously(new ComputeTypesTask(quickFixes, strategy), getFamilyName, true, project)

    inWriteCommandAction(project, getFamilyName) {
      strategy.addActualTypes(refreshViews)
    }
  }
}

object AddTypeAnnotationQuickFix {
  val Name = "Add type annotation"

  private def updateProcessIndicator(text: String, quickFixesCount: Int): Unit = {
    val indicator = ProgressManager.getInstance().getProgressIndicator
    indicator.setFraction(indicator.getFraction + 1.0 / quickFixesCount)
    indicator.setText(text)
  }

  private class ComputeTypesTask(quickFixes: Seq[AddTypeAnnotationQuickFix],
                                 strategy: CollectTypesToAddStrategy) extends Runnable {

    override def run(): Unit = inReadAction {
      val quickFixesCount = quickFixes.length
      quickFixes.map(_.getStartElement)
        .filter(_.isValid)
        .foreach { element =>
          complete(element, strategy)
          updateProcessIndicator(element.getText, quickFixesCount)
        }
    }
  }

  private class CollectTypesToAddStrategy() extends AddOnlyStrategy(editor = None) {
    import AddOnlyStrategy._
    import TypeAdjuster.markToAdjust

    private val annotations = mutable.ArrayBuffer[(ScTypeElement, PsiElement)]()

    def addActualTypes(refreshViews: Runnable): Unit = {
      val maybeViews = Option(refreshViews)

      annotations.map {
        case (typeElement, anchor) => addActualType(typeElement, anchor)
      } foreach { addedElement =>
        markToAdjust(addedElement)
        maybeViews.foreach(_.run())
      }
    }

    override def addTypeAnnotation(`type`: ScType, context: PsiElement, anchor: PsiElement): Unit = {
      annotations ++= annotationFor(`type`, context).map((_, anchor))
    }
  }
}
