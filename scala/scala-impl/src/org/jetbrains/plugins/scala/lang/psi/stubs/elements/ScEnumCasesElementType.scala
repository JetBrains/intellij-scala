package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCases
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScEnumCasesImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScEnumCasesStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScEnumCasesStubImpl

object ScEnumCasesElementType extends ScStubElementType.Impl[ScEnumCasesStub, ScEnumCases]("ScEnumCases") {
  override protected def createPsi(stub: ScEnumCasesStub,
                                   nodeType: ScEnumCasesElementType.this.type,
                                   node: ASTNode,
                                   debugName: String): ScEnumCases = new ScEnumCasesImpl(stub, nodeType, node)

  override protected def createStubImpl(psi: ScEnumCases, parentStub: StubElement[_ <: PsiElement]): ScEnumCasesStub =
    new ScEnumCasesStubImpl(parentStub, this)

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScEnumCasesStub =
    new ScEnumCasesStubImpl(parentStub, this)
}
