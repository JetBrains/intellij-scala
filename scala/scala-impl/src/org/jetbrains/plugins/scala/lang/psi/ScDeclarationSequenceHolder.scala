package org.jetbrains.plugins.scala.lang.psi

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.scope._
import org.jetbrains.plugins.scala.lang.psi.ScDeclarationSequenceHolder.processSyntheticsForTopLevelDefinition
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaredElementsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum, ScGivenDefinition, ScMember, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor

trait ScDeclarationSequenceHolder extends ScalaPsiElement {
  override def processDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement,
    place: PsiElement
  ): Boolean = {
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
        case m: ScMember =>
          if (!processSyntheticsForTopLevelDefinition(m, processor, state))
            return false
        case _ =>
      }

      e match {
        case c: ScClass =>
          processor.execute(c, state)
          if (isOkForFakeCompanionModule(c)) {
            processor.execute(c.fakeCompanionModule.get, state)
          }
          true
        case t: ScTrait =>
          processor.execute(t, state)
          if (isOkForFakeCompanionModule(t)) {
            processor.execute(t.fakeCompanionModule.get, state)
          }
          true
        case named: ScNamedElement =>
          processor.execute(named, state)
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
          case id: ScStableCodeReference => run match {
            case po: ScObject if po.isPackageObject && id.qualName == po.qualifiedName => // do nothing
            case _ => if (!processElement(run, state)) return false
          }
          case _ => if (!processElement(run, state)) return false
        }
        run = run.getPrevSibling
      }

      //forward references are allowed (e.g. 2 local methods see each other)
      run = lastParent.getNextSibling
      val forwardState = state.withForwardRef
      while (run != null) {
        ProgressManager.checkCanceled()
        if (!processElement(run, forwardState)) return false
        run = run.getNextSibling
      }
    }
    true
  }
}

object ScDeclarationSequenceHolder {

  /** @return false to stop processing */
  private[psi] def processSyntheticsForTopLevelDefinition(definition: ScMember, processor: PsiScopeProcessor, state: ResolveState): Boolean = {
    val synthetics: Iterable[ScMember] = definition match {
      case cls: ScClass => cls.getSyntheticImplicitMethod
      case e: ScEnum => e.syntheticClass
      case gvn: ScGivenDefinition => gvn.desugaredDefinitions
      case _ => None
    }
    val stopAt = synthetics.find { member =>
      !processor.execute(member, state)
    }
    stopAt.isEmpty
  }
}