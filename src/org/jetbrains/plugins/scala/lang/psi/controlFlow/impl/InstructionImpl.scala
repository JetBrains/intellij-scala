package org.jetbrains.plugins.scala.lang.psi.controlFlow
package impl

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

import scala.collection.mutable.ArrayBuffer

/**
 * @author ilyas
 */

sealed class InstructionImpl(override val num: Int,
                             val element: Option[ScalaPsiElement])
        extends Instruction with Cloneable {
  private val mySucc = new ArrayBuffer[Instruction]
  private val myPred = new ArrayBuffer[Instruction]

  def pred(): ArrayBuffer[Instruction] = myPred

  def succ(): ArrayBuffer[Instruction] = mySucc

  def addPred(p: Instruction) {
    myPred += p
  }

  def addSucc(s: Instruction) {
    mySucc += s
  }


  override def toString: String = {
    val builder = new StringBuilder
    builder.append(num)
    builder.append("(")
    for (i <- 0 until mySucc.size) {
      if (i > 0) builder.append(",")
      builder.append(mySucc(i).num)
    }
    builder.append(") ").append(getPresentation)
    builder.toString()
  }

  protected def getPresentation = "element: " + (element match {
    case Some(x) => x
    case z => z
  })
}

case class DefinitionInstruction(override val num: Int,
                                  namedElement: ScNamedElement,
                                  defType: DefinitionType)
        extends InstructionImpl(num, Some(namedElement)) {
  private val myName = namedElement.name

  def getName: String = myName

  override protected def getPresentation = s"${defType.name} $getName"
}

case class ReadWriteVariableInstruction(override val num: Int,
                                    ref: ScReferenceExpression,
                                    variable: Option[PsiNamedElement],
                                    write: Boolean)
        extends InstructionImpl(num, Some(ref)) {
  private val myName = ref.getText
  def getName: String = myName
  override protected def getPresentation = (if (write) "WRITE " else "READ ") + getName
}