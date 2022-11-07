package org.jetbrains.plugins.scala.lang.resolve.processor

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt


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
        case _ => (namedElement, state.fromType)
      }

      val result = new ScalaResolveResult(target, state.substitutor, state.importsUsed, fromType = fromType, isAccessible = true)
      candidatesSet = candidatesSet + result
    }

    true
  }
}
