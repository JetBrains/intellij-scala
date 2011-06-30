package org.jetbrains.plugins.scala
package lang
package psi

import api.base.ScStableCodeReferenceElement
import api.statements.ScDeclaredElementsHolder
import api.toplevel.ScNamedElement
import api.toplevel.typedef.{ScClass, ScObject}
import com.intellij.psi._
import psi.impl.ScalaFileImpl
import scope._
import com.intellij.psi.stubs.StubElement
import com.intellij.openapi.progress.ProgressManager
import collection.Seq

trait ScDeclarationSequenceHolder extends ScalaPsiElement {
  override def processDeclarations(processor: PsiScopeProcessor,
      state : ResolveState,
      lastParent: PsiElement,
      place: PsiElement): Boolean = {

    if (lastParent != null) {
      var run = lastParent
      while (run != null) {
        ProgressManager.checkCanceled()
        place match {
          case id: ScStableCodeReferenceElement => run match {
            case po: ScObject if po.isPackageObject && id.qualName == po.getQualifiedName => // do nothing
            case _ => if (!processElement(run, processor, state)) return false
          }
          case _ => if (!processElement(run, processor, state)) return false
        }
        run = ScalaPsiUtil.getPrevStubOrPsiElement(run)
      }

      //forward references are allowed (e.g. 2 local methods see each other)
      run = ScalaPsiUtil.getNextStubOrPsiElement(lastParent)
      while (run != null) {
        ProgressManager.checkCanceled()
        if (!processElement(run, processor, state)) return false
        run = ScalaPsiUtil.getNextStubOrPsiElement(run)
      }
    }
    true
  }

  private def processElement(e : PsiElement, processor: PsiScopeProcessor, state : ResolveState) : Boolean = e match {
    case c: ScClass if c.isCase && c.fakeCompanionModule != None =>
      processor.execute(c, state)
      processor.execute(c.fakeCompanionModule.get, state)
    case named: ScNamedElement => processor.execute(named, state)
    case holder: ScDeclaredElementsHolder => {
      val elements: Seq[PsiNamedElement] = holder.declaredElements
      var i = 0
      while (i < elements.length) {
        ProgressManager.checkCanceled()
        if (!processor.execute(elements(i), state)) return false
        i = i + 1
      }
      true
    }
    case _ => true
  }
}