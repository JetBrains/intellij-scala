package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import _root_.scala.collection.mutable.ArrayBuffer
import api.statements.{ScVariable, ScVariableDeclaration}
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.util.io.StringRef
import impl.ScVariableStubImpl
import index.ScalaIndexKeys
import java.io.IOException

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.10.2008
 */

abstract class ScVariableElementType[Variable <: ScVariable](debugName: String)
extends ScStubElementType[ScVariableStub, ScVariable](debugName) {
  def createStubImpl[ParentPsi <: PsiElement](psi: ScVariable, parentStub: StubElement[ParentPsi]): ScVariableStub = {
    new ScVariableStubImpl[ParentPsi](parentStub, this, (for (elem <- psi.declaredElements) yield elem.getName).toArray, psi.isInstanceOf[ScVariableDeclaration])
  }

  def serialize(stub: ScVariableStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.isDeclaration)
    val names = stub.getNames
    dataStream.writeInt(names.length)
    for (name <- names) dataStream.writeName(name)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScVariableStub = {
    val isDecl = dataStream.readBoolean
    val namesLength = dataStream.readInt
    val names = new Array[String](namesLength)
    for (i <- 0 to (namesLength - 1)) names(i) = StringRef.toString(dataStream.readName)
    val parent = parentStub.asInstanceOf[StubElement[PsiElement]]
    new ScVariableStubImpl(parent, this, names, isDecl)
  }

  def indexStub(stub: ScVariableStub, sink: IndexSink): Unit = {
    val names = stub.getNames
    for (name <- names if name != null) {
      sink.occurrence(ScalaIndexKeys.VARIABLE_NAME_KEY, name)
    }
  }
}