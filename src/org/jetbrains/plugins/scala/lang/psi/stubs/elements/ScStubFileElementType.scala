package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements
import com.intellij.lang.Language
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.psi.{PsiElement, StubBuilder}
import org.jetbrains.plugins.scala.decompiler.DecompilerUtil
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScFileStubImpl

/**
 * @author ilyas
 */

class ScStubFileElementType(debugName: String = "file",
                            language: Language = ScalaLanguage.Instance)
  extends IStubFileElementType[ScFileStub](debugName, language) with ExternalIdOwner {
  override def getStubVersion: Int = StubVersion.STUB_VERSION

  override def getBuilder: StubBuilder = new ScalaFileStubBuilder

  override def serialize(stub: ScFileStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.isScript)
    dataStream.writeBoolean(stub.isCompiled)
    dataStream.writeName(stub.packageName)
    dataStream.writeName(stub.sourceName)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScFileStub =
    new ScFileStubImpl(null,
      dataStream.readBoolean,
      dataStream.readBoolean,
      dataStream.readName,
      dataStream.readName)

  def indexStub(stub: ScFileStub, sink: IndexSink): Unit = {}

  override def getLanguageName: String = getLanguage.toString.toLowerCase

  override def getExternalId: String = s"$getLanguageName.$debugName"
}

object StubVersion {
  val STUB_VERSION: Int = DecompilerUtil.DECOMPILER_VERSION
}