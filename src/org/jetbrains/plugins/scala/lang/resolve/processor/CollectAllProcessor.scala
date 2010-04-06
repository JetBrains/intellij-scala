package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.psi._
import collection.Set
import processor.ResolveProcessor

class CollectAllProcessor(override val kinds: Set[ResolveTargets.Value],
                          override val ref: PsiElement,
                          override val name: String) extends ResolveProcessor(kinds, ref, name) {
  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      if (!isAccessible(named, ref)) return true
      candidatesSet += new ScalaResolveResult(named, getSubst(state), getImports(state))
    }
    true
  }
}
