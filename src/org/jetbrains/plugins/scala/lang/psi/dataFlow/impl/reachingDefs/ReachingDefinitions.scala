package org.jetbrains.plugins.scala.lang.psi.dataFlow
package impl.reachingDefs

import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction
import collection.Iterable
import org.jetbrains.plugins.scala.lang.psi.controlFlow.impl.{ReadWriteVariableInstruction, DefineValueInstruction}

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
      case wr@ReadWriteVariableInstruction(_, _, true) => a + wr
      case _ => a
    }
  }

  class ReachingDefinitionsLattice extends Semilattice[A] {
    val bottom: A = Set()

    def join(ins: Iterable[A]) = ins.foldLeft(bottom)(_ ++ _)

    def eq(e1: A, e2: A) = e1 == e2 // todo is it correct?
  }

}