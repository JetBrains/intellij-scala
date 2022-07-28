package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiNamedElement
import com.intellij.psi.stubs.NamedStub
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

trait ScBoundsOwnerStub[E <: PsiNamedElement] extends NamedStub[E] with PsiOwner[E] {

  private val lowerBoundStub = new ScTypeElementOwnerStubImpl[E](lowerBoundText, this)

  private val upperBoundStub = new ScTypeElementOwnerStubImpl[E](upperBoundText, this)

  def lowerBoundText: Option[String]

  def upperBoundText: Option[String]

  def lowerBoundTypeElement: Option[ScTypeElement] = lowerBoundStub.typeElement

  def upperBoundTypeElement: Option[ScTypeElement] = upperBoundStub.typeElement
}
