package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs._
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.parser.ScalaPsiCreator.SelfPsiCreator

/**
  * @author ilyas
  */
abstract class ScStubElementType[S <: StubElement[T], T <: PsiElement](val debugName: String)
  extends IStubElementType[S, T](debugName, ScalaLanguage.INSTANCE) with SelfPsiCreator with DefaultStubSerializer[S] {

  override def createElement(node: ASTNode): T

  def isCompiled(stub: S): Boolean = {
    var parent = stub
    while (!parent.isInstanceOf[PsiFileStub[_ <: PsiFile]]) {
      parent = parent.getParentStub.asInstanceOf[S]
    }
    parent.asInstanceOf[ScFileStub].isCompiled
  }

  override def isLeftBound = true

  protected def withStubAccessLock(fun: => S): S= {
    import ScStubElementType._isStubBuilding
    try {
      _isStubBuilding.set(true)
      fun
    } finally { _isStubBuilding.set(false) }
  }
}

object ScStubElementType {
  private val _isStubBuilding: ThreadLocal[Boolean] = new ThreadLocal[Boolean]
  def isStubBuilding: Boolean = _isStubBuilding.get()
}
