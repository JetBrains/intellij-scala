package org.jetbrains.plugins.scala.lang.psi.controlFlow
package impl

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

import scala.collection.mutable.ArrayBuffer

/**
 * @author ilyas
 */

sealed class InstructionImpl(override val num: Int,
                             override val element: Option[ScalaPsiElement])
        extends Instruction with Cloneable {
  private val mySucc = new ArrayBuffer[Instruction]
  private val myPred = new ArrayBuffer[Instruction]

  override def pred: ArrayBuffer[Instruction] = myPred

  override def succ: ArrayBuffer[Instruction] = mySucc

  override def addPred(p: Instruction): Unit = {
    myPred += p
  }

  override def addSucc(s: Instruction): Unit = {
    mySucc += s
  }


  override def toString: String = {
    val builder = new StringBuilder
    builder.append(num)
    builder.append("(")
    for (i <- mySucc.indices) {
      if (i > 0) builder.append(",")
      builder.append(mySucc(i).num)
    }
    builder.append(") ").append(getPresentation)
    builder.toString()
  }

  protected def getPresentation: String = "element: " + (element match {
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
  override protected def getPresentation: String = (if (write) "WRITE " else "READ ") + getName
}