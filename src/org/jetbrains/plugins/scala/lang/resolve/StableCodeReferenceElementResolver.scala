package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.psi.impl.source.resolve.ResolveCache
import processor._
import psi.api.base.patterns.{ScConstructorPattern, ScInfixPattern}
import psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import psi.api.base.ScStableCodeReferenceElement

class StableCodeReferenceElementResolver(reference: ResolvableStableCodeReferenceElement) extends ResolveCache.PolyVariantResolver[ScStableCodeReferenceElement] {
  def resolve(ref: ScStableCodeReferenceElement, incomplete: Boolean) = {
    val kinds = ref.getKinds(false)
    val proc = ref.getContext match {
    //last ref may import many elements with the same name
      case e: ScImportExpr if (e.selectorSet == None && !e.singleWildcard) => new CollectAllProcessor(kinds, ref, reference.refName)
      case e: ScImportExpr if e.singleWildcard => new ResolveProcessor(kinds, ref, reference.refName)
      case _: ScImportSelector => new CollectAllProcessor(kinds, ref, reference.refName)

      case constr: ScConstructorPattern => new ExtractorResolveProcessor(ref, reference.refName, kinds, constr.expectedType)
      case infix: ScInfixPattern => new ExtractorResolveProcessor(ref, reference.refName, kinds, infix.expectedType)
      case _ => new ResolveProcessor(kinds, ref, reference.refName)
    }
    reference.doResolve(ref, proc)
  }
}
