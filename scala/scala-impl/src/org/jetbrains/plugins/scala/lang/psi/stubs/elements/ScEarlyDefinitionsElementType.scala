package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.ScEarlyDefinitionsImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScEarlyDefinitionsStubImpl

class ScEarlyDefinitionsElementType[Func <: ScEarlyDefinitions]
  extends ScStubElementType[ScEarlyDefinitionsStub, ScEarlyDefinitions]("early definitions") {
  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScEarlyDefinitionsStub =
    new ScEarlyDefinitionsStubImpl(parentStub, this)

  override def createStubImpl(psi: ScEarlyDefinitions, parentStub: StubElement[_ <: PsiElement]): ScEarlyDefinitionsStub =
    new ScEarlyDefinitionsStubImpl(parentStub, this)

  override def createElement(node: ASTNode): ScEarlyDefinitions = new ScEarlyDefinitionsImpl(node)

  override def createPsi(stub: ScEarlyDefinitionsStub): ScEarlyDefinitions = new ScEarlyDefinitionsImpl(stub)
}