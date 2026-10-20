package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.util.SofterReference

trait PsiOwner[T <: PsiElement] {
  def getPsi: T

  def getFromOptionalReference[E <: PsiElement](
    reference:          SofterReference[Option[E]]
  )(elementConstructor: (PsiElement, PsiElement) => Option[E]
  )(refUpdate:          SofterReference[Option[E]] => Unit
  ): Option[E] =
    getFromReferenceWithFilter[E, Option[E]](reference, elementConstructor, refUpdate)

  def getFromReference[E <: PsiElement](
    reference:          SofterReference[Seq[E]]
  )(elementConstructor: (PsiElement, PsiElement) => Seq[E]
  )(refUpdate:          SofterReference[Seq[E]] => Unit
  ): Seq[E] =
    getFromReferenceWithFilter[E, Seq[E]](reference, elementConstructor, refUpdate)

  private def getFromReferenceWithFilter[E <: PsiElement, C <: IterableOnce[E]](
    reference:          SofterReference[C],
    elementConstructor: (PsiElement, PsiElement) => C,
    refUpdate:          SofterReference[C] => Unit
  ): C = {
    def updateAndGetNewValue(): C = {
      val result = elementConstructor(getPsi, null)

      assert(result.iterator.forall(_.getContext == getPsi))

      refUpdate(new SofterReference[C](result))
      result
    }

    if (reference != null) {
      reference.get() match {
        case null => updateAndGetNewValue()
        case result =>
          if (result.iterator.forall(_.getContext == getPsi)) result
          else updateAndGetNewValue()
      }
    } else updateAndGetNewValue()
  }
}
