package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScInfixTypeElement, ScParameterizedTypeElement, ScParenthesisedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.{ScDerivesBlockImpl, ScExtendsBlockImpl}
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScExtendsBlockStubImpl

import scala.annotation.tailrec

/**
 * @author ilyas
 */
sealed abstract class ScExtendsBlockElementType(debugName: String)
  extends ScStubElementType[ScExtendsBlockStub, ScExtendsBlock](debugName) {

  override final def serialize(stub: ScExtendsBlockStub,
                               dataStream: StubOutputStream): Unit = {
    dataStream.writeNames(stub.baseClasses)
  }

  override final def deserialize(dataStream: StubInputStream,
                                 parentStub: StubElement[_ <: PsiElement]) = new ScExtendsBlockStubImpl(
    parentStub,
    this,
    baseClasses = dataStream.readNames
  )

  override final def createStubImpl(block: ScExtendsBlock,
                                    parentStub: StubElement[_ <: PsiElement]) = new ScExtendsBlockStubImpl(
    parentStub,
    this,
    baseClasses = directSupersNames(block)
  )

  protected def directSupersNames(block: ScExtendsBlock): Array[String]
}

object ExtendsBlock extends ScExtendsBlockElementType("extends block") {

  private val DefaultParents = Seq("Object")
  private val CaseClassDefaults = DefaultParents :+ "Product" :+ "Serializable"

  override def createElement(node: ASTNode) = new ScExtendsBlockImpl(null, null, node)

  override def createPsi(stub: ScExtendsBlockStub) = new ScExtendsBlockImpl(stub, this, null)

  override def indexStub(stub: ScExtendsBlockStub, sink: IndexSink): Unit = {
    sink.occurrences(index.ScalaIndexKeys.SUPER_CLASS_NAME_KEY, stub.baseClasses: _*)
  }

  override protected def directSupersNames(block: ScExtendsBlock): Array[String] = {
    @tailrec
    def refName(te: ScTypeElement): Option[String] = {
      te match {
        case simpleType: ScSimpleTypeElement => simpleType.reference.map(_.refName)
        case infixType: ScInfixTypeElement => Option(infixType.operation).map(_.refName)
        case x: ScParameterizedTypeElement => refName(x.typeElement)
        case x: ScParenthesisedTypeElement =>
          x.innerElement match {
            case Some(e) => refName(e)
            case _ => None
          }
        case _ => None
      }
    }

    block.templateParents match {
      case Some(parents) =>
        val default = if (block.isUnderCaseClass) CaseClassDefaults else DefaultParents

        val parentElements = parents.typeElements
        (parentElements.flatMap(refName) ++ default).toArray
      case None => Array.empty
    }
  }

}

object DerivesBlock extends ScExtendsBlockElementType("derives block") {

  override def createElement(node: ASTNode) = new ScDerivesBlockImpl(null, null, node)

  override def createPsi(stub: ScExtendsBlockStub) = new ScDerivesBlockImpl(stub, this, null)

  override protected def directSupersNames(block: ScExtendsBlock): Array[String] = Array.empty
}
