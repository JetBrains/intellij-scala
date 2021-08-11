package org.jetbrains.plugins.scala.lang.psi.controlFlow

import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement


/**
 * @author ilyas
 */

trait Instruction {
  def succ: Iterable[Instruction]
  def pred: Iterable[Instruction]

  def addSucc(s: Instruction): Unit
  def addPred(p: Instruction): Unit

  val num: Int

  @Nullable
  def element: Option[ScalaPsiElement]
}