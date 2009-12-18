package org.jetbrains.plugins.scala.lang.psi.controlFlow

import org.jetbrains.annotations.Nullable
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement


/**
 * @author ilyas
 */

trait Instruction {
  def succ( /*put call env here*/ ): Iterable[Instruction]
  def pred( /*put call env here*/ ): Iterable[Instruction]

  def addSucc(s: Instruction) : Unit
  def addPred(p: Instruction) : Unit

  val num: Int

  @Nullable
  def element: Option[ScalaPsiElement]
}