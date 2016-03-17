package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.psi.impl.source.resolve.ResolveCache
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScInfixPattern, ScInterpolationPattern}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.resolve.processor._

class StableCodeReferenceElementResolver(reference: ResolvableStableCodeReferenceElement, shapeResolve: Boolean,
                                          allConstructorResults: Boolean, noConstructorResolve: Boolean)
        extends ResolveCache.PolyVariantResolver[ScStableCodeReferenceElement] {
  def resolve(ref: ScStableCodeReferenceElement, incomplete: Boolean) = {
    import ref.typeSystem
    val kinds = getKindsFor(ref)
    val proc = if (ref.isConstructorReference && !noConstructorResolve) {
      val constr = ref.getConstructor.get
      val typeArgs = constr.typeArgList.map(_.typeArgs).getOrElse(Seq())
      val effectiveArgs = constr.arguments.toList.map(_.exprs.map(new Expression(_))) match {
        case List() => List(List())
        case x => x
      }
      new ConstructorResolveProcessor(ref, ref.refName, effectiveArgs, typeArgs, kinds, shapeResolve, allConstructorResults)
    } else ref.getContext match {
      //last ref may import many elements with the same name
      case e: ScImportExpr if e.selectorSet.isEmpty && !e.singleWildcard =>
        new CollectAllForImportProcessor(kinds, ref, reference.refName)
      case e: ScImportExpr if e.singleWildcard => new ResolveProcessor(kinds, ref, reference.refName)
      case _: ScImportSelector => new CollectAllForImportProcessor(kinds, ref, reference.refName)
      case constr: ScInterpolationPattern =>
        new ExtractorResolveProcessor(ref, reference.refName, kinds, constr.expectedType)
      case constr: ScConstructorPattern =>
        new ExtractorResolveProcessor(ref, reference.refName, kinds, constr.expectedType)
      case infix: ScInfixPattern => new ExtractorResolveProcessor(ref, reference.refName, kinds, infix.expectedType)
      case _ => new ResolveProcessor(kinds, ref, reference.refName)
    }

    reference.doResolve(ref, proc)
  }

  protected def getKindsFor(ref: ScStableCodeReferenceElement) = ref.getKinds(incomplete = false)
}
