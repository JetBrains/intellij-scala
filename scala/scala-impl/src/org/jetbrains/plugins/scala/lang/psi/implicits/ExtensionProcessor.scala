package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.psi.{PsiElement, PsiNamedElement, ResolveState}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor.ResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}

final class ExtensionProcessor(place: PsiElement, name: String, forCompletion: Boolean)
    extends ResolveProcessor(StdKinds.methodsOnly, place, name) {

  override protected def execute(
    namedElement: PsiNamedElement
  )(implicit
    state: ResolveState
  ): Boolean = {
    val isDeclaredOrExportedInExtension = ImplicitProcessor.isDeclaredOrExportedInExtension(namedElement, state)

    if ((forCompletion || nameMatches(namedElement)) && isDeclaredOrExportedInExtension){
      addResult(
        new ScalaResolveResult(
          namedElement,
          renamed                  = state.renamed,
          substitutor              = state.substitutor,
          importsUsed              = state.importsUsed,
          implicitConversion       = state.implicitConversion,
          implicitType             = state.implicitType,
          implicitScopeObject      = state.implicitScopeObject,
          unresolvedTypeParameters = state.unresolvedTypeParams,
          isExtensionCall          = true,
          exportedIn               = state.exportedIn
        )
      )
    }

    true
  }
}
