package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.util.SofterReference

/**
  * @author adkozlov
  */
trait PsiOwner[T <: PsiElement] {
  def getPsi: T

  def getFromOptionalReference[E <: PsiElement](reference: SofterReference[Option[E]])
                                               (elementConstructor: (PsiElement, PsiElement) => Option[E])
                                               (refUpdate: SofterReference[Option[E]] => Unit): Option[E] =
    getFromReferenceWithFilter(reference, elementConstructor, refUpdate)(_.toSeq)

  def getFromReference[E <: PsiElement](reference: SofterReference[Seq[E]])
                                       (elementConstructor: (PsiElement, PsiElement) => Seq[E])
                                       (refUpdate: SofterReference[Seq[E]] => Unit): Seq[E] =
    getFromReferenceWithFilter(reference, elementConstructor, refUpdate)

  protected def getFromReferenceWithFilter[E <: PsiElement, C](reference: SofterReference[C],
                                                               elementConstructor: (PsiElement, PsiElement) => C,
                                                               refUpdate: SofterReference[C] => Unit)
                                                              (implicit evidence: C => collection.Seq[E]): C = {
    def updateAndGetNewValue(): C = {
      val result = elementConstructor(getPsi, null)

      assert(result.forall(_.getContext == getPsi))

      refUpdate(new SofterReference[C](result))
      result
    }

    if (reference != null) {
      reference.get() match {
        case null => updateAndGetNewValue()
        case result =>
          if (result.forall(_.getContext == getPsi)) result
          else updateAndGetNewValue()
      }
    }
    else updateAndGetNewValue()
  }

}