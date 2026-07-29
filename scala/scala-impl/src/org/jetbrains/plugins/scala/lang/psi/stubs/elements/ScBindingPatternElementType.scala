package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs._
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.base.patterns.{ScNamingPatternImpl, ScReferencePatternImpl, ScSeqWildcardPatternImpl, ScTypedPatternImpl}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScBindingPatternStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScBindingPatternStubImpl

abstract class ScBindingPatternElementType[P <: ScBindingPattern](debugName: String)
  extends ScalaStubBasedElementType[ScBindingPatternStub[P], P](debugName)

abstract class ScBindingPatternStubFactory[P <: ScBindingPattern](elementType: IElementType)
  extends StubSerializingElementFactory[ScBindingPatternStub[P], P] {

  override def serialize(stub: ScBindingPatternStub[P], dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScBindingPatternStub[P] =
    new ScBindingPatternStubImpl[P](parentStub, elementType, name = dataStream.readNameString)

  override def createStub(psi: P, parentStub: StubElement[_ <: PsiElement]): ScBindingPatternStub[P] =
    ScStubElementType.Processing.run {
      new ScBindingPatternStubImpl[P](parentStub, elementType, psi.name)
    }

  override def indexStub(stub: ScBindingPatternStub[P], sink: IndexSink): Unit = {}

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}

final class ScReferencePatternElementType extends ScBindingPatternElementType[ScReferencePattern](ScReferencePatternElementType.DebugName) {
  override def createElement(node: ASTNode): ScReferencePattern =
    new ScReferencePatternImpl(node)
}

object ScReferencePatternElementType {
  val DebugName = "reference pattern"
}

class ScReferencePatternStubFactory(elementType: IElementType)
  extends ScBindingPatternStubFactory[ScReferencePattern](elementType) {
  override def createPsi(stub: ScBindingPatternStub[ScReferencePattern]): ScReferencePattern =
    new ScReferencePatternImpl(stub)

  override def getExternalId: String = s"scala.${ScReferencePatternElementType.DebugName}"
}

final class ScTypedPatternElementType extends ScBindingPatternElementType[ScTypedPattern](ScTypedPatternElementType.DebugName) {
  override def createElement(node: ASTNode): ScTypedPattern =
    new ScTypedPatternImpl(node)
}

object ScTypedPatternElementType {
  val DebugName = "typed pattern"
}

class ScTypedPatternStubFactory(elementType: IElementType)
  extends ScBindingPatternStubFactory[ScTypedPattern](elementType) {
  override def createPsi(stub: ScBindingPatternStub[ScTypedPattern]): ScTypedPattern =
    new ScTypedPatternImpl(stub)

  override def getExternalId: String = s"scala.${ScTypedPatternElementType.DebugName}"
}

final class ScNamingPatternElementType extends ScBindingPatternElementType[ScNamingPattern](ScNamingPatternElementType.DebugName) {
  override def createElement(node: ASTNode): ScNamingPattern =
    new ScNamingPatternImpl(node)
}

object ScNamingPatternElementType {
  val DebugName = "naming pattern"
}

class ScNamingPatternStubFactory(elementType: IElementType)
  extends ScBindingPatternStubFactory[ScNamingPattern](elementType) {
  override def createPsi(stub: ScBindingPatternStub[ScNamingPattern]): ScNamingPattern =
    new ScNamingPatternImpl(stub)

  override def getExternalId: String = s"scala.${ScNamingPatternElementType.DebugName}"
}

final class ScSeqWildcardPatternElementType extends ScBindingPatternElementType[ScSeqWildcardPattern](ScSeqWildcardPatternElementType.DebugName) {
  override def createElement(node: ASTNode): ScSeqWildcardPattern =
    new ScSeqWildcardPatternImpl(node)
}

object ScSeqWildcardPatternElementType {
  val DebugName = "seq wildcard pattern"
}

class ScSeqWildcardPatternStubFactory(elementType: IElementType)
  extends ScBindingPatternStubFactory[ScSeqWildcardPattern](elementType) {
  override def createPsi(stub: ScBindingPatternStub[ScSeqWildcardPattern]): ScSeqWildcardPattern =
    new ScSeqWildcardPatternImpl(stub)

  override def getExternalId: String = s"scala.${ScSeqWildcardPatternElementType.DebugName}"
}
