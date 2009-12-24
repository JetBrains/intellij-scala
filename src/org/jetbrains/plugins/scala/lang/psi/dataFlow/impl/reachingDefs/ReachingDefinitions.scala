package org.jetbrains.plugins.scala.lang.psi.dataFlow
package impl.reachingDefs

import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction
import collection.Iterable
import org.jetbrains.plugins.scala.lang.psi.controlFlow.impl.{ReadWriteVariableInstruction, DefineValueInstruction}
import com.intellij.psi.PsiElement
/**
 * @author ilyas
 */

object ReachingDefinitions {

  /**
   * Semilattice element type:
   * the set of assignments
   */
  type A = Set[Instruction]

  class ReachingDefinitionsInstance extends DfaInstance[A] {
    def isForward = true
    val fun = (i: Instruction) => (a: A) => i match {
      case dv: DefineValueInstruction => a + dv
      case wr@ReadWriteVariableInstruction(_, ref, true) => {
        val target: PsiElement = ref.resolve

        def previousAssignments(i: Instruction) = i match {
          case DefineValueInstruction(_, named, true) => named eq target
          case ReadWriteVariableInstruction(_, ref1, true) => ref1.resolve eq target
          case _ => false
        }

        a.filterNot(previousAssignments) + wr
      }
      case _ => a
    }
  }

  class ReachingDefinitionsLattice extends Semilattice[A] {
    val bottom: A = Set()

    def join(ins: Iterable[A]) = ins.foldLeft(bottom)(_ ++ _)

    def eq(e1: A, e2: A) = e1 == e2 // todo is this correct?
  }

}