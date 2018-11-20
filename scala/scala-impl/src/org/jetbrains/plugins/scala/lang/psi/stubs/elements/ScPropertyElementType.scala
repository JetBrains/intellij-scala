package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScIdList, ScPatternList}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.impl.statements._
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScPropertyStubImpl

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
    dataStream.writeName(stub.bindingsContainerText)
    dataStream.writeBoolean(stub.isLocal)
  }

  override final def deserialize(dataStream: StubInputStream,
                                 parentStub: StubElement[_ <: PsiElement]) = new ScPropertyStubImpl(
    parentStub,
    this,
    isDeclaration = dataStream.readBoolean,
    isImplicit = dataStream.readBoolean,
    namesRefs = dataStream.readNames,
    typeTextRef = dataStream.readOptionName,
    bodyTextRef = dataStream.readOptionName,
    containerTextRef = dataStream.readName,
    isLocal = dataStream.readBoolean
  )

  override protected final def createStubImpl(property: P,
                                              parentStub: StubElement[_ <: PsiElement]) = new ScPropertyStubImpl(
    parentStub,
    this,
    isDeclaration = property.isInstanceOf[ScVariableDeclaration],
    isImplicit = property.hasModifierProperty("implicit"),
    namesRefs = property.declaredNames.asReferences,
    typeTextRef = property.typeElement.map(_.getText).asReference,
    bodyTextRef = body(property).map(_.getText).asReference,
    containerTextRef = StringRef.fromString(container(property).getText),
    isLocal = property.containingClass == null
  )

  override final def indexStub(stub: ScPropertyStub[P], sink: IndexSink): Unit = {
    import index.ScalaIndexKeys._
    sink.occurrences(PROPERTY_NAME_KEY, stub.names: _*)
    IMPLICITS_KEY.occurence(sink)
  }

  protected def body(property: P): Option[ScExpression] = None

  protected def container(property: P): ScalaPsiElement
}

object ValueDeclaration extends ScPropertyElementType[ScValueDeclaration]("value declaration") {

  override def createElement(node: ASTNode) =
    new ScValueDeclarationImpl(null, null, node)

  override def createPsi(stub: ScPropertyStub[ScValueDeclaration]) =
    new ScValueDeclarationImpl(stub, this, null)

  override protected def container(property: ScValueDeclaration): ScIdList =
    property.getIdList
}

object ValueDefinition extends ScPropertyElementType[ScPatternDefinition]("value definition") {

  override def createElement(node: ASTNode) =
    new ScPatternDefinitionImpl(null, null, node)

  override def createPsi(stub: ScPropertyStub[ScPatternDefinition]) =
    new ScPatternDefinitionImpl(stub, this, null)

  override protected def body(property: ScPatternDefinition): Option[ScExpression] =
    property.expr

  override protected def container(property: ScPatternDefinition): ScPatternList =
    property.pList
}

object VariableDeclaration extends ScPropertyElementType[ScVariableDeclaration]("variable declaration") {

  override def createElement(node: ASTNode) =
    new ScVariableDeclarationImpl(null, null, node)

  override def createPsi(stub: ScPropertyStub[ScVariableDeclaration]) =
    new ScVariableDeclarationImpl(stub, this, null)

  override protected def container(property: ScVariableDeclaration): ScIdList =
    property.getIdList
}

object VariableDefinition extends ScPropertyElementType[ScVariableDefinition]("variable definition") {

  override def createElement(node: ASTNode) =
    new ScVariableDefinitionImpl(null, null, node)

  override def createPsi(stub: ScPropertyStub[ScVariableDefinition]) =
    new ScVariableDefinitionImpl(stub, this, null)

  override protected def body(property: ScVariableDefinition): Option[ScExpression] =
    property.expr

  override protected def container(property: ScVariableDefinition): ScPatternList =
    property.pList
}