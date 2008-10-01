package org.jetbrains.plugins.scala.lang.psi.stubs.elements
import _root_.org.jetbrains.plugins.scala.lang.psi.impl.compiled.ScClsExtendsBlockImpl
import _root_.org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScExtendsBlockImpl
import api.toplevel.templates.ScExtendsBlock
import api.toplevel.typedef.ScTypeDefinition
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import impl.{ScExtendsBlockStubImpl, ScTypeDefinitionStubImpl}
import index.ScalaIndexKeys

/**
 * @author ilyas
 */

class ScExtendsBlockElementType
extends ScStubElementType[ScExtendsBlockStub, ScExtendsBlock]("extends block") {

  //todo implement me!
  def serialize(stub: ScExtendsBlockStub, dataStream: StubOutputStream) {}
  def indexStub(stub: ScExtendsBlockStub, sink: IndexSink) {}

  protected def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScExtendsBlockStub = {
    new ScExtendsBlockStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this)
  }

  protected def createStubImpl[ParentPsi <: PsiElement](psi: ScExtendsBlock, parentStub: StubElement[ParentPsi]) = {
    new ScExtendsBlockStubImpl(parentStub, this)
  }

  def createPsi(stub: ScExtendsBlockStub): ScExtendsBlock = if (isCompiled(stub)) {
    new ScClsExtendsBlockImpl(stub)
  } else {
    new ScClsExtendsBlockImpl(stub)
  }
}


