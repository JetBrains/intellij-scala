package org.jetbrains.plugins.scala.lang.psi.controlFlow

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}

/**
 * @author ilyas
 */

object ControlFlowUtil {

  /**
   * Detects connected components in a control-flow graph
   */
  def detectConnectedComponents(cfg: Seq[Instruction]): Seq[collection.Set[Instruction]] = {
    val mainSeq = new ListBuffer[Instruction]
    mainSeq ++= cfg
    mainSeq.sortBy(_.num)
    var buffer = new ArrayBuffer[collection.Set[Instruction]]

    @tailrec
    def inner(next: Iterable[Instruction], currentSet: mutable.HashSet[Instruction]): Unit = {
      if (next.isEmpty) {
        buffer += currentSet
        mainSeq --= currentSet
      } else {
        val currentSucc = new ArrayBuffer[Instruction]
        for (n <- next if !currentSet.contains(n)) {
          currentSucc ++= n.succ
          currentSet += n
        }
        inner(currentSucc, currentSet)
      }
    }

    while (mainSeq.nonEmpty) {
      mainSeq.headOption match {
        case Some(h) => inner(Seq(h), new mutable.HashSet[Instruction])
        case None =>
      }
    }
    buffer

  }

}