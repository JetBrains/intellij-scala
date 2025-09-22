package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.hints.Hint.HintPosition
import org.jetbrains.plugins.scala.annotator.hints.{Hint, Text}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightSettings
import org.jetbrains.plugins.scala.extensions.{&, IterableExt, ObjectExt, PsiElementExt, Resolved}
import org.jetbrains.plugins.scala.incremental.Highlighting.ElementHighlightingExt
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructorInvocation, ScMethodLike, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintSystem, Context, ScAbstractType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}

import scala.annotation.tailrec

private[codeInsight] trait ScalaTypeArgumentHintsPass {
  protected def collectTypeArgumentHints(editor: Editor, root: PsiElement): Iterator[Hint] =
    if (ScalaApplicationSettings.XRAY_SHOW_TYPE_ARGUMENT_HINTS) doCollectTypeArgumentHints(editor, root)
    else Iterator.empty

  private def doCollectTypeArgumentHints(editor: Editor, root: PsiElement): Iterator[Hint] = root.elements(_.isVisible).flatMap {
    case ci@ScConstructorInvocation.reference(Resolved(r@ScalaResolveResult(fun: ScMethodLike, _)))
      if getTypeArguments(fun).nonEmpty =>
      r.resultUndef.flatMap { cs =>
        xRayTypeArgumentsHints(ci.typeElement, cs, fun, editor)
      }.getOrElse(Seq.empty)
    case outermostMethodCall@ScMethodCall.withDeepestInvoked((invoked: ScReferenceExpression) & Resolved(ScalaResolveResult(methodLike: ScMethodLike, _)))
      if !outermostMethodCall.getParent.is[ScMethodCall] && getTypeArguments(methodLike).nonEmpty =>
      val cs = collectMethodCalls(outermostMethodCall)
        .flatMap { mc =>
          for {
            typePoly <- mc.getNonValueType(fromUnderscore = true).toOption.flatMap(_.asOptionOf[ScTypePolymorphicType])
            inferRes = InferUtil.localTypeInferenceWithApplicabilityExt(
              typePoly.internalType,
              mc.matchedParameters.map(_._2),
              mc.matchedParameters.map(_._1),
              typePoly.typeParameters
            )
          } yield inferRes._2.constraints
        }
        .foldLeft(ConstraintSystem.empty)(_ + _)
      val hints = xRayTypeArgumentsHints(invoked, cs, methodLike, editor)
      hints.getOrElse(Seq.empty)
    case _ => Seq.empty
  }

  private def xRayTypeArgumentsHints(invoked: PsiElement, cs: ConstraintSystem, fun: ScMethodLike, editor: Editor) = {
    cs.substitutionBounds(canThrowSCE = false)(invoked, Context(invoked)).map { bounds =>
      def typeParamSubst(tp: ScTypeParam) = {
        bounds.substitutor(ScAbstractType(TypeParameter(tp), tp.lowerBound.getOrNothing, tp.upperBound.getOrAny))
      }

      getTypeArguments(fun).map { tp =>
        val ty = typeParamSubst(tp).removeAbstracts
        textPartsOf(ty, ScalaCodeInsightSettings.getInstance.presentationLength, invoked)(editor.getColorsScheme, TypePresentationContext(invoked), Context(invoked))
      }
    }.map(texts =>
      Seq(
        Hint(
          Text("[") +: texts.intersperse(Seq(Text(", "))).flatten :+ Text("]"),
          invoked,
          position = HintPosition.AfterElement,
          relatesToPrecedingElement = true
        )
      )
    )
  }

  private def getTypeArguments(function: ScMethodLike) = function match {
    case fun: ScFunction if !fun.isConstructor => fun.typeParameters
    case _: ScFunction | _: ScPrimaryConstructor =>
      function.containingClass match {
        case td: ScTypeDefinition => td.typeParameters
        case _ => Seq.empty
      }
    case _ => Seq.empty
  }

  private def collectMethodCalls(expr: ScExpression): List[ScMethodCall] = {
    @tailrec
    def loop(current: ScExpression, acc: List[ScMethodCall]): List[ScMethodCall] = current match {
      case call: ScMethodCall =>
        loop(call.getEffectiveInvokedExpr, call :: acc)
      case _ => acc
    }

    loop(expr, Nil)
  }
}
