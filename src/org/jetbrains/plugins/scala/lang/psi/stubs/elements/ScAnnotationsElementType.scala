package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotations
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScAnnotationsImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScAnnotationsStubImpl

/**
  * User: Alexander Podkhalyuzin
  * Date: 22.06.2009
  */

class ScAnnotationsElementType[Func <: ScAnnotations]
  extends ScStubElementType[ScAnnotationsStub, ScAnnotations]("annotations") {
  def serialize(stub: ScAnnotationsStub, dataStream: StubOutputStream): Unit = {
  }

  override def createElement(node: ASTNode): ScAnnotations = new ScAnnotationsImpl(node)

  override def createPsi(stub: ScAnnotationsStub): ScAnnotations = new ScAnnotationsImpl(stub)

  def createStubImpl[ParentPsi <: PsiElement](psi: ScAnnotations, parentStub: StubElement[ParentPsi]): ScAnnotationsStub = {
    new ScAnnotationsStubImpl(parentStub, this)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScAnnotationsStub =
    new ScAnnotationsStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this)
}