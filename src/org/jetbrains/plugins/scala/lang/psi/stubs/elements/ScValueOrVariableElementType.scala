package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.psi.stubs.{IndexSink, StubIndexKey, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScVariableDeclaration, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.stubs.ScValueOrVariableStub

/**
  * @author adkozlov
  */
abstract class ScValueOrVariableElementType[S <: ScValueOrVariableStub[T], T <: ScMember](debugName: String)
  extends ScStubElementType[S, T](debugName) {
  protected val key: StubIndexKey[String, T]

  override def serialize(stub: S, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.isDeclaration)
    dataStream.writeNames(stub.names)
    dataStream.writeOptionName(stub.typeText)
    dataStream.writeOptionName(stub.bodyText)
    dataStream.writeOptionName(stub.bindingsContainerText)
    dataStream.writeBoolean(stub.isLocal)
  }

  override def indexStub(stub: S, sink: IndexSink): Unit =
    this.indexStub(stub.names, sink, key)


  protected def isDeclaration(member: ScMember) =
    member.isInstanceOf[ScVariableDeclaration]

  protected def bodyText(member: ScMember) =
    Option(member).collect {
      case definition: ScVariableDefinition => definition
    }.flatMap {
      _.expr
    }.map {
      _.getText
    }.asReference

  protected def containerText(member: ScMember) =
    Option(member).collect {
      case declaration: ScVariableDeclaration => declaration.getIdList
      case definition: ScVariableDefinition => definition.pList
    }.map {
      _.getText
    }.asReference

  protected def isLocal(member: ScMember) =
    member.containingClass == null
}
