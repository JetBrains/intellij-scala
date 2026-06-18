package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.{PsiNamedElement, PsiTypeParameterListOwner}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScExtractorPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeArgument}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScGenericCall
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import org.jetbrains.plugins.scala.lang.resolve.processor._

class StableCodeReferenceResolver(
  ref:                   ScStableCodeReference,
  shapeResolve:          Boolean,
  allConstructorResults: Boolean,
  noConstructorResolve:  Boolean
) {

  private def processTypeParameters(
    elem: PsiNamedElement,
    name: String
  ): Option[ScalaResolveResult] =
    elem match {
      case owner: ScTypeParametersOwner =>
        val targetTypeParam = owner.typeParameters.find(_.name == name)
        targetTypeParam.map(new ScalaResolveResult(_))
      case owner: PsiTypeParameterListOwner =>
        val targetTypeParam = owner.getTypeParameters.find(_.getName == name)
        targetTypeParam.map(new ScalaResolveResult(_))
      case _ => None
    }

  private def processNamedTypeArgument(targ: ScTypeArgument, name: String): Array[ScalaResolveResult] =
    targ.getContext.getContext match {
      case gCall: ScGenericCall =>
        val targetReference = referenceTargetDeep(gCall) match {
          case Some(ref) => ref
          case _         => return ScalaResolveResult.EMPTY_ARRAY
        }

        val resolveResults =
          if (shapeResolve) targetReference.shapeResolve
          else              targetReference.multiResolveScala(incomplete = false)

        resolveResults match {
          case Array(srr) =>
            val targetTypeParam = processTypeParameters(srr.element, name)
            targetTypeParam match {
              case Some(typeParam) => Array(typeParam)
              case None => //Check type parameters of apply method candidate (if present)
                val applyMethodTypeParam =
                  srr.innerResolveResult.flatMap(
                    innerSrr =>
                      processTypeParameters(innerSrr.element, name)
                  )

                applyMethodTypeParam.toArray
            }
          case _ => ScalaResolveResult.EMPTY_ARRAY
        }
      case pte: ScParameterizedTypeElement =>
        pte.typeElement match {
          case ste: ScSimpleTypeElement =>
            ste.reference
              .flatMap(_.bind())
              .flatMap { srr =>
                processTypeParameters(srr.element, name)
                  .orElse(srr.innerResolveResult.flatMap(innerSrr => processTypeParameters(innerSrr.element, name)))
              }
              .toArray
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
      val typeArgs = constr.typeArgList.map(_.typeArguments).getOrElse(Seq.empty)

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
