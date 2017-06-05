package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.psi.stubs.{IndexSink, StubIndexKey, StubOutputStream}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScValueOrVariableStub

/**
  * @author adkozlov
  */
abstract class ScValueOrVariableElementType[S <: ScValueOrVariableStub[V], V <: ScValueOrVariable](debugName: String)
  extends ScStubElementType[S, V](debugName) {
  protected val key: StubIndexKey[String, V]

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

  protected def isDeclaration(valueOrVariable: ScValueOrVariable): Boolean =
    valueOrVariable.isInstanceOf[ScVariableDeclaration]

  protected def typeText(valueOrVariable: ScValueOrVariable): Option[StringRef] =
    valueOrVariable.typeElement.map(_.getText).asReference

  protected def names(valueOrVariable: ScValueOrVariable): Array[StringRef] =
    valueOrVariable.declaredElements.map(_.name).toArray.asReferences

  protected def bodyText(valueOrVariable: ScValueOrVariable): Option[StringRef] = {
    valueOrVariable match {
      case definition: ScVariableDefinition => definition.expr.map(_.getText).asReference
      case definition: ScPatternDefinition => definition.expr.map(_.getText).asReference
      case _ => None
    }
  }

  protected def containerText(valueOrVariable: ScValueOrVariable): Option[StringRef] = {
    Option(valueOrVariable).collect {
      case declaration: ScVariableDeclaration => declaration.getIdList
      case declaration: ScValueDeclaration => declaration.getIdList
      case definition: ScVariableDefinition => definition.pList
      case definition: ScPatternDefinition => definition.pList
    }.map {
      _.getText
    }.asReference
  }

  protected def isLocal(valueOrVariable: ScValueOrVariable): Boolean =
    valueOrVariable.containingClass == null
}
