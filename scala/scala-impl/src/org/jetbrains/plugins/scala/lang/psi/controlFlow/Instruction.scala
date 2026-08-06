package org.jetbrains.plugins.scala.lang.psi.controlFlow

import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait Instruction {
  def succ: Iterable[Instruction]
  def pred: Iterable[Instruction]

  val num: Int

  @Nullable
  def element: Option[ScalaPsiElement]
}