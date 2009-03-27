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
      var run = lastParent
      while (run != null) {
        if (!processElement(run, processor, state)) return false
        run = run.getPrevSibling
      }

      //forward references are allowed (e.g. 2 local methods see each other), with highlighting errors in case of var/vals
      run = lastParent.getNextSibling
      while (run != null) {
        if (!processElement(run, processor, state)) return false
        run = run.getNextSibling
      }
    }
    true
  }

  private def processElement(e : PsiElement, processor: PsiScopeProcessor, state : ResolveState) : Boolean = e match {
    case named: ScNamedElement => processor.execute(named, state)
    case holder: ScDeclaredElementsHolder => {
      for (declared <- holder.declaredElements) {
        if (!processor.execute(declared, state)) return false
      }
      true
    }
    case _ => true
  }
}