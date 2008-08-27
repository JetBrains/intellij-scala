package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import wrappers.{StubElementWrapper, PsiStubElementWrapper}
import com.intellij.psi.stubs.{StubElement, IStubElementType}
import com.intellij.psi.PsiElement
import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode

/**
 * @author ilyas
 */

// Basic class for scala PSI elements
class ScalaBaseElementImpl[StubT <: StubElementWrapper[PsiT], PsiT <: PsiElement](node: ASTNode) extends PsiStubElementWrapper[StubT, PsiT](node) {

  override def getParent: PsiElement = getParentByStub

}
