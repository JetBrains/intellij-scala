package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.ir.{StubInputStreamForIRExt, StubOutputStreamForIRExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScContextBound
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScContextBoundImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScContextBoundStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScContextBoundStubImpl

class ScContextBoundElementType extends ScStubElementType[ScContextBoundStub, ScContextBound]("context bound"){
  override def createElement(node: ASTNode): ScContextBound =
    new ScContextBoundImpl(null, this, node)

  override protected def createStubImpl(psi: ScContextBound, parentStub: StubElement[_ <: PsiElement]): ScContextBoundStub =
    new ScContextBoundStubImpl(parentStub, this, psi.nameOpt.orNull, Some(psi.typeTreeHolder))

  override def createPsi(stub: ScContextBoundStub): ScContextBound =
    new ScContextBoundImpl(stub, this, null)

  override def serialize(stub: ScContextBoundStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeOptionName(Option(stub.getName))
    dataStream.writeTypeTreeHolder(stub.typeTreeHolder.value)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScContextBoundStub =
    new ScContextBoundStubImpl(
      parentStub.asInstanceOf[StubElement[PsiElement]],
      this,
      name = dataStream.readOptionName.orNull,
      typeTreeHolder = Some(dataStream.readTypeTreeHolder())
    )
}
