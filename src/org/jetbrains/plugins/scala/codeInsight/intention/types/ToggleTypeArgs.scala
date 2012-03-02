package org.jetbrains.plugins.scala
package codeInsight
package intention
package types

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import lang.psi.api.expr.{ScGenericCall, ScMethodCall, ScReferenceExpression}
import lang.psi.types.result.TypingContext
import lang.psi.types.nonvalue.ScTypePolymorphicType
import extensions._
import com.intellij.psi.{PsiTypeParameterListOwner, PsiElement}
import lang.psi.ScalaPsiUtil
import lang.psi.types.ScType
import lang.psi.impl.ScalaPsiElementFactory

// TODO move messages to the localisation bundle.
class ToggleTypeArgs extends PsiElementBaseIntentionAction {
  def getFamilyName = "Toggle Type Args"

  def isAvailable(project: Project, editor: Editor, element: PsiElement) = {
    check(element) match {
      case MethodCallWithInferedTypeArgs(call, inferred) =>
        val args = inferred.map(ScType.presentableText).mkString("[", ", ", "]")
        setText("Explicitly provide inferred type arguments: %s".format(args))
        true
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    check(element) match {
      case MethodCallWithInferedTypeArgs(call, inferred) =>
        val methodCallText = call.getText
        val x = call.getInvokedExpr.getTextLength
        val (start, end) = methodCallText.splitAt(x)
        val args = inferred.map(ScType.canonicalText).mkString("[", ", ", "]")
        val newMethodCallText = start + args + end
        val newMethodCall = ScalaPsiElementFactory.createExpressionFromText(newMethodCallText, call.getManager)
        call.replace(newMethodCall)
      case Other =>
    }
  }

  private def check(element: PsiElement): TypeArgResult = {
    val refExprOpt = for {
      re <- element.getContext.asOptionOf[ScReferenceExpression]
    } yield re

    val genericCallOpt = for {
      re <- refExprOpt
      gc <- re.getContext.asOptionOf[ScGenericCall]
    } yield gc

    val referencedTypeParamOwner = for {
      re <- refExprOpt
      resolveResult <- re.advancedResolve
      typeParamOwner <- resolveResult.element.asOptionOf[PsiTypeParameterListOwner] // TODO follow aliases
    } yield (resolveResult, typeParamOwner)

    val typeParamsOpt = for {
      re <- refExprOpt
      mc <- re.getContext.asOptionOf[ScMethodCall]
      mcTpe <- mc.getNonValueType(TypingContext.empty).toOption
      tpTpe <- mcTpe.asOptionOf[ScTypePolymorphicType]
      val pts = tpTpe.polymorphicTypeSubstitutor
    } yield (mc, tpTpe.typeParameters, pts)

    // TODO: calls to constructors, java generic methods/constructors.
    // TODO: detect if the provided arguments could be omitted without changing the result type of the method call,
    //       and offer to remove them.
    val result = (referencedTypeParamOwner, genericCallOpt, typeParamsOpt) match {
      case (Some((resolveResult, typeParamOwner)), None, Some((mc, typeParams, subst))) =>
        val tps = typeParamOwner.getTypeParameters
        val inferredTypes = tps.map {tp =>
          val key = (tp.name, ScalaPsiUtil.getPsiElementId(tp))
          subst.tvMap.get(key)
        }
        if (inferredTypes.contains(None)) Other else MethodCallWithInferedTypeArgs(mc, inferredTypes.map(_.get))
      case _ => Other
    }
    result
  }

  sealed abstract class TypeArgResult
  case class MethodCallWithInferedTypeArgs(call: ScMethodCall, inferredArgs: Seq[ScType]) extends TypeArgResult
  case object Other extends TypeArgResult
}