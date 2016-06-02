package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.psi.stubs.{IndexSink, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.decompiler.DecompilerUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.wrappers.IStubFileElementWrapper

/**
 * @author ilyas
 */

class ScStubFileElementType private(val debugName: String)
  extends IStubFileElementWrapper[ScalaFile, ScFileStub](debugName, ScalaFileType.SCALA_LANGUAGE) with ScStubSerializer[ScFileStub] {

  def this() = this("file")

  override def getStubVersion = StubVersion.STUB_VERSION

  override def getBuilder = new ScalaFileStubBuilder

  override def getExternalId = super.getExternalId

  override def deserializeImpl(dataStream: StubInputStream, parentStub: Object): ScFileStub = {
    val isScript = dataStream.readBoolean
    val isCompiled = dataStream.readBoolean
    getBuilder.fileStub(null, dataStream.readName, dataStream.readName, isCompiled, isScript)
  }

  override def serialize(stub: ScFileStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.isScript)
    dataStream.writeBoolean(stub.isCompiled)
    dataStream.writeName(stub.packageName)
    dataStream.writeName(stub.getFileName)
  }

  def indexStub(stub: ScFileStub, sink: IndexSink) {
  }
}

object StubVersion {
  val STUB_VERSION: Int = DecompilerUtil.DECOMPILER_VERSION
}