package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.psi._
import collection.Set
import psi.impl.ScPackageImpl

class CollectAllProcessor(override val kinds: Set[ResolveTargets.Value],
                          override val ref: PsiElement,
                          override val name: String) extends ResolveProcessor(kinds, ref, name) {
  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      if (!isAccessible(named, ref)) return true
      named match {
        case pack: PsiPackage =>
          candidatesSet += new ScalaResolveResult(ScPackageImpl(pack), getSubst(state), getImports(state))
        case _ =>  candidatesSet += new ScalaResolveResult(named, getSubst(state), getImports(state),
          boundClass = getBoundClass(state))
      }
    }
    true
  }
}
