package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScDerivesClause
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScDerivesClauseImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScDerivesClauseStubImpl

class ScDerivesClauseElementType extends ScStubElementType[ScDerivesClauseStub, ScDerivesClause]("template derives") {

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScDerivesClauseStub =
    new ScDerivesClauseStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this)

  override def createStubImpl(psi: ScDerivesClause, parentStub: StubElement[_ <: PsiElement]): ScDerivesClauseStub =
    new ScDerivesClauseStubImpl(parentStub, this)

  override def createElement(node: ASTNode): ScDerivesClause = new ScDerivesClauseImpl(node)

  override def createPsi(stub: ScDerivesClauseStub): ScDerivesClause = new ScDerivesClauseImpl(stub)
}
