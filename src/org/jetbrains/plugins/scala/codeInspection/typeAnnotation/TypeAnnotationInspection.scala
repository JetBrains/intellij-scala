package org.jetbrains.plugins.scala
package codeInspection
package typeAnnotation

import java.{util => ju}

import com.intellij.codeInspection._
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInsight.intention.types.AbstractTypeAnnotationIntention.complete
import org.jetbrains.plugins.scala.codeInsight.intention.types.AddOnlyStrategy
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScFunctionExpr, ScTypedStmt, ScUnderscoreSection}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, TypeAdjuster}
import org.jetbrains.plugins.scala.settings.annotations._
import org.jetbrains.plugins.scala.util._

import scala.collection.mutable

/**
  * Pavel Fatin
  */
class TypeAnnotationInspection extends AbstractInspection {

  import TypeAnnotationInspection._

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case value: ScPatternDefinition if value.isSimple && !value.hasExplicitType =>
      inspect(value, value.bindings.head, value.expr)
    case variable: ScVariableDefinition if variable.isSimple && !variable.hasExplicitType =>
      inspect(variable, variable.bindings.head, variable.expr)
    case method: ScFunctionDefinition if method.hasAssign && !method.hasExplicitType && !method.isSecondaryConstructor =>
      inspect(method, method.nameId, method.body)
    case (parameter: ScParameter) && Parent(Parent(Parent(_: ScFunctionExpr))) if parameter.typeElement.isEmpty =>
      inspect(parameter, parameter.nameId, implementation = None)
    case (underscore: ScUnderscoreSection) && Parent(parent) if !parent.isInstanceOf[ScTypedStmt] =>
      inspect(underscore, underscore, implementation = None)
  }
}

object TypeAnnotationInspection {
  private[typeAnnotation] val Description = "Explicit type annotation required (according to Code Style settings)"

  private def inspect(element: ScalaPsiElement,
                      anchor: PsiElement,
                      implementation: Option[ScExpression])
                     (implicit holder: ProblemsHolder): Unit = {

    val declaration = Declaration(element)
    val location = Location(element)

    if (ScalaTypeAnnotationSettings(element.getProject).isTypeAnnotationRequiredFor(
      declaration, location, implementation.map(Implementation.Expression(_)))) {

      // TODO Create the general-purpose inspection
      val canBePrivate = declaration.entity match {
        case Entity.Method | Entity.Value | Entity.Variable if !location.isInLocalScope =>
          declaration.visibility != Visibility.Private
        case _ => false
      }

      val fixes =
        canBePrivate.seq(new MakePrivateQuickFix(element.asInstanceOf[ScModifierListOwner])) ++
          Seq(new AddTypeAnnotationQuickFix(anchor), new ModifyCodeStyleQuickFix(), new LearnWhyQuickFix())

      holder.registerProblem(anchor, Description, fixes: _*)
    }
  }
}

private class MakePrivateQuickFix(element: ScModifierListOwner) extends AbstractFixOnPsiElement("Make private", element) {
  override def doApplyFix(project: Project): Unit =
    element.setModifierProperty("private", value = true)
}

class AddTypeAnnotationQuickFix(element: PsiElement)
  extends AbstractFixOnPsiElement(AddTypeAnnotationQuickFix.Name, element) with BatchQuickFix[CommonProblemDescriptor] {

  import AddTypeAnnotationQuickFix._

  def doApplyFix(project: Project): Unit =
    complete(getElement)

  override def applyFix(project: Project,
                        descriptors: Array[CommonProblemDescriptor],
                        psiElementsToIgnore: ju.List[PsiElement],
                        refreshViews: Runnable): Unit = {
    val quickFixes = descriptors.flatMap(_.getFixes).collect {
      case quickFix: AddTypeAnnotationQuickFix => quickFix
    }

    val strategy = new CollectTypesToAddStrategy
    ApplicationManagerEx.getApplicationEx.runProcessWithProgressSynchronously(
      new ComputeTypesTask(quickFixes, strategy),
      getFamilyName,
      true,
      project
    )

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
      quickFixes.map(_.getElement)
        .foreach { element =>
          complete(element, strategy)
          updateProcessIndicator(element.getText, quickFixesCount)
        }
    }
  }

  class CollectTypesToAddStrategy() extends AddOnlyStrategy(editor = None) {

    import AddOnlyStrategy._
    import TypeAdjuster.markToAdjust

    private val annotations = mutable.ArrayBuffer[(ScTypeElement, PsiElement)]()

    def addActualTypes(refreshViews: Runnable): Unit = {
      val maybeViews = Option(refreshViews)

      annotations.map {
        case (typeElement, anchor) => addActualType(typeElement, anchor)
      }.foreach { addedElement =>
        markToAdjust(addedElement)
        maybeViews.foreach(_.run())
      }
    }

    override def addTypeAnnotation(`type`: ScType, context: PsiElement, anchor: PsiElement): Unit = {
      annotations ++= annotationFor(`type`, context)
        .map((_, anchor))
    }
  }

}

class LearnWhyQuickFix extends LocalQuickFixBase("Learn Why...") {

  import LearnWhyQuickFix.BlogPostUrl

  def applyFix(project: Project, problemDescriptor: ProblemDescriptor): Unit =
    DesktopUtils.browse(BlogPostUrl)
}

object LearnWhyQuickFix {

  private val BlogPostUrl: String = "http://blog.jetbrains.com/scala/2016/10/05/beyond-code-style/"
}

class ModifyCodeStyleQuickFix extends LocalQuickFixBase("Modify Code Style...") {

  def applyFix(project: Project, problemDescriptor: ProblemDescriptor): Unit =
    TypeAnnotationUtil.showTypeAnnotationsSettings(project)
}
