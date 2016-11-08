package org.jetbrains.plugins.scala
package lang
package psi

import java.lang

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.scope._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaredElementsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScTrait, ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor

import scala.collection.Seq

trait ScDeclarationSequenceHolder extends ScalaPsiElement {
  override def processDeclarations(processor: PsiScopeProcessor,
      state : ResolveState,
      lastParent: PsiElement,
      place: PsiElement): Boolean = {
    def processElement(e: PsiElement, state: ResolveState): Boolean = {
      def isOkCompanionModule = {
        processor match {
          case b: BaseProcessor =>
            b.kinds.contains(ResolveTargets.OBJECT) || b.kinds.contains(ResolveTargets.VAL)
          case _ => true
        }
      }

      def isOkForFakeCompanionModule(t: ScTypeDefinition): Boolean = {
        isOkCompanionModule && t.fakeCompanionModule.isDefined
      }

      e match {
        case c: ScClass =>
          processor.execute(c, state)
          if (isOkForFakeCompanionModule(c)) {
            processor.execute(c.fakeCompanionModule.get, state)
          }
          c.getSyntheticImplicitMethod match {
            case Some(impl) => if (!processElement(impl, state)) return false
            case _ =>
          }
          true
        case t: ScTrait =>
          processor.execute(t, state)
          if (isOkForFakeCompanionModule(t)) {
            processor.execute(t.fakeCompanionModule.get, state)
          }
          true
        case named: ScNamedElement => processor.execute(named, state)
        case holder: ScDeclaredElementsHolder =>
          val elements: Seq[PsiNamedElement] = holder.declaredElements
          var i = 0
          while (i < elements.length) {
            ProgressManager.checkCanceled()
            if (!processor.execute(elements(i), state)) return false
            i = i + 1
          }
          true
        case _ => true
      }
    }

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
            case _ => if (!processElement(run, state)) return false
          }
          case _ => if (!processElement(run, state)) return false
        }
        run = run.getPrevSibling
      }

      //forward references are allowed (e.g. 2 local methods see each other)
      run = lastParent.getNextSibling
      val forwardState = state.put(BaseProcessor.FORWARD_REFERENCE_KEY, lang.Boolean.TRUE)
      while (run != null) {
        ProgressManager.checkCanceled()
        if (!processElement(run, forwardState)) return false
        run = run.getNextSibling
      }
    }
    true
  }
}