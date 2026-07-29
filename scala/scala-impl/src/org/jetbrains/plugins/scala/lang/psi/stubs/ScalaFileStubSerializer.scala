package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializer}
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType

/**
 * The Scala file stub carries no serialized state of its own (classes are recomputed from children),
 * so `serialize`/`indexStub` are no-ops and `deserialize` produces an empty file stub.
 */
final class ScalaFileStubSerializer(fileType: ScStubFileElementType) extends StubSerializer[ScFileStub] {
  override def getExternalId: String = fileType.stubExternalId

  override def serialize(stub: ScFileStub, dataStream: StubOutputStream): Unit = {}

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScFileStub =
    fileType.createFileStub(null)

  override def indexStub(stub: ScFileStub, sink: IndexSink): Unit = {}
}
