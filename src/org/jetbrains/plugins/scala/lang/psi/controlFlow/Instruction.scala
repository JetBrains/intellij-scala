package org.jetbrains.plugins.scala.lang.psi.controlFlow

import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement


/**
 * @author ilyas
 */

trait Instruction {
  def succ( /*put call env here*/ ): Iterable[Instruction]
  def pred( /*put call env here*/ ): Iterable[Instruction]

  def addSucc(s: Instruction)
  def addPred(p: Instruction)

  val num: Int

  @Nullable
  def element: Option[ScalaPsiElement]
}