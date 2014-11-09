package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements
import _root_.org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScExtendsBlockImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScExtendsBlockStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys

/**
 * @author ilyas
 */

class ScExtendsBlockElementType
extends ScStubElementType[ScExtendsBlockStub, ScExtendsBlock]("extends block") {

  def serialize(stub: ScExtendsBlockStub, dataStream: StubOutputStream) {
    dataStream.writeInt(stub.getBaseClasses.length)
    for (name <- stub.getBaseClasses) dataStream.writeName(name)
  }

  def indexStub(stub: ScExtendsBlockStub, sink: IndexSink) {
    for (name <- stub.getBaseClasses) {
      sink.occurrence(ScalaIndexKeys.SUPER_CLASS_NAME_KEY, name)
    }
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScExtendsBlockStub = {
    val n = dataStream.readInt
    val baseClasses = new Array[StringRef](n)
    for (i <- 0 until n) baseClasses(i) = dataStream.readName
    new ScExtendsBlockStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, baseClasses)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScExtendsBlock, parentStub: StubElement[ParentPsi]) = {
    val baseNames = psi.directSupersNames
    new ScExtendsBlockStubImpl(parentStub, this, baseNames.toArray)
  }

  def createPsi(stub: ScExtendsBlockStub): ScExtendsBlock = new ScExtendsBlockImpl(stub)

  override def isLeftBound = true
}


