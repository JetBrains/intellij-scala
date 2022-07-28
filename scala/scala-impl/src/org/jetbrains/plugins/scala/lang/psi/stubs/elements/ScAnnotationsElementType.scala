package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotations
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScAnnotationsImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScAnnotationsStubImpl

class ScAnnotationsElementType extends ScStubElementType[ScAnnotationsStub, ScAnnotations]("annotations") {
  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScAnnotationsStub =
    new ScAnnotationsStubImpl(parentStub, this)

  override def createStubImpl(psi: ScAnnotations, parentStub: StubElement[_ <: PsiElement]): ScAnnotationsStub =
    new ScAnnotationsStubImpl(parentStub, this)

  override def createElement(node: ASTNode): ScAnnotations = new ScAnnotationsImpl(node)

  override def createPsi(stub: ScAnnotationsStub): ScAnnotations = new ScAnnotationsImpl(stub)
}