package org.jetbrains.plugins.scala.lang.psi.stubs.factories

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScNamingPattern, ScReferencePattern, ScSeqWildcardPattern, ScTypedPattern}
import org.jetbrains.plugins.scala.lang.psi.impl.base.patterns.{ScNamingPatternImpl, ScReferencePatternImpl, ScSeqWildcardPatternImpl, ScTypedPatternImpl}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScBindingPatternStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.{ScBindingPatternElementType, ScNamingPatternElementType, ScReferencePatternElementType, ScSeqWildcardPatternElementType, ScTypedPatternElementType}
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScBindingPatternStubImpl

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

final class ScReferencePatternStubFactory(elementType: ScReferencePatternElementType)
  extends ScBindingPatternStubFactory[ScReferencePattern](elementType) {
  override def createPsi(stub: ScBindingPatternStub[ScReferencePattern]): ScReferencePattern =
    new ScReferencePatternImpl(stub)
}

final class ScNamingPatternStubFactory(elementType: ScNamingPatternElementType)
  extends ScBindingPatternStubFactory[ScNamingPattern](elementType) {
  override def createPsi(stub: ScBindingPatternStub[ScNamingPattern]): ScNamingPattern =
    new ScNamingPatternImpl(stub)
}

final class ScTypedPatternStubFactory(elementType: ScTypedPatternElementType)
  extends ScBindingPatternStubFactory[ScTypedPattern](elementType) {
  override def createPsi(stub: ScBindingPatternStub[ScTypedPattern]): ScTypedPattern =
    new ScTypedPatternImpl(stub)
}

final class ScSeqWildcardPatternStubFactory(elementType: ScSeqWildcardPatternElementType)
  extends ScBindingPatternStubFactory[ScSeqWildcardPattern](elementType) {
  override def createPsi(stub: ScBindingPatternStub[ScSeqWildcardPattern]): ScSeqWildcardPattern =
    new ScSeqWildcardPatternImpl(stub)
}
