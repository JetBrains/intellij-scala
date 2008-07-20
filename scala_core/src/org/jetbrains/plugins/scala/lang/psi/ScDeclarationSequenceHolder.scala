package org.jetbrains.plugins.scala.lang.psi

import api.statements.ScDeclaredElementsHolder
import api.toplevel.ScNamedElement
import com.intellij.psi._
import scope._

trait ScDeclarationSequenceHolder extends ScalaPsiElement {
  override def processDeclarations(processor: PsiScopeProcessor,
      state : ResolveState,
      lastParent: PsiElement,
      place: PsiElement): Boolean = {
    import org.jetbrains.plugins.scala.lang.resolve._

    if (lastParent != null) {
      var run = lastParent.getPrevSibling
      while (run != null) {
        run match {
          case named : ScNamedElement => if (!processor.execute(named, state)) return false
          case holder : ScDeclaredElementsHolder => for (declared <- holder.declaredElements) {
            if (!processor.execute(declared, state)) return false
          }
          case _ =>
        }
        run = run.getPrevSibling
      }
    }
    true
  }
}