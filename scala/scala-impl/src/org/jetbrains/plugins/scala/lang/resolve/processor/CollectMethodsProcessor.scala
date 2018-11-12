package org.jetbrains.plugins.scala.lang.resolve.processor

import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.CachesUtil._
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}

/**
 * @author Alexander Podkhalyuzin
 */

class CollectMethodsProcessor(place: PsiElement, name: String)
        extends ResolveProcessor(StdKinds.methodsOnly, place, name) {

  override protected def execute(namedElement: PsiNamedElement)
                                (implicit state: ResolveState): Boolean = {
    if (nameMatches(namedElement)) {
      val accessible = isAccessible(namedElement, ref)
      if (accessibility && !accessible) return true

      namedElement match {
        case m: PsiMethod =>
          addResult(new ScalaResolveResult(m,
            substitutor = getSubst(state),
            importsUsed = getImports(state),
            implicitConversionClass = Option(state.get(IMPLICIT_RESOLUTION)),
            implicitConversion = Option(state.get(IMPLICIT_FUNCTION)),
            implicitType = Option(state.get(IMPLICIT_TYPE)),
            isAccessible = accessible))
        case _ =>
      }
    }

    true
  }
}