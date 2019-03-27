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
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScExtendsBlockImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScExtendsBlockStubImpl

import scala.annotation.tailrec
import scala.collection.Seq

/**
  * @author ilyas
  */
class ScExtendsBlockElementType extends ScStubElementType[ScExtendsBlockStub, ScExtendsBlock]("extends block") {

  override def serialize(stub: ScExtendsBlockStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeNames(stub.baseClasses)
  }

  override def deserialize(dataStream: StubInputStream,
                           parentStub: StubElement[_ <: PsiElement]) = new ScExtendsBlockStubImpl(
    parentStub,
    this,
    baseClasses = dataStream.readNames
  )

  override def createStubImpl(block: ScExtendsBlock,
                              parentStub: StubElement[_ <: PsiElement]) = new ScExtendsBlockStubImpl(
    parentStub,
    this,
    baseClasses = ScExtendsBlockElementType.directSupersNames(block)
  )

  override def indexStub(stub: ScExtendsBlockStub, sink: IndexSink): Unit = {
    sink.occurrences(index.ScalaIndexKeys.SUPER_CLASS_NAME_KEY, stub.baseClasses: _*)
  }

  override def createElement(node: ASTNode) = new ScExtendsBlockImpl(node)

  override def createPsi(stub: ScExtendsBlockStub) = new ScExtendsBlockImpl(stub)
}

private object ScExtendsBlockElementType {

  private def directSupersNames(extBlock: ScExtendsBlock): Array[String] = {
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

    def default = if (extBlock.isUnderCaseClass) caseClassDefaults else defaultParents

    extBlock.templateParents match {
      case None => Array.empty
      case Some(parents) =>
        val parentElements = parents.typeElements
        parentElements.flatMap(refName).toArray ++ default
    }
  }

  private val defaultParents   : Array[String] = Array("Object")
  private val caseClassDefaults: Array[String] = defaultParents :+ "Product" :+ "Serializable"

}
