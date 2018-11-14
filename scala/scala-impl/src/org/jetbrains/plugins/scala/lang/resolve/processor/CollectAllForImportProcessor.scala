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
      val (target, fromType) = namedElement match {
        case pack: PsiPackage => (ScPackageImpl(pack), None)
        case _ => (namedElement, getFromType(state))
      }

      candidatesSet += new ScalaResolveResult(target, getSubst(state), getImports(state), fromType = fromType, isAccessible = true)
    }

    true
  }
}
