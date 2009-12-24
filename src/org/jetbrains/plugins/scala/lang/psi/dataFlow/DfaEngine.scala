package org.jetbrains.plugins.scala.lang.psi.dataFlow

import collection.mutable.HashMap
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction

/**
 * @author ilyas
 */

final class DfaEngine[E](cfg: Seq[Instruction],
                   dfa: DfaInstance[E],
                   l: Semilattice[E]) {

  def performDFA: collection.mutable.Map[Instruction, E] = {
    val initial = for (v <- cfg) yield (v, l.bottom) // (vertex, after)
    val after = HashMap(initial: _*)
    val forward = dfa.isForward

    def traverse(workList: Set[Instruction]): Unit = if (!workList.isEmpty) {
      val v = workList.head
      var newWorkList = workList - v

      val fv = dfa.fun(v)
      val newAfter = fv(l.join((if (forward) v.pred else v.succ).map(after(_))))
      if (!l.eq(newAfter, after(v))) {
        after(v) = newAfter
        newWorkList ++= (if (forward) v.succ else v.pred)
      }
      traverse(newWorkList)
    }

    traverse(Set(cfg: _*))
    after
  }
}