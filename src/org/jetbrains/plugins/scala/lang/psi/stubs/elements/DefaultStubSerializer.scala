package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.Language
import com.intellij.psi.stubs.{IndexSink, StubElement, StubOutputStream, StubSerializer}
import org.jetbrains.plugins.scala.lang.{ScalaTokenSets, TokenSets}

/**
  * @author adkozlov
  */
trait DefaultStubSerializer[S <: StubElement[_]] extends StubSerializer[S] {
  def getLanguage: Language

  def debugName: String

  def tokensSet: TokenSets = ScalaTokenSets

  override def getExternalId: String =
    s"${getLanguage.toString.toLowerCase}.$debugName"

  override def serialize(stub: S, dataStream: StubOutputStream): Unit = {}

  override def indexStub(stub: S, sink: IndexSink): Unit = {}
}
