package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScExtractorPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeArgument
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScGenericCall
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import org.jetbrains.plugins.scala.lang.resolve.processor._

class StableCodeReferenceResolver(
  ref:                   ScStableCodeReference,
  shapeResolve:          Boolean,
  allConstructorResults: Boolean,
  noConstructorResolve:  Boolean
) {

  private def processMethodTypeParameters(
    elem: PsiNamedElement,
    name: String
  ): Option[ScalaResolveResult] =
    elem match {
      case fn: ScFunction =>
        val typeParams      = fn.typeParameters
        val targetTypeParam = typeParams.find(_.name == name)
        targetTypeParam.map(new ScalaResolveResult(_))
      case _ => None
    }

  private def processNamedTypeArgument(targ: ScTypeArgument, name: String): Array[ScalaResolveResult] =
    targ.getContext.getContext match {
      case gCall: ScGenericCall =>
        val resolveResults =
          if (shapeResolve) gCall.shapeMultiResolve.getOrElse(Array.empty)
          else              gCall.multiResolve.getOrElse(Array.empty)

        resolveResults match {
          case Array(srr) =>
            val targetTypeParam = processMethodTypeParameters(srr.element, name)
            targetTypeParam match {
              case Some(typeParam) => Array(typeParam)
              case None => //Check type parameters of apply method candidate (if present)
                val applyMethodTypeParam =
                  srr.innerResolveResult.flatMap(
                    innerSrr =>
                      processMethodTypeParameters(innerSrr.element, name)
                  )

                applyMethodTypeParam.toArray
            }
          case _ => ScalaResolveResult.EMPTY_ARRAY
        }
      case _ => ScalaResolveResult.EMPTY_ARRAY
    }


  final def resolve(): Array[ScalaResolveResult] = {
    val refName = ref.refName

    ref.getContext match {
      case targ: ScTypeArgument =>
        return processNamedTypeArgument(targ, refName)
      case _ => ()
    }

    val kinds = getKindsFor(ref)

    val proc = if (ref.isConstructorReference && !noConstructorResolve) {
      val constr   = ref.getConstructorInvocation.get
      val typeArgs = constr.typeArgList.map(_.typeArgsWithNamed).getOrElse(Seq.empty)

      val effectiveArgs = constr.arguments.toList.map(_.exprs) match {
        case List() => List(List())
        case x      => x
      }

      new ConstructorResolveProcessor(ref, refName, effectiveArgs, typeArgs, kinds, shapeResolve, allConstructorResults)
    } else ref.getContext match {
      //last ref may import many elements with the same name
      case e: ScImportExpr =>
        if (e.selectorSet.isEmpty && !e.hasWildcardSelector)
          new CollectAllForImportProcessor(kinds, ref, refName)
        else
          new ResolveProcessor(kinds, ref, refName)

      case sel: ScImportSelector if !sel.isWildcardSelector =>
        new CollectAllForImportProcessor(kinds, ref, refName)
      case constr: ScExtractorPattern =>
        new ExtractorResolveProcessor(ref, refName, kinds, constr.expectedType)
      case _ =>
        new ResolveProcessor(kinds, ref, refName)
    }

    ref.doResolve(proc)
  }

  protected def getKindsFor(ref: ScStableCodeReference): Set[ResolveTargets.Value] =
    ref.getKinds(incomplete = false)
}
