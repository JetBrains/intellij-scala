package org.jetbrains.plugins.scala.lang.psi.dataFlow.impl.reachingDefs

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScControlFlowOwner
import org.jetbrains.plugins.scala.lang.psi.dataFlow.DfaEngine
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.scala.lang.psi.controlFlow.impl.ReadWriteVariableInstruction
import com.incors.plaf.alloy.ch
import collection.mutable.ArrayBuffer
import com.intellij.psi.{PsiNamedElement, PsiElement}

/**
 * @author ilyas
 */

object ReachingDefintionsCollector {
  import ReachingDefinitions._

  /**
   * @param elements a fragment to analyze
   * @param scope since Extract Method refactoring is in fact RDC's main client, it should define a scope
   *              where to look for captured variables 
   */
  def collectVariableInfo(elements: Seq[ScalaPsiElement], scope: ScalaPsiElement): FragmentVariableInfos = {
    import PsiTreeUtil._
    val commonParent = findCommonParent(elements: _*)
    val cfowner = getParentOfType(commonParent, classOf[ScControlFlowOwner])
    val cfg = cfowner.getControlFlow
    val engine = new DfaEngine(cfg, ReachingDefinitionsInstance, ReachingDefinitionsLattice)
    val dfa = engine.performDFA
    val isInFragment = elementToScopeMapper(elements)

    // for every READ, define is it's definition in scope
    val fragmentNodes = filterByFragment(cfg, isInFragment)
    val inputInfos = getInputInfo(fragmentNodes, scope, isInFragment)

    // for every WRITE or VAL define, if it escapes `elements' or not
    // take into account scope
    // todo implement


    new FragmentVariableInfos {
      def inputVariables = inputInfos
      def outputVariables: Iterable[VariableInfo] = null // todo implement me!
    }
  }

  def elementToScopeMapper(elements: Seq[ScalaPsiElement]) = new ((PsiElement) => Boolean) {
    import collection.mutable._
    def elem2Outer: Map[PsiElement, Boolean] = new HashMap[PsiElement, Boolean]

    def apply(elem: PsiElement): Boolean = elem != null && (elem2Outer.get(elem) match {
      case Some(b) => b
      case None => {
        for (e <- elements) {
          if (PsiTreeUtil.findCommonParent(e, elem) eq e) {
            elem2Outer + (elem -> true)
            return true
          }
        }
        false
      }
    })
  }

  def filterByFragment(cfg: Seq[Instruction], checker: (PsiElement) => Boolean) = cfg.filter(i =>
    i.element match {
      case None => false
      case Some(e) => checker(e)
    })

  def getInputInfo(cfg: Seq[Instruction],
                   scope: ScalaPsiElement, 
                   checker: (PsiElement) => Boolean): Iterable[VariableInfo] = {
    val infos = new ArrayBuffer[VariableInfo]
    cfg.foreach(i => i match {
        case ReadWriteVariableInstruction(_, ref, _) => {
          ref.resolve match {
            case elem: PsiNamedElement => if (checker(elem) && (PsiTreeUtil.findCommonParent(elem, scope) eq scope)) {
              infos + new VariableInfo { def element = elem }
            }
            case _ =>
          }
        }
        case _ =>
      })
    infos
  }


}

trait FragmentVariableInfos {
  def inputVariables: Iterable[VariableInfo]

  def outputVariables: Iterable[VariableInfo]
}

trait VariableInfo {
  def element: PsiNamedElement
}