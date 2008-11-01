package org.jetbrains.plugins.scala.lang.psi.stubs.elements
import _root_.org.jetbrains.plugins.scala.lang.psi.impl.compiled.ScClsExtendsBlockImpl
import _root_.org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScExtendsBlockImpl
import api.toplevel.templates.ScExtendsBlock
import api.toplevel.typedef.ScTypeDefinition
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}

import com.intellij.util.io.StringRef
import impl.{ScExtendsBlockStubImpl, ScTypeDefinitionStubImpl}
import index.ScalaIndexKeys

/**
 * @author ilyas
 */

class ScExtendsBlockElementType
extends ScStubElementType[ScExtendsBlockStub, ScExtendsBlock]("extends block") {

  def serialize(stub: ScExtendsBlockStub, dataStream: StubOutputStream) {
    dataStream.writeByte(stub.getBaseClasses.length)
    for (name <- stub.getBaseClasses) dataStream.writeName(name)
  }

  def indexStub(stub: ScExtendsBlockStub, sink: IndexSink) {
    for (name <- stub.getBaseClasses) {
      sink.occurrence(ScalaIndexKeys.SUPER_CLASS_NAME_KEY, name)
    }
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScExtendsBlockStub = {
    val n = dataStream.readByte
    val baseClasses = new Array[String](n)
    for (i <- 0 to n-1) baseClasses(i) = StringRef.toString(dataStream.readName)
    new ScExtendsBlockStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, baseClasses)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScExtendsBlock, parentStub: StubElement[ParentPsi]) = {
    val baseNames = psi.directSupersNames
    new ScExtendsBlockStubImpl(parentStub, this, baseNames.toArray)
  }

  def createPsi(stub: ScExtendsBlockStub): ScExtendsBlock = if (isCompiled(stub)) {
    new ScClsExtendsBlockImpl(stub)
  } else {
    new ScExtendsBlockImpl(stub)
  }
}


