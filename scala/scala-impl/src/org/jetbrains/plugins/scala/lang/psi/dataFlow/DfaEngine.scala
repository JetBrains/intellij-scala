package org.jetbrains.plugins.scala.lang.psi.dataFlow

import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction

import scala.collection.mutable

/**
 * @author ilyas
 */

final class DfaEngine[E](cfg: Seq[Instruction],
                         dfa: DfaInstance[E],
                         l: Semilattice[E]) {

  def performDFA: collection.mutable.Map[Instruction, E] = {
    val initial: Seq[(Instruction, E)] = for (v <- cfg) yield (v, l.bottom) // (vertex, after)
    val after = mutable.HashMap.newBuilder.addAll(initial).result()
    val forward = dfa.isForward

    val workList: java.util.Set[Instruction] = new java.util.HashSet[Instruction](java.util.Arrays.asList(cfg.toArray : _*))
    while (!workList.isEmpty) {
      val v = workList.iterator.next
      workList.remove(v)

      val fv = dfa.fun(v) _
      val newAfter = fv(l.join((if (forward) v.pred else v.succ).map(after(_))))
      if (!l.eq(newAfter, after(v))) {
        after(v) = newAfter
        workList addAll java.util.Arrays.asList((if (forward) v.succ.toArray else v.pred.toArray): _*)
      }
    }
    after
  }
}