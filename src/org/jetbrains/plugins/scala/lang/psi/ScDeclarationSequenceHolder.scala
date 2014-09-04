package org.jetbrains.plugins.scala
package lang
package psi

import java.lang

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.scope._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaredElementsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor

import scala.collection.Seq

trait ScDeclarationSequenceHolder extends ScalaPsiElement {
  override def processDeclarations(processor: PsiScopeProcessor,
      state : ResolveState,
      lastParent: PsiElement,
      place: PsiElement): Boolean = {
    if (lastParent != null) {
      var run = lastParent match {
        case element: ScalaPsiElement => element.getDeepSameElementInContext
        case _ => lastParent
      }
      while (run != null) {
        ProgressManager.checkCanceled()
        place match {
          case id: ScStableCodeReferenceElement => run match {
            case po: ScObject if po.isPackageObject && id.qualName == po.qualifiedName => // do nothing
            case _ => if (!processElement(run, processor, state)) return false
          }
          case _ => if (!processElement(run, processor, state)) return false
        }
        run = run.getPrevSibling
      }

      //forward references are allowed (e.g. 2 local methods see each other)
      run = lastParent.getNextSibling
      val forwardState = state.put(BaseProcessor.FORWARD_REFERENCE_KEY, lang.Boolean.TRUE)
      while (run != null) {
        ProgressManager.checkCanceled()
        if (!processElement(run, processor, forwardState)) return false
        run = run.getNextSibling
      }
    }
    true
  }

  private def processElement(e : PsiElement, processor: PsiScopeProcessor, state : ResolveState) : Boolean = {
    e match {
      case c: ScClass =>
        processor.execute(c, state)
        if (c.isCase && c.fakeCompanionModule != None) {
          processor.execute(c.fakeCompanionModule.get, state)
        }
        c.getSyntheticImplicitMethod match {
          case Some(impl) => if (!processElement(impl, processor, state)) return false
          case _ =>
        }
        true
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
}