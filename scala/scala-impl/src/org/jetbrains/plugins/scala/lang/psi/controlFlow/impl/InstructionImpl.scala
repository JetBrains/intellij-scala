package org.jetbrains.plugins.scala.lang.psi.controlFlow.impl

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.controlFlow.Instruction

import scala.collection.mutable.ArrayBuffer

sealed abstract class InstructionImpl(override val num: Int,
                                      override val element: Option[ScalaPsiElement])
        extends Instruction with Cloneable {
  private val mySucc = new ArrayBuffer[Instruction]
  private val myPred = new ArrayBuffer[Instruction]

  override def pred: ArrayBuffer[Instruction] = myPred

  override def succ: ArrayBuffer[Instruction] = mySucc

  private final def addPredecessor(p: Instruction): Unit = {
    if (!myPred.contains(p))
      myPred += p
  }

  private final def addSuccessor(s: Instruction): Unit = {
    if (!mySucc.contains(s))
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

object InstructionImpl {
  def addEdge(from: InstructionImpl, to: InstructionImpl): InstructionImpl = {
    if (to != null && from != null) {
      from.addSuccessor(to)
      to.addPredecessor(from)
    }
    to
  }
}

final class LiteralInstruction(num: Int, element: ScLiteral)
  extends InstructionImpl(num, Some(element)) {

  override def getPresentation: String = "Lit: " + element.getText.shorten(50)
}

final class ElementInstruction(num: Int, element: Option[ScalaPsiElement])
  extends InstructionImpl(num, element)

final case class DefinitionInstruction(namedElement: ScNamedElement,
                                       defType: DefinitionType)
                                      (num: Int)
        extends InstructionImpl(num, Some(namedElement))
{
  private val myName = namedElement.name

  def getName: String = myName

  override protected def getPresentation = s"${defType.name} $getName"
}

final case class ReadWriteVariableInstruction(ref: ScReferenceExpression,
                                              variable: Option[PsiNamedElement],
                                              write: Boolean)
                                             (num: Int)
        extends InstructionImpl(num, Some(ref))
{
  private val myName = ref.getText
  def getName: String = myName
  override protected def getPresentation: String = (if (write) "WRITE " else "READ ") + getName
}
