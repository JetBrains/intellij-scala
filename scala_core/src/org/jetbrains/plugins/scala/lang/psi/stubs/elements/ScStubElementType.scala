package org.jetbrains.plugins.scala.lang.psi.stubs.elements
import wrappers.{StubElementWrapper, IStubElementTypeWrapper}
import com.intellij.psi.stubs.{IndexSink, IStubElementType}
import com.intellij.psi.PsiElement
import eclipse.jdt.internal.compiler.ast.ASTNode
/**
 * @author ilyas
 */

abstract class ScStubElementType[S <: StubElementWrapper[T], T <: PsiElement](debugName: String)
extends IStubElementTypeWrapper[S, T](debugName) {

  def getExternalId = "sc." + super.toString()
}