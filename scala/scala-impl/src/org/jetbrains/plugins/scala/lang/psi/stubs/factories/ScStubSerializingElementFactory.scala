package org.jetbrains.plugins.scala.lang.psi.stubs.factories

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubOutputStream, StubSerializingElementFactory}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubElementType
import org.jetbrains.plugins.scala.lang.psi.tree.IScalaElementType

abstract class ScStubSerializingElementFactory[Stub <: StubElement[_], Psi <: PsiElement](
  elementType: IScalaElementType,
) extends StubSerializingElementFactory[Stub, Psi] {

  protected def getExternalIdPrefix: String = ScalaLanguage.INSTANCE.getDisplayName.toLowerCase

  override final def getExternalId: String = s"$getExternalIdPrefix.$elementType"

  override def serialize(stub: Stub, dataStream: StubOutputStream): Unit = {}

  override def indexStub(stub: Stub, sink: IndexSink): Unit = {}

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)

  override final def createStub(psi: Psi, parentStub: StubElement[_ <: PsiElement]): Stub = ScStubElementType.Processing.run {
    createStubImpl(psi, parentStub)
  }

  protected def createStubImpl(psi: Psi, parentStub: StubElement[_ <: PsiElement]): Stub
}
