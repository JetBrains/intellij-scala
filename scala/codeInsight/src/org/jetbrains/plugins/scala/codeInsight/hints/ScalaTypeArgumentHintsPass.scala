package org.jetbrains.plugins.scala.codeInsight.hints

import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.annotator.hints.Hint.HintPosition
import org.jetbrains.plugins.scala.annotator.hints.{Hint, Text}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightSettings
import org.jetbrains.plugins.scala.extensions.{IterableExt, ObjectExt, PsiElementExt, Resolved}
import org.jetbrains.plugins.scala.incremental.Highlighting.ElementHighlightingExt
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructorInvocation, ScMethodLike}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression, ScGenericCall, ScParenthesisedExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScSignatureClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScTypeParam, ScTypeParamClause, TypeParamIdOwner}
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.{Compatibility, ConstraintSystem, Context, ScAbstractType, ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}

private[codeInsight] trait ScalaTypeArgumentHintsPass {
  import ScalaTypeArgumentHintsPass._

  protected def collectTypeArgumentHints(editor: Editor, root: PsiFile): Iterator[Hint] =
    if (ScalaHintsSettings.xRayMode && ScalaApplicationSettings.XRAY_SHOW_TYPE_ARGUMENT_HINTS) doCollectTypeArgumentHints(editor, root)
    else Iterator.empty

  private def doCollectTypeArgumentHints(editor: Editor, root: PsiFile): Iterator[Hint] =
    root
      .elements(_.isVisible(editor.getProject, root))
      .flatMap {
        case ci @ ScConstructorInvocation.reference(Resolved(r @ ScalaResolveResult(cons: ScMethodLike, _))) =>
          val (_, applicability, _) = Compatibility.checkConstructorApplicability(ci, cons, r)(ci)

          xRayTypeArgumentHint(
            TypeArgumentHint.Bracketed(
              ci.typeElement,
              typeArgumentsFromConstraints(
                ci.typeElement,
                applicability.constraints,
                cons.getConstructorTypeParameters
              )
            ),
            editor
          ).getOrElse(Seq.empty)
        case GenericCallWithInferredNamedTypeArguments(hint) =>
          xRayTypeArgumentHint(hint, editor).getOrElse(Seq.empty)
        case CallWithInferredTypeArguments(hints) =>
          hints.flatMap { hint =>
            xRayTypeArgumentHint(hint, editor).getOrElse(Seq.empty)
          }
        case _ => Seq.empty
      }
}

private object ScalaTypeArgumentHintsPass {
  private def typeArgumentsFromConstraints(invoked: PsiElement, cs: ConstraintSystem, typeParams: Seq[ScTypeParam]): Seq[ScType] = {
    if (cs.isEmpty) Seq.empty
    else
      cs
        .substitutionBounds(canThrowSCE = false)(invoked, Context(invoked))
        .toSeq
        .flatMap { bounds =>
          typeParams.map { tp =>
            val typeParam = ScAbstractType(TypeParameter(tp), tp.lowerBound.getOrNothing, tp.upperBound.getOrAny)
            bounds.substitutor(typeParam).removeAbstracts
          }
        }
  }

  private def xRayTypeArgumentHint(hint: TypeArgumentHint, editor: Editor) = {
    def typeTextParts(tpe: ScType, anchor: PsiElement): Seq[Text] =
      textPartsOf(
        tpe,
        ScalaCodeInsightSettings.getInstance.presentationLength,
        anchor
      )(editor.getColorsScheme, TypePresentationContext(anchor), Context(anchor))

    val parts = hint match {
      case TypeArgumentHint.Bracketed(anchor, typeArguments) =>
        Option.when(typeArguments.nonEmpty) {
          val texts = typeArguments.map(typeTextParts(_, anchor))
          Text("[") +: texts.intersperse(Seq(Text(", "))).flatten :+ Text("]")
        }
      case TypeArgumentHint.NamedSuffix(anchor, namedParts) =>
        Option.when(namedParts.nonEmpty) {
          val texts = namedParts.map { part =>
            Text(s"${part.name} = ") +: typeTextParts(part.tpe, anchor)
          }
          Text(", ") +: texts.intersperse(Seq(Text(", "))).flatten
        }
    }

    parts.map { parts =>
      Seq(
        Hint(
          parts,
          hint.anchor,
          position = HintPosition.AfterElement,
          relatesToPrecedingElement = true
        )
      )
    }
  }

  private sealed trait TypeArgumentHint {
    def anchor: PsiElement
  }

  private object TypeArgumentHint {
    final case class NamedPart(name: String, tpe: ScType)

    final case class Bracketed(
      anchor:        PsiElement,
      typeArguments: Seq[ScType]
    ) extends TypeArgumentHint

    final case class NamedSuffix(
      anchor: PsiElement,
      parts:  Seq[NamedPart]
    ) extends TypeArgumentHint
  }

  private object GenericCallWithInferredNamedTypeArguments {
    def unapply(genCall: ScGenericCall): Option[TypeArgumentHint] =
      if (!genCall.typeArgs.hasNamedTypeArgs) None
      else {
        InferredMethodTypeArguments
          .methodCallsFor(genCall)
          .flatMap { case (function, methodCalls) =>
            InferredMethodTypeArguments.inferredTypeArgumentsFor(function, methodCalls, genCall)
          }
      }
  }

  private object CallWithInferredTypeArguments {
    def unapply(call: MethodInvocation): Option[Seq[TypeArgumentHint]] =
      InferredMethodTypeArguments
        .methodCallsFor(call)
        .map { case (function, methodCalls) =>
          InferredMethodTypeArguments.inferTypeArgumentsByDeclaredTypeClause(function, methodCalls)
        }.filter(_.nonEmpty)
  }

  private object InferredMethodTypeArguments {
    private def collectNestedMethodCalls(element: PsiElement): List[MethodInvocation] =
      element match {
        case parenthesised: ScParenthesisedExpr => collectNestedMethodCalls(parenthesised.getParent)
        case gen: ScGenericCall                 => collectNestedMethodCalls(gen.getParent)
        case invocation: MethodInvocation if invocation.target.isEmpty =>
          invocation :: collectNestedMethodCalls(invocation.getParent)
        case _ => Nil
      }

    def methodCallsFor(call: MethodInvocation): Option[(ScFunction, List[MethodInvocation])] =
      call.getInvokedExpr match {
        case genCall: ScGenericCall =>
          methodCallsFor(genCall)
        case _ =>
          call.target.map(_.element).collect {
            case function: ScFunction if !function.isConstructor =>
              function -> (call :: collectNestedMethodCalls(call.getParent))
          }
      }

    def methodCallsFor(genCall: ScGenericCall): Option[(ScFunction, List[MethodInvocation])] = {
      def nestedInvocation(element: PsiElement): Option[MethodInvocation] =
        element match {
          case invocation: MethodInvocation       => Option(invocation)
          case parenthesised: ScParenthesisedExpr => parenthesised.innerElement.flatMap(nestedInvocation)
          case gen: ScGenericCall                 => nestedInvocation(gen.referencedExpr)
          case _                                  => None
        }

      val targetCall =
        nestedInvocation(genCall.referencedExpr).orElse {
          genCall.getParent.asOptionOf[MethodInvocation].filter(_.getInvokedExpr == genCall)
        }

      for {
        call     <- targetCall
        function <- functionFor(call)
      } yield function -> (call :: collectNestedMethodCalls(call.getParent))
    }

    private def functionFor(expr: ScExpression): Option[ScFunction] =
      expr match {
        case ref: ScReferenceExpression =>
          ref.bind().map(_.element).collect {
            case function: ScFunction if !function.isConstructor => function
          }
        case gen: ScGenericCall                 => functionFor(gen.referencedExpr)
        case invocation: MethodInvocation       => functionFor(invocation.getEffectiveInvokedExpr)
        case parenthesised: ScParenthesisedExpr => parenthesised.innerElement.flatMap(functionFor)
        case _                                  => None
      }

    def inferredTypeArgumentsFor(
      function:    ScFunction,
      methodCalls: List[MethodInvocation],
      genCall:     ScGenericCall
    ): Option[TypeArgumentHint] = {
      val inferredByTypeParamId = inferredTypeArgumentsById(methodCalls)
      val explicitNames         = genCall.typeArgs.namedTypeArgs.flatMap(_.name)

      def isExplicit(typeParam: ScTypeParam): Boolean =
        explicitNames.exists(name => ScalaNamesUtil.equivalent(typeParam.name, name))

      typeClauseFor(function, methodCalls, genCall).flatMap { clause =>
        val parts = clause.typeParameters.flatMap { typeParam =>
          Option.when(!isExplicit(typeParam)) {
            inferredByTypeParamId
              .get(typeParam.typeParamId)
              .map(TypeArgumentHint.NamedPart(typeParam.name, _))
          }.flatten
        }

        genCall.typeArguments.lastOption
          .filter(_ => parts.nonEmpty)
          .map(TypeArgumentHint.NamedSuffix(_, parts))
      }
    }

    def inferTypeArgumentsByDeclaredTypeClause(
      function:    ScFunction,
      methodCalls: List[MethodInvocation]
    ): Seq[TypeArgumentHint] = {
      val inferredByTypeParamId = inferredTypeArgumentsById(methodCalls)

      typeClausesWithAnchors(function, methodCalls).flatMap { case (clause, anchor) =>
        val typeArguments = clause.typeParameters.flatMap { typeParam =>
          inferredByTypeParamId.get(typeParam.typeParamId)
        }

        val isExistingNamedClause = anchor match {
          case gen: ScGenericCall => gen.typeArgs.hasNamedTypeArgs
          case _                  => false
        }

        Option.when(typeArguments.nonEmpty && !isExistingNamedClause)(
          TypeArgumentHint.Bracketed(anchor, typeArguments)
        )
      }
    }

    private def inferredTypeArgumentsById(methodCalls: List[MethodInvocation]): Map[Long, ScType] =
      methodCalls
        .flatMap(_.matchedTypeParameters)
        .map { case (tpe, typeParameter) => typeParameter.typeParamId -> tpe }
        .toMap

    private def typeClauseFor(
      function:    ScFunction,
      methodCalls: List[MethodInvocation],
      genCall:     ScGenericCall
    ): Option[ScTypeParamClause] =
      typeClausesWithAnchors(function, methodCalls)
        .collectFirst { case (clause, `genCall`) => clause }

    private def typeClausesWithAnchors(
      function:    ScFunction,
      methodCalls: List[MethodInvocation]
    ): Seq[(ScTypeParamClause, PsiElement)] = {
      def anchorAfterExplicitTermClauses(count: Int): PsiElement =
        methodCalls.lift(count).map(_.getInvokedExpr).getOrElse(methodCalls.last)

      var explicitTermClauseCount = 0

      function.signatureClauses.flatMap {
        case ScSignatureClause.TypeClause(clause) =>
          Option(clause -> anchorAfterExplicitTermClauses(explicitTermClauseCount))
        case ScSignatureClause.TermClause(clause) =>
          val explicitArgs       = methodCalls.lift(explicitTermClauseCount).map(_.argumentExpressions)
          val omittedUsingClause = clause.hasUsingKeyword && !explicitArgs.exists(Compatibility.isExplicitUsingArgClause)

          if (!omittedUsingClause) explicitTermClauseCount += 1
          None
      }
    }
  }
}
