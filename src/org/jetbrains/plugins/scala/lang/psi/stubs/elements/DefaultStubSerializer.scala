package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.Language
import com.intellij.psi.stubs.{IndexSink, StubElement, StubOutputStream, StubSerializer}

/**
  * @author adkozlov
  */
trait DefaultStubSerializer[S <: StubElement[_]] extends StubSerializer[S] {
  def getLanguage: Language

  def debugName: String

  override def getExternalId: String =
    s"${getLanguage.toString.toLowerCase}.$debugName"

  override def serialize(stub: S, dataStream: StubOutputStream): Unit = {}

  override def indexStub(stub: S, sink: IndexSink): Unit = {}
}
