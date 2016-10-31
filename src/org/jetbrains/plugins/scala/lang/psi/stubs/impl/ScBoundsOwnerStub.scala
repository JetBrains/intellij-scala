package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiNamedElement
import com.intellij.psi.stubs.NamedStub
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

/**
  * @author adkozlov
  */
trait ScBoundsOwnerStub[E <: PsiNamedElement] extends NamedStub[E] with PsiOwner[E] {
  protected[impl] val lowerBoundTextRef: Option[StringRef]
  private val lowerBoundStub = new ScTypeElementOwnerStubImpl[E](lowerBoundTextRef, this)

  protected[impl] val upperBoundTextRef: Option[StringRef]
  private val upperBoundStub = new ScTypeElementOwnerStubImpl[E](upperBoundTextRef, this)

  def lowerBoundText: Option[String] = lowerBoundStub.typeText

  def lowerBoundTypeElement: Option[ScTypeElement] = lowerBoundStub.typeElement

  def upperBoundText: Option[String] = upperBoundStub.typeText

  def upperBoundTypeElement: Option[ScTypeElement] = upperBoundStub.typeElement
}
