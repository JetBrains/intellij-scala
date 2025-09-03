package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiNamedElement
import com.intellij.psi.stubs.NamedStub
import org.jetbrains.plugins.scala.lang.ir.typeTree.TypeTreeHolder

trait ScBoundsOwnerStub[E <: PsiNamedElement] extends NamedStub[E] {
  def lowerBoundTypeTree: Option[TypeTreeHolder]
  def upperBoundTypeTree: Option[TypeTreeHolder]

 def viewBoundsTypeTrees: Seq[TypeTreeHolder]
}
