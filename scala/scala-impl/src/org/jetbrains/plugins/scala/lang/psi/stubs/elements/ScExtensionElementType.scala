package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtension
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScExtensionImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScExtensionStubImpl


class ScExtensionElementType extends ScStubElementType[ScExtensionStub, ScExtension]("extension") {

  override def serialize(stub: ScExtensionStub, dataStream: StubOutputStream): Unit = ()

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScExtensionStub =
    new ScExtensionStubImpl(parentStub, this)

  override def createStubImpl(extension: ScExtension, parentStub: StubElement[_ <: PsiElement]) =
    new ScExtensionStubImpl(parentStub, this)

  override def createElement(node: ASTNode): ScExtension = new ScExtensionImpl(null, node)

  override def createPsi(stub: ScExtensionStub): ScExtension = new ScExtensionImpl(stub, null)
}