package org.jetbrains.plugins.scala.lang.psi.stubs.elements
import com.intellij.psi.stubs.{StubElement, IndexSink, IStubElementType, PsiFileStub}
import wrappers.IStubElementTypeWrapper
import com.intellij.psi.PsiElement
import eclipse.jdt.internal.compiler.ast.ASTNode
/**
 * @author ilyas
 */

abstract class ScStubElementType[S <: StubElement[T], T <: PsiElement](debugName: String)
extends IStubElementTypeWrapper[S, T](debugName) {

  def getExternalId = "sc." + super.toString()

  def isCompiled(stub: S) = {
    var parent = stub
    while (!(parent.isInstanceOf[PsiFileStub[_]])) {
      parent = parent.getParentStub.asInstanceOf[S]
    }
    parent.asInstanceOf[ScFileStub].isCompiled
  }

}