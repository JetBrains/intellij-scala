package org.jetbrains.plugins.scala.lang.psi.dataFlow
package impl.reachingDefs

import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction
import org.jetbrains.plugins.scala.lang.psi.controlFlow.impl.{DefinitionInstruction, DefinitionType, ReadWriteVariableInstruction}

object ReachingDefinitions {

  /**
   * Semilattice element type:
   * the set of assignments
   */
  type RDSet = Set[Instruction]

  object ReachingDefinitionsInstance extends DfaInstance[RDSet] {
    override def isForward = true

    override def fun(i: Instruction)(set: RDSet): RDSet = i match {
      case dv: DefinitionInstruction => set + dv
      case wr@ReadWriteVariableInstruction(_, _, Some(target), true) =>

        def previousAssignments(i: Instruction) = i match {
          case DefinitionInstruction(_, named, DefinitionType.VAR) => named == target
          case ReadWriteVariableInstruction(_, _, Some(target1), true) => target1 == target
          case _ => false
        }

        set.filterNot(previousAssignments) + wr
      case _ => set
    }
  }

  object ReachingDefinitionsLattice extends Semilattice[RDSet] {
    override val bottom: RDSet = Set()

    override def join(ins: Iterable[RDSet]): RDSet = ins.foldLeft(bottom)(_ ++ _)

    override def eq(e1: RDSet, e2: RDSet): Boolean = e1 == e2 // todo is this correct?
  }

}