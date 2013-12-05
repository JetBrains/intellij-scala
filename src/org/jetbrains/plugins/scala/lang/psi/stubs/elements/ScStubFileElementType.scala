package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements
import api.ScalaFile
import com.intellij.lang.Language
import com.intellij.psi.stubs.{IndexSink, StubOutputStream, StubInputStream}
import decompiler.DecompilerUtil
import wrappers.IStubFileElementWrapper
import com.intellij.psi.StubBuilder
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaStubsUtil

/**
 * @author ilyas
 */

class ScStubFileElementType(lang: Language) extends IStubFileElementWrapper[ScalaFile, ScFileStub]("scala.FILE", lang) {

  override def getStubVersion: Int = StubVersion.STUB_VERSION

  override def getBuilder: StubBuilder = new ScalaFileStubBuilder()

  override def getExternalId = "scala.FILE"

  override def deserializeImpl(dataStream: StubInputStream, parentStub: Object): ScFileStub = {
    ScalaStubsUtil.deserializeFileStubElement(dataStream, parentStub)
  }

  override def serialize(stub: ScFileStub, dataStream: StubOutputStream): Unit = {
    ScalaStubsUtil.serializeFileStubElement(stub, dataStream)
  }

  def indexStub(stub: ScFileStub, sink: IndexSink){
  }

}

object StubVersion {
  val STUB_VERSION: Int = DecompilerUtil.DECOMPILER_VERSION
}