package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.impl.statements._
import org.jetbrains.plugins.scala.lang.psi.stubs.factories.ScStubSerializingElementFactory
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScPropertyStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.{ScPropertyStub, classNames}

sealed abstract class ScPropertyElementType[P <: ScValueOrVariable](debugName: String)
  extends ScStubElementType[P](debugName)

sealed abstract class ScPropertyStubFactory[P <: ScValueOrVariable](elementType: ScPropertyElementType[P])
  extends ScStubSerializingElementFactory[ScPropertyStub[P], P](elementType) {

  override final def serialize(stub: ScPropertyStub[P], dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.isDeclaration)
    dataStream.writeBoolean(stub.isImplicit)
    dataStream.writeNames(stub.names)
    dataStream.writeOptionName(stub.typeText)
    dataStream.writeOptionName(stub.bodyText)
    dataStream.writeBoolean(stub.isLocal)
    dataStream.writeNames(stub.classNames)
    dataStream.writeBoolean(stub.isTopLevel)
    dataStream.writeOptionName(stub.topLevelQualifier)
  }

  override final def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScPropertyStub[P] =
    new ScPropertyStubImpl(
      parentStub,
      elementType,
      isDeclaration     = dataStream.readBoolean,
      isImplicit        = dataStream.readBoolean,
      names             = dataStream.readNames,
      typeText          = dataStream.readOptionName,
      bodyText          = dataStream.readOptionName,
      isLocal           = dataStream.readBoolean,
      classNames        = dataStream.readNames,
      isTopLevel        = dataStream.readBoolean,
      topLevelQualifier = dataStream.readOptionName
    )

  override final def createStubImpl(property: P, parentStub: StubElement[_ <: PsiElement]): ScPropertyStub[P] =
    new ScPropertyStubImpl(
      parentStub,
      elementType,
      isDeclaration     = property.isInstanceOf[ScVariableDeclaration],
      isImplicit        = property.hasModifierProperty("implicit"),
      names             = property.declaredNames.toArray,
      typeText          = property.typeElement.map(_.getText),
      bodyText          = body(property).map(_.getText),
      isLocal           = property.containingClass == null,
      classNames        = property.typeElement.toArray.flatMap(classNames),
      isTopLevel        = property.isTopLevel,
      topLevelQualifier = property.topLevelQualifier
    )

  override final def indexStub(stub: ScPropertyStub[P], sink: IndexSink): Unit = {
    import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys._
    sink.occurrences(PROPERTY_NAME_KEY, stub.names.toSeq: _*)
    sink.occurrences(PROPERTY_CLASS_NAME_KEY, stub.classNames.toSeq: _*)

    if (stub.isTopLevel){
      stub.topLevelQualifier.foreach(
        sink.fqnOccurence(TOP_LEVEL_VAL_OR_VAR_BY_PKG_KEY, _)
      )
    }

    stub.indexImplicits(sink)
  }

  protected def body(property: P): Option[ScExpression] = None
}

final class ValueDeclaration extends ScPropertyElementType[ScValueDeclaration]("value declaration") {
  override def createElement(node: ASTNode): ScValueDeclaration = new ScValueDeclarationImpl(null, null, node)
}

final class ScValueDeclarationStubFactory(elementType: ValueDeclaration)
  extends ScPropertyStubFactory[ScValueDeclaration](elementType) {
  override def createPsi(stub: ScPropertyStub[ScValueDeclaration]): ScValueDeclaration =
    new ScValueDeclarationImpl(stub, elementType, null)
}

final class ValueDefinition extends ScPropertyElementType[ScPatternDefinition]("value definition") {
  override def createElement(node: ASTNode): ScPatternDefinition = new ScPatternDefinitionImpl(null, null, node)
}

final class ScValueDefinitionStubFactory(elementType: ValueDefinition)
  extends ScPropertyStubFactory[ScPatternDefinition](elementType) {
  override def createPsi(stub: ScPropertyStub[ScPatternDefinition]): ScPatternDefinition =
    new ScPatternDefinitionImpl(stub, elementType, null)

  override protected def body(property: ScPatternDefinition): Option[ScExpression] = property.expr
}

final class VariableDeclaration extends ScPropertyElementType[ScVariableDeclaration]("variable declaration") {
  override def createElement(node: ASTNode): ScVariableDeclaration = new ScVariableDeclarationImpl(null, null, node)
}

final class ScVariableDeclarationStubFactory(elementType: VariableDeclaration)
  extends ScPropertyStubFactory[ScVariableDeclaration](elementType) {
  override def createPsi(stub: ScPropertyStub[ScVariableDeclaration]): ScVariableDeclaration =
    new ScVariableDeclarationImpl(stub, elementType, null)
}

final class VariableDefinition extends ScPropertyElementType[ScVariableDefinition]("variable definition") {
  override def createElement(node: ASTNode): ScVariableDefinition = new ScVariableDefinitionImpl(null, null, node)
}

final class ScVariableDefinitionStubFactory(elementType: VariableDefinition)
  extends ScPropertyStubFactory[ScVariableDefinition](elementType) {
  override def createPsi(stub: ScPropertyStub[ScVariableDefinition]): ScVariableDefinition =
    new ScVariableDefinitionImpl(stub, elementType, null)

  override protected def body(property: ScVariableDefinition): Option[ScExpression] = property.expr
}
