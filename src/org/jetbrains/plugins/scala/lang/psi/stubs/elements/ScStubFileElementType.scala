package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.Language
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.psi.{PsiElement, StubBuilder}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.decompiler.DecompilerUtil
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.AbstractFileStub

/**
  * @author ilyas
  */

class ScStubFileElementType(val debugName: String = "file",
                            language: Language = ScalaLanguage.INSTANCE)
  extends IStubFileElementType[ScFileStub](debugName, language) with DefaultStubSerializer[ScFileStub] {
  override def getStubVersion: Int = StubVersion.STUB_VERSION

  override def getBuilder: StubBuilder = new ScalaFileStubBuilder

  override def serialize(stub: ScFileStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.isScript)
    dataStream.writeBoolean(stub.isCompiled)
    dataStream.writeName(stub.packageName)
    dataStream.writeName(stub.sourceName)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScFileStub =
    deserializedStub(dataStream.readBoolean, dataStream.readBoolean,
      dataStream.readName, dataStream.readName)

  protected def deserializedStub(isScript: Boolean,
                                 isCompiled: Boolean,
                                 packageNameRef: StringRef,
                                 sourceNameRef: StringRef): DeserializedStubImpl =
    new DeserializedStubImpl(isScript, isCompiled,
      packageNameRef, sourceNameRef)

  protected class DeserializedStubImpl(val isScript: Boolean,
                                       val isCompiled: Boolean,
                                       packageNameRef: StringRef,
                                       sourceNameRef: StringRef)
    extends AbstractFileStub(null) {
    override def packageName = StringRef.toString(packageNameRef)

    override def sourceName = StringRef.toString(sourceNameRef)
  }

}

object StubVersion {
  val STUB_VERSION: Int = DecompilerUtil.DECOMPILER_VERSION
}