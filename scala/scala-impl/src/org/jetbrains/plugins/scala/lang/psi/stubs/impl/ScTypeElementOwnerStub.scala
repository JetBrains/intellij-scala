package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.ir.typeTree.TypeTreeHolder

trait ScTypeElementOwnerStub[E <: PsiElement] extends PsiOwner[E] {
  def typeTreeHolder: Option[TypeTreeHolder]
}