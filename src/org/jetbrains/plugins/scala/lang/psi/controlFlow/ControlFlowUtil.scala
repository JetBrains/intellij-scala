package org.jetbrains.plugins.scala.lang.psi.controlFlow

import collection.mutable.{ListBuffer, HashSet, ArrayBuffer}

/**
 * @author ilyas
 */

object ControlFlowUtil {

  /**
   * Detects connected components in a control-flow graph
   */
  def detectConnectedComponents(cfg: Seq[Instruction]): Seq[Iterable[Instruction]] = {
    val mainSeq = new ListBuffer[Instruction]
    mainSeq ++= cfg
    mainSeq.sortBy(_.num)
    var buffer = new ArrayBuffer[Iterable[Instruction]]

    def inner(next: Iterable[Instruction], currentSet: HashSet[Instruction]): Unit = {
      if (next.isEmpty) {
        buffer + currentSet
        mainSeq --= currentSet
      } else {
        val currentSucc = new ArrayBuffer[Instruction]
        for (n <- next if !currentSet.contains(n)) {
          currentSucc ++= n.succ
          currentSet + n
        }
        inner(currentSucc, currentSet)
      }
    }

    while (!mainSeq.isEmpty) {
      mainSeq.headOption match {
        case Some(h) => inner(Seq(h), new HashSet[Instruction])
        case None =>
      }
    }
    buffer

  }

}