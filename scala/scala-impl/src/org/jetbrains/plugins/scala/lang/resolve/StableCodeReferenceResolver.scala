package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScInfixPattern, ScInterpolationPattern}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import org.jetbrains.plugins.scala.lang.resolve.processor._

class StableCodeReferenceResolver(
  ref: ScStableCodeReference,
  shapeResolve: Boolean,
  allConstructorResults: Boolean,
  noConstructorResolve: Boolean
) {

  final def resolve(): Array[ScalaResolveResult] = {
    val kinds = getKindsFor(ref)
    val refName = ref.refName

    val proc = if (ref.isConstructorReference && !noConstructorResolve) {
      val constr = ref.getConstructorInvocation.get
      val typeArgs = constr.typeArgList.map(_.typeArgs).getOrElse(Seq())
      val effectiveArgs = constr.arguments.toList.map(_.exprs) match {
        case List() => List(List())
        case x => x
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
      case constr: ScInterpolationPattern =>
        new ExtractorResolveProcessor(ref, refName, kinds, constr.expectedType)
      case constr: ScConstructorPattern =>
        new ExtractorResolveProcessor(ref, refName, kinds, constr.expectedType)
      case infix: ScInfixPattern =>
        new ExtractorResolveProcessor(ref, refName, kinds, infix.expectedType)
      case _ =>
        new ResolveProcessor(kinds, ref, refName)
    }

    ref.doResolve(proc)
  }

  protected def getKindsFor(ref: ScStableCodeReference): Set[ResolveTargets.Value] =
    ref.getKinds(incomplete = false)
}
