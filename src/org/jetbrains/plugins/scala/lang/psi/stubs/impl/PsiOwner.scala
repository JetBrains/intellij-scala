package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.util.SofterReference

/**
  * @author adkozlov
  */
trait PsiOwner[T <: PsiElement] {
  def getPsi: T

  def updateOptionalReference[E <: PsiElement](reference: SofterReference[Option[E]])
                                              (elementConstructor: (PsiElement, PsiElement) => Option[E]): SofterReference[Option[E]] =
    updateReferenceWithFilter(reference, elementConstructor)(_.toSeq)

  def updateReference[E <: PsiElement](reference: SofterReference[Seq[E]])
                                      (elementConstructor: (PsiElement, PsiElement) => Seq[E]): SofterReference[Seq[E]] =
    updateReferenceWithFilter(reference, elementConstructor)

  private def updateReferenceWithFilter[E <: PsiElement, C](reference: SofterReference[C],
                                                            elementConstructor: (PsiElement, PsiElement) => C)
                                                           (implicit evidence: C => Seq[E]): SofterReference[C] =
    Option(reference).filter {
      _.get match {
        case null => false
        case Seq() => true
        case seq => seq.forall {
          _.getContext eq getPsi
        }
      }
    }.getOrElse {
      new SofterReference(elementConstructor(getPsi, null))
    }
}
