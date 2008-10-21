package org.jetbrains.plugins.scala.lang.psi.stubs.elements
import api.ScalaFile
import com.intellij.lang.Language
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.util.io.StringRef
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
    val compiled = dataStream.readBoolean
    val packName = dataStream.readName
    return new ScFileStubImpl(null, StringRef.fromString(""), packName, compiled)
  }

  override def serialize(stub: ScFileStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.isCompiled)
    dataStream.writeName(stub.packageName)
  }

  def indexStub(stub: ScFileStub, sink: IndexSink){
  }

}

private[elements] object StubVersion {
  val STUB_VERSION: Int = 4
}