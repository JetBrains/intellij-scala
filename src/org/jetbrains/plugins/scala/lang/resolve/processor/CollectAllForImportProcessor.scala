package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem

import scala.collection.Set

class CollectAllForImportProcessor(override val kinds: Set[ResolveTargets.Value],
                                   override val ref: PsiElement,
                                   override val name: String)
                                  (implicit override val typeSystem: TypeSystem)
  extends ResolveProcessor(kinds, ref, name) {
  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      val accessible = isAccessible(named, ref)
      if (accessibility && !accessible) return true
      named match {
        case pack: PsiPackage =>
          candidatesSet += new ScalaResolveResult(ScPackageImpl(pack), getSubst(state), getImports(state),
            isAccessible = true)
        case _ =>  candidatesSet += new ScalaResolveResult(named, getSubst(state), getImports(state),
          boundClass = getBoundClass(state), fromType = getFromType(state), isAccessible = true)
      }
    }
    true
  }
}
