package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements
import api.ScalaFile
import com.intellij.lang.Language
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.util.io.StringRef
import decompiler.DecompilerUtil
import impl.ScFileStubImpl
import wrappers.IStubFileElementWrapper

/**
 * @author ilyas
 */

class ScStubFileElementType(lang: Language) extends IStubFileElementWrapper[ScalaFile, ScFileStub]("scala.FILE", lang) {

  override def getStubVersion: Int = StubVersion.STUB_VERSION

  override def getBuilder = new ScalaFileStubBuilder()

  override def getExternalId = "scala.FILE"

  override def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScFileStub = {
    val script = dataStream.readBoolean
    val compiled = dataStream.readBoolean
    val packName = dataStream.readName
    val fileName = dataStream.readName
    return new ScFileStubImpl(null, packName, fileName, compiled, script)
  }

  override def serialize(stub: ScFileStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.isScript)
    dataStream.writeBoolean(stub.isCompiled)
    dataStream.writeName(stub.packageName)
    dataStream.writeName(stub.getFileName)
  }

  def indexStub(stub: ScFileStub, sink: IndexSink){
  }

}

private[elements] object StubVersion {
  val STUB_VERSION: Int = DecompilerUtil.DECOMPILER_VERSION
}