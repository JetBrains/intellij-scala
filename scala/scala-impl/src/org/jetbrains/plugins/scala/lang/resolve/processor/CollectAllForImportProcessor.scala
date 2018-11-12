package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl

import scala.collection.Set

class CollectAllForImportProcessor(override val kinds: Set[ResolveTargets.Value],
                                   override val ref: PsiElement,
                                   override val name: String)
  extends ResolveProcessor(kinds, ref, name) {

  override protected def execute(namedElement: PsiNamedElement)
                                (implicit state: ResolveState): Boolean = {
    if (nameMatches(namedElement)) {
      val accessible = isAccessible(namedElement, ref)
      if (accessibility && !accessible) return true
      namedElement match {
        case pack: PsiPackage =>
          candidatesSet += new ScalaResolveResult(ScPackageImpl(pack), getSubst(state), getImports(state),
            isAccessible = true)
        case _ => candidatesSet += new ScalaResolveResult(namedElement, getSubst(state), getImports(state),
          boundClass = getBoundClass(state), fromType = getFromType(state), isAccessible = true)
      }
    }

    true
  }
}
