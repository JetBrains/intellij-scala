package org.jetbrains.plugins.scala
package lang
package resolve

import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScInfixPattern, ScInterpolationPattern}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import org.jetbrains.plugins.scala.lang.resolve.processor._

class StableCodeReferenceResolver(reference: ScStableCodeReference, shapeResolve: Boolean,
                                  allConstructorResults: Boolean, noConstructorResolve: Boolean) {

  def resolve(ref: ScStableCodeReference, incomplete: Boolean): Array[ScalaResolveResult] = {
    val kinds = getKindsFor(ref)
    val proc = if (ref.isConstructorReference && !noConstructorResolve) {
      val constr = ref.getConstructorInvocation.get
      val typeArgs = constr.typeArgList.map(_.typeArgs).getOrElse(Seq())
      val effectiveArgs = constr.arguments.toList.map(_.exprs) match {
        case List() => List(List())
        case x => x
      }
      new ConstructorResolveProcessor(ref, ref.refName, effectiveArgs, typeArgs, kinds, shapeResolve, allConstructorResults)
    } else ref.getContext match {
      //last ref may import many elements with the same name
      case e: ScImportExpr if e.selectorSet.isEmpty && !e.hasWildcardSelector =>
        new CollectAllForImportProcessor(kinds, ref, reference.refName)
      case e: ScImportExpr if e.hasWildcardSelector =>
        new ResolveProcessor(kinds, ref, reference.refName)
      case _: ScImportSelector =>
        new CollectAllForImportProcessor(kinds, ref, reference.refName)
      case constr: ScInterpolationPattern =>
        new ExtractorResolveProcessor(ref, reference.refName, kinds, constr.expectedType)
      case constr: ScConstructorPattern =>
        new ExtractorResolveProcessor(ref, reference.refName, kinds, constr.expectedType)
      case infix: ScInfixPattern =>
        new ExtractorResolveProcessor(ref, reference.refName, kinds, infix.expectedType)
      case _ =>
        new ResolveProcessor(kinds, ref, reference.refName)
    }

    reference.doResolve(proc)
  }

  protected def getKindsFor(ref: ScStableCodeReference): Set[ResolveTargets.Value] =
    ref.getKinds(incomplete = false)
}
