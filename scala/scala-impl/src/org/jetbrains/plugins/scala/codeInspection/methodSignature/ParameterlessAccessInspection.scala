package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection._
import com.intellij.psi.{PsiElement, PsiFile, PsiMethod}
import org.jetbrains.plugins.scala.annotator.quickfix.AddCallParenthesesQuickFix
import org.jetbrains.plugins.scala.codeInspection.PsiElementVisitorSimple
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.project.ScalaFeatures.forPsiOrDefault
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

import scala.annotation.tailrec

sealed abstract class ParameterlessAccessInspection extends LocalInspectionTool {

  import ParameterlessAccessInspection._

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = (element: PsiElement) => {
    if (isValid(element)) {
      val errorData = element match {
        case ref@ScReferenceExpression(target) =>
          getMissingEmptyArgumentsErrorWithFix(ref, target)
        case _ =>
          None
      }
      errorData.foreach { case (elementToAnnotate, quickFix) =>
        holder.registerProblem(elementToAnnotate, getDisplayName, quickFix)
      }
    }
  }

  def getMissingEmptyArgumentsErrorWithFix(ref: ScReferenceExpression, targetElement: PsiElement): Option[(PsiElement, AddCallParenthesesQuickFix)] =
    targetElement match {
      case method: PsiMethod =>
        if (isApplicableForInspection(method))
          getMissingEmptyArgumentsFix(ref).map(fix => (ref.nameId, fix))
        else
          None
      case _ =>
        None
    }

  private def getMissingEmptyArgumentsFix(ref: ScReferenceExpression): Option[AddCallParenthesesQuickFix] = {
    val maybeTargetExpression = ref.getParent match {
      case parent if !isFixable(parent) => None
      case call: ScGenericCall if !findCall(call) => Some(call)
      case _: ScGenericCall => None
      case _ => Some(ref)
    }

    maybeTargetExpression.flatMap(collect(_, ref)).map { expr =>
      new AddCallParenthesesQuickFix(expr)
    }
  }

  protected def isValid(element: PsiElement): Boolean = element.isValid

  protected def isApplicableForInspection(method: PsiMethod): Boolean

  protected def isFixable(parent: PsiElement): Boolean = parent match {
    case _: ScMethodCall |
         _: ScInfixExpr |
         _: ScUnderscoreSection => false
    case _ => true
  }

  protected def collect(expression: ScExpression,
                        reference: ScReferenceExpression): Option[ScExpression]
}

object ParameterlessAccessInspection {

  final class JavaMutator extends ParameterlessAccessInspection {

    override protected def collect(expression: ScExpression,
                                   reference: ScReferenceExpression): Option[ScExpression] = reference match {
      case HasFunctionType(_) => None
      case _ => Some(expression)
    }

    override protected def isApplicableForInspection(method: PsiMethod): Boolean = quickfix.isMutator(method)
  }

  final class EmptyParenMethod extends ParameterlessAccessInspection {

    /** Scala 3 is handled in [[org.jetbrains.plugins.scala.annotator.element.ScReferenceAnnotator.annotateReference]]*/
    override def isAvailableForFile(file: PsiFile): Boolean =
      !file.isScala3 && super.isAvailableForFile(file)

    // might have been eta-expanded to () => A, so don't worn.
    // this avoids false positives. To be more accurate, we would need an 'etaExpanded'
    // flag in ScalaResolveResult.
    override protected def collect(expression: ScExpression,
                                   reference: ScReferenceExpression): Option[ScExpression] = expression match {
      case HasFunctionType(Seq()) => None
      case _ => Some(reference)
    }

    override protected def isValid(element: PsiElement): Boolean =
      super.isValid(element) && IntentionAvailabilityChecker.checkInspection(this, element)

    override protected def isApplicableForInspection(method: PsiMethod): Boolean = method match {
      case function: ScFunction =>
        function.isValid && !function.isInCompiledFile && {
          //TODO: we might need to review this logic when we implement "mixed using clauses" (SCL-22384, SCL-22190)
          // For example the type of foo3 here should not be treated as () => Int but as Int with missing () arguments
          // def foo3(using String)(): Int = 1
          // foo3
          val explicitParamClauses = function.paramClauses.clauses.filterNot(_.isImplicit)
          explicitParamClauses.size == 1 && explicitParamClauses.head.parameters.isEmpty
        }
      case _ => false
    }

    override protected def isFixable(parent: PsiElement): Boolean = parent match {
      case _: ScPrefixExpr => false
      case _ => super.isFixable(parent)
    }
  }

  object EmptyParenMethod {
    val InstanceForAnnotator = new EmptyParenMethod
  }

  @tailrec
  private def findCall(element: PsiElement): Boolean = element.getContext match {
    case _: ScMethodCall => true
    case expression: ScParenthesisedExpr => findCall(expression)
    case call: ScGenericCall => findCall(call)
    case _ => false
  }

  private object HasFunctionType {

    def unapply(expression: ScExpression): Option[Seq[ScType]] = {
      implicit val context: Context = Context(expression)

      expression match {
        case result.Typeable(api.FunctionType(_, seq)) => Some(seq)
        case _ => None
      }
    }
  }
}
