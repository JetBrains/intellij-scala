package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.base.patterns.{ScNamingPatternImpl, ScReferencePatternImpl, ScSeqWildcardPatternImpl, ScTypedPatternImpl}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScBindingPatternStub
import org.jetbrains.plugins.scala.lang.psi.stubs.factories.ScStubSerializingElementFactory
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScBindingPatternStubImpl

abstract class ScBindingPatternElementType[P <: ScBindingPattern](debugName: String)
  extends ScStubElementType[P](debugName)

abstract class ScBindingPatternStubFactory[P <: ScBindingPattern](elementType: ScBindingPatternElementType[P])
  extends ScStubSerializingElementFactory[ScBindingPatternStub[P], P](elementType) {

  override def serialize(stub: ScBindingPatternStub[P], dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScBindingPatternStub[P] =
    new ScBindingPatternStubImpl[P](parentStub, elementType, name = dataStream.readNameString)

  override def createStubImpl(psi: P, parentStub: StubElement[_ <: PsiElement]): ScBindingPatternStub[P] =
    new ScBindingPatternStubImpl[P](parentStub, elementType, psi.name)
}

final class ScReferencePatternElementType extends ScBindingPatternElementType[ScReferencePattern]("reference pattern") {
  override def createElement(node: ASTNode): ScReferencePattern =
    new ScReferencePatternImpl(node)
}

final class ScReferencePatternStubFactory(elementType: ScReferencePatternElementType)
  extends ScBindingPatternStubFactory[ScReferencePattern](elementType) {
  override def createPsi(stub: ScBindingPatternStub[ScReferencePattern]): ScReferencePattern =
    new ScReferencePatternImpl(stub)
}

final class ScTypedPatternElementType extends ScBindingPatternElementType[ScTypedPattern]("typed pattern") {
  override def createElement(node: ASTNode): ScTypedPattern =
    new ScTypedPatternImpl(node)
}

final class ScTypedPatternStubFactory(elementType: ScTypedPatternElementType)
  extends ScBindingPatternStubFactory[ScTypedPattern](elementType) {
  override def createPsi(stub: ScBindingPatternStub[ScTypedPattern]): ScTypedPattern =
    new ScTypedPatternImpl(stub)
}

final class ScNamingPatternElementType extends ScBindingPatternElementType[ScNamingPattern]("naming pattern") {
  override def createElement(node: ASTNode): ScNamingPattern =
    new ScNamingPatternImpl(node)
}

final class ScNamingPatternStubFactory(elementType: ScNamingPatternElementType)
  extends ScBindingPatternStubFactory[ScNamingPattern](elementType) {
  override def createPsi(stub: ScBindingPatternStub[ScNamingPattern]): ScNamingPattern =
    new ScNamingPatternImpl(stub)
}

final class ScSeqWildcardPatternElementType extends ScBindingPatternElementType[ScSeqWildcardPattern]("seq wildcard pattern") {
  override def createElement(node: ASTNode): ScSeqWildcardPattern =
    new ScSeqWildcardPatternImpl(node)
}

final class ScSeqWildcardPatternStubFactory(elementType: ScSeqWildcardPatternElementType)
  extends ScBindingPatternStubFactory[ScSeqWildcardPattern](elementType) {
  override def createPsi(stub: ScBindingPatternStub[ScSeqWildcardPattern]): ScSeqWildcardPattern =
    new ScSeqWildcardPatternImpl(stub)
}
