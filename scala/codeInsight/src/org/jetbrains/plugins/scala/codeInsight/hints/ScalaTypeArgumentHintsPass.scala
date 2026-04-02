package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.annotator.hints.Hint.HintPosition
import org.jetbrains.plugins.scala.annotator.hints.{Hint, Text}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightSettings
import org.jetbrains.plugins.scala.extensions.{IterableExt, ObjectExt, PsiElementExt, Resolved}
import org.jetbrains.plugins.scala.incremental.Highlighting.ElementHighlightingExt
import org.jetbrains.plugins.scala.lang.psi.PsiElementContext
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructorInvocation, ScMethodLike, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScParenthesisedExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintSystem, Context, ScAbstractType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}

private[codeInsight] trait ScalaTypeArgumentHintsPass {
  protected def collectTypeArgumentHints(editor: Editor, root: PsiFile): Iterator[Hint] =
    if (ScalaHintsSettings.xRayMode && ScalaApplicationSettings.XRAY_SHOW_TYPE_ARGUMENT_HINTS) doCollectTypeArgumentHints(editor, root)
    else Iterator.empty

  private def doCollectTypeArgumentHints(editor: Editor, root: PsiFile): Iterator[Hint] = root.elements(_.isVisible(editor.getProject, root)).flatMap {
    case ci@ScConstructorInvocation.reference(Resolved(r@ScalaResolveResult(TypeParamsOfMethodLike(typeParams), _))) =>
      r.applicabilityConstraints.flatMap { cs =>
        xRayTypeArgumentsHints(ci.typeElement, cs, typeParams, editor)
      }.getOrElse(Seq.empty)
    case CallWithTypeArguments(invoked, typeParams, methodCalls) =>
      val cs = methodCalls
        .flatMap { mc =>
          for {
            typePoly <- mc.getNonValueType(fromUnderscore = true).toOption.flatMap(_.asOptionOf[ScTypePolymorphicType])
            matchedParameters = mc.matchedParameters
            inferRes = InferUtil.localTypeInferenceWithApplicabilityExt(
              typePoly.internalType,
              matchedParameters.map(_._2),
              matchedParameters.map(_._1),
              typePoly.typeParameters
            )
          } yield inferRes._2.constraints
        }
        .foldLeft(ConstraintSystem.empty)(_ + _)
      val hints = xRayTypeArgumentsHints(invoked, cs, typeParams, editor)
      hints.getOrElse(Seq.empty)
    case _ => Seq.empty
  }

  private def xRayTypeArgumentsHints(invoked: PsiElement, cs: ConstraintSystem, typeParams: Seq[ScTypeParam], editor: Editor) = {
    cs.substitutionBounds(canThrowSCE = false)(invoked, Context(invoked)).map { bounds =>
      def typeParamSubst(tp: ScTypeParam) = {
        bounds.substitutor(ScAbstractType(TypeParameter(tp), tp.lowerBound.getOrNothing, tp.upperBound.getOrAny))
      }

      typeParams.map { tp =>
        val ty = typeParamSubst(tp).removeAbstracts
        textPartsOf(ty, ScalaCodeInsightSettings.getInstance.presentationLength, invoked)(editor.getColorsScheme, PsiElementContext(invoked))
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

  private object TypeParamsOfMethodLike {
    def unapply(e: ScMethodLike): Option[Seq[ScTypeParam]] = {
      val typeParams = getTypeParameters(e)
      if (typeParams.isEmpty) None
      else Some(typeParams)
    }
  }

  private object CallWithTypeArguments {
    def unapply(call: MethodInvocation): Option[(PsiElement, Seq[ScTypeParam], List[MethodInvocation])] = {
      for {
        TypeParamsOfMethodLike(typeParams) <- call.target.map(_.element)
      } yield (call.getInvokedExpr, typeParams, call :: collectMethodCalls(call.getParent))
    }

    private def collectMethodCalls(call: PsiElement): List[MethodInvocation] = {
      call match {
        case parenthesis: ScParenthesisedExpr =>
          collectMethodCalls(parenthesis.getParent)
        case invocation: MethodInvocation if invocation.target.isEmpty =>
          invocation :: collectMethodCalls(invocation.getParent)
        case _ =>
          Nil
      }
    }
  }

  private def getTypeParameters(function: ScMethodLike): Seq[ScTypeParam] = function match {
    case fun: ScFunction if !fun.isConstructor => fun.typeParameters
    case _: ScFunction | _: ScPrimaryConstructor =>
      function.containingClass match {
        case td: ScTypeDefinition => td.typeParameters
        case _ => Seq.empty
      }
    case _ => Seq.empty
  }
}
