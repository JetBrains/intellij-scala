package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.Language
import com.intellij.openapi.vfs.{StandardFileSystems, VirtualFile}
import com.intellij.psi.stubs._
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.psi.{PsiElement, PsiFile}

/**
  * @author ilyas
  */
class ScStubFileElementType(language: Language = ScalaLanguage.INSTANCE)
  extends IStubFileElementType[ScFileStub](
    "file",
    language
  ) {

  import impl.ScFileStubImpl

  override final def getStubVersion: Int = super.getStubVersion + decompiler.DECOMPILER_VERSION

  override def shouldBuildStubFor(file: VirtualFile): Boolean =
    file.getFileSystem.getProtocol != StandardFileSystems.JAR_PROTOCOL

  override def getBuilder: DefaultStubBuilder = new ScFileStubBuilderImpl

  override def getExternalId = "scala.file"

  override final def serialize(stub: ScFileStub,
                               dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.isScript)
    dataStream.writeName(stub.sourceName)
  }

  override final def deserialize(dataStream: StubInputStream,
                                 parentStub: StubElement[_ <: PsiElement]): ScFileStub = {
    val isScriptImpl = dataStream.readBoolean
    val sourceNameImpl = dataStream.readNameString

    new ScFileStubImpl(null, this) {
      override val isScript: Boolean = isScriptImpl

      override val sourceName: String = sourceNameImpl
    }
  }

  override final def indexStub(stub: ScFileStub,
                               sink: IndexSink): Unit = {}

  private class ScFileStubBuilderImpl extends DefaultStubBuilder {

    protected override def createStubForFile(file: PsiFile) = new ScFileStubImpl(
      file.getViewProvider.getPsi(ScalaLanguage.INSTANCE).asInstanceOf[api.ScalaFile],
      ScStubFileElementType.this
    )
  }

}
