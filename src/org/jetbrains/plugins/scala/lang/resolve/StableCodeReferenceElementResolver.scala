package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.psi.impl.source.resolve.ResolveCache
import processor._
import psi.api.base.patterns.{ScConstructorPattern, ScInfixPattern}
import psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import psi.api.base.{ScConstructor, ScStableCodeReferenceElement}
import psi.types.Compatibility.Expression

class StableCodeReferenceElementResolver(reference: ResolvableStableCodeReferenceElement, shapeResolve: Boolean,
                                          allConstructorResults: Boolean)
        extends ResolveCache.PolyVariantResolver[ScStableCodeReferenceElement] {
  def resolve(ref: ScStableCodeReferenceElement, incomplete: Boolean) = {
    val kinds = ref.getKinds(false)

    val proc = if (ref.isConstructorReference) {
      val constr = ref.getConstructor.get
      new ConstructorResolveProcessor(ref, ref.refName, constr.arguments.toList.map(_.exprs.map(new Expression(_))),
        Seq.empty /*todo*/, kinds, shapeResolve, allConstructorResults)
    } else ref.getContext match {
      //last ref may import many elements with the same name
      case e: ScImportExpr if (e.selectorSet == None && !e.singleWildcard) =>
        new CollectAllProcessor(kinds, ref, reference.refName)
      case e: ScImportExpr if e.singleWildcard => new ResolveProcessor(kinds, ref, reference.refName)
      case _: ScImportSelector => new CollectAllProcessor(kinds, ref, reference.refName)
      case constr: ScConstructorPattern =>
        new ExtractorResolveProcessor(ref, reference.refName, kinds, constr.expectedType)
      case infix: ScInfixPattern => new ExtractorResolveProcessor(ref, reference.refName, kinds, infix.expectedType)
      case _ => new ResolveProcessor(kinds, ref, reference.refName)
    }
    reference.doResolve(ref, proc)
  }
}
