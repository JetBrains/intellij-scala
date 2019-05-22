package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.impl.statements._
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScPropertyStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ImplicitInstanceIndex

/**
  * @author adkozlov
  */
sealed abstract class ScPropertyElementType[P <: ScValueOrVariable](debugName: String)
  extends ScStubElementType[ScPropertyStub[P], P](debugName) {

  override final def serialize(stub: ScPropertyStub[P],
                               dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.isDeclaration)
    dataStream.writeBoolean(stub.isImplicit)
    dataStream.writeNames(stub.names)
    dataStream.writeOptionName(stub.typeText)
    dataStream.writeOptionName(stub.bodyText)
    dataStream.writeBoolean(stub.isLocal)
    dataStream.writeOptionName(stub.implicitType)
  }

  override final def deserialize(dataStream: StubInputStream,
                                 parentStub: StubElement[_ <: PsiElement]) = new ScPropertyStubImpl(
    parentStub,
    this,
    isDeclaration = dataStream.readBoolean,
    isImplicit = dataStream.readBoolean,
    names = dataStream.readNames,
    typeText = dataStream.readOptionName,
    bodyText = dataStream.readOptionName,
    isLocal = dataStream.readBoolean,
    implicitType = dataStream.readOptionName
  )

  override protected final def createStubImpl(property: P,
                                              parentStub: StubElement[_ <: PsiElement]) =
    new ScPropertyStubImpl(
      parentStub,
      this,
      isDeclaration = property.isInstanceOf[ScVariableDeclaration],
      isImplicit = property.hasModifierProperty("implicit"),
      names = property.declaredNames.toArray,
      typeText = property.typeElement.map(_.getText),
      bodyText = body(property).map(_.getText),
      isLocal = property.containingClass == null,
      implicitType = ScImplicitInstanceStub.implicitType(property, property.typeElement)
    )

  override final def indexStub(stub: ScPropertyStub[P], sink: IndexSink): Unit = {
    import index.ScalaIndexKeys._
    sink.occurrences(PROPERTY_NAME_KEY, stub.names: _*)
    ImplicitInstanceIndex.occurrence(sink, stub.implicitType)
  }

  protected def body(property: P): Option[ScExpression] = None
}

object ValueDeclaration extends ScPropertyElementType[ScValueDeclaration]("value declaration") {

  override def createElement(node: ASTNode) =
    new ScValueDeclarationImpl(null, null, node)

  override def createPsi(stub: ScPropertyStub[ScValueDeclaration]) =
    new ScValueDeclarationImpl(stub, this, null)
}

object ValueDefinition extends ScPropertyElementType[ScPatternDefinition]("value definition") {

  override def createElement(node: ASTNode) =
    new ScPatternDefinitionImpl(null, null, node)

  override def createPsi(stub: ScPropertyStub[ScPatternDefinition]) =
    new ScPatternDefinitionImpl(stub, this, null)

  override protected def body(property: ScPatternDefinition): Option[ScExpression] =
    property.expr
}

object VariableDeclaration extends ScPropertyElementType[ScVariableDeclaration]("variable declaration") {

  override def createElement(node: ASTNode) =
    new ScVariableDeclarationImpl(null, null, node)

  override def createPsi(stub: ScPropertyStub[ScVariableDeclaration]) =
    new ScVariableDeclarationImpl(stub, this, null)
}

object VariableDefinition extends ScPropertyElementType[ScVariableDefinition]("variable definition") {

  override def createElement(node: ASTNode) =
    new ScVariableDefinitionImpl(null, null, node)

  override def createPsi(stub: ScPropertyStub[ScVariableDefinition]) =
    new ScVariableDefinitionImpl(stub, this, null)

  override protected def body(property: ScVariableDefinition): Option[ScExpression] =
    property.expr
}