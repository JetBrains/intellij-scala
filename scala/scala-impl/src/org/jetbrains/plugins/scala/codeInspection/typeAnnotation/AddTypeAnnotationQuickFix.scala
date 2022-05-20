package org.jetbrains.plugins.scala
package codeInspection
package typeAnnotation

import com.intellij.codeInspection._
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInsight.intention.types.AbstractTypeAnnotationIntention.complete
import org.jetbrains.plugins.scala.codeInsight.intention.types.AddOnlyStrategy
import org.jetbrains.plugins.scala.codeInspection.typeAnnotation.AddTypeAnnotationQuickFix._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.TypeAdjuster
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

import java.{util => ju}
import scala.collection.mutable

class AddTypeAnnotationQuickFix(element: PsiElement)
  extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("add.type.annotation"), element) with BatchQuickFix {

  override protected def doApplyFix(element: PsiElement)
                                   (implicit project: Project): Unit = {
    val maybeEditor = getActiveEditor(element, project)
    complete(element, strategy = new AddOnlyStrategy(maybeEditor))
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
      .runProcessWithProgressSynchronously(new ComputeTypesTask(quickFixes.toSeq, strategy), getFamilyName, true, project)

    inWriteCommandAction {
      strategy.addActualTypes(refreshViews)
    }(project)
  }
}

object AddTypeAnnotationQuickFix {
  private def updateProcessIndicator(text: String, quickFixesCount: Int): Unit = {
    val indicator = ProgressManager.getInstance().getProgressIndicator
    indicator.setFraction(indicator.getFraction + 1.0 / quickFixesCount)
    //noinspection ReferencePassedToNls
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

    override def addTypeAnnotation(types: Seq[TypeForAnnotation], context: PsiElement, anchor: PsiElement): Unit = {
      annotations ++= types.flatMap(_.typeWithSuperTypes).headOption.map((_, anchor))
    }
  }
}
