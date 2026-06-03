package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiNamedElement
import com.intellij.psi.stubs.NamedStub
import com.intellij.util.SofterReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScContextBound, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createContextBoundFromText, createTypeElementFromText}

trait ScBoundsOwnerStub[E <: PsiNamedElement] extends NamedStub[E] with PsiOwner[E] {

  private val lowerBoundStub = new ScTypeElementOwnerStubImpl[E](lowerBoundText, this)

  private val upperBoundStub = new ScTypeElementOwnerStubImpl[E](upperBoundText, this)

  def lowerBoundText:     Option[String]
  def upperBoundText:     Option[String]
  def contextBoundsTexts: Array[String]
  def viewBoundsTexts:    Array[String]

  def lowerBoundTypeElement: Option[ScTypeElement] = lowerBoundStub.typeElement
  def upperBoundTypeElement: Option[ScTypeElement] = upperBoundStub.typeElement

  private var viewElementsReferences: SofterReference[Seq[ScTypeElement]] = _
  private var contextElementsReferences: SofterReference[Seq[ScContextBound]] = _

 def viewBounds: Seq[ScTypeElement] = {
    getFromReference(viewElementsReferences) {
      case (context, child) =>
        viewBoundsTexts.map {
          createTypeElementFromText(_, context, child)
        }.toSeq
    } (viewElementsReferences = _)
  }

 def contextBounds: Seq[ScContextBound] = {
    getFromReference(contextElementsReferences) {
      case (context, child) =>
        contextBoundsTexts.map {
          createContextBoundFromText(_, context, child)
        }.toSeq
    } (contextElementsReferences = _)
  }
}
