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
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScExtendsBlockElementType.directSupersNames
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScExtendsBlockStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.SUPER_CLASS_NAME_KEY

import scala.annotation.tailrec
import scala.collection.Seq

/**
  * @author ilyas
  */
class ScExtendsBlockElementType extends ScStubElementType[ScExtendsBlockStub, ScExtendsBlock]("extends block") {

  override def serialize(stub: ScExtendsBlockStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeNames(stub.baseClasses)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScExtendsBlockStub = {
    new ScExtendsBlockStubImpl(parentStub, this,
      baseClassesRefs = dataStream.readNames)
  }

  override def createStubImpl(block: ScExtendsBlock, parentStub: StubElement[_ <: PsiElement]): ScExtendsBlockStub =
    new ScExtendsBlockStubImpl(parentStub, this,
      baseClassesRefs = directSupersNames(block).toArray.asReferences)

  override def indexStub(stub: ScExtendsBlockStub, sink: IndexSink): Unit =
    this.indexStub(stub.baseClasses, sink, SUPER_CLASS_NAME_KEY)

  override def createElement(node: ASTNode): ScExtendsBlock = new ScExtendsBlockImpl(node)

  override def createPsi(stub: ScExtendsBlockStub): ScExtendsBlock = new ScExtendsBlockImpl(stub)
}

private object ScExtendsBlockElementType {
  def directSupersNames(extBlock: ScExtendsBlock): Seq[String] = {

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

    def default: Seq[String] = if (extBlock.isUnderCaseClass) caseClassDefaults else defaultParents

    extBlock.templateParents match {
      case None => Seq.empty
      case Some(parents) =>
        val parentElements: Seq[ScTypeElement] = parents.typeElements
        parentElements.flatMap(refName) ++ default
    }
  }

  private val defaultParents = "Object" :: "ScalaObject" :: Nil
  private val caseClassDefaults = defaultParents ::: "Product" :: "Serializable" :: Nil

}
