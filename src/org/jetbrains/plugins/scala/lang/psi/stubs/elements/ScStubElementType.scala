package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.{PsiFileStub, StubElement}
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.parser.ScalaPsiCreator.SelfPsiCreator
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.wrappers.IStubElementTypeWrapper

/**
  * @author ilyas
  */

abstract class ScStubElementType[S <: StubElement[T], T <: PsiElement](debugName: String)
  extends IStubElementTypeWrapper[S, T](debugName) with SelfPsiCreator {

  override def createElement(node: ASTNode): T

  def getExternalId: String = "sc." + super.toString

  def isCompiled(stub: S): Boolean = {
    var parent = stub
    while (!parent.isInstanceOf[PsiFileStub[_ <: PsiFile]]) {
      parent = parent.getParentStub.asInstanceOf[S]
    }
    parent.asInstanceOf[ScFileStub].isCompiled
  }

  override def isLeftBound = true
}