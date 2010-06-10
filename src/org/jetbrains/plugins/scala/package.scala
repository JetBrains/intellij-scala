package org.jetbrains.plugins

import scala.lang.psi.RichPsiElement
import com.intellij.psi.{PsiElement, PsiReference}

/**
 * Pavel.Fatin, 21.04.2010
 */

package object scala {
  def print(x: Any) = Console.print(x)
  def println() = Console.println()
  def println(x: Any) = Console.println(x)
  def printf(text: String, xs: Any*) = Console.printf(text, xs: _*)
    
  implicit def toRichObject[T](o: T) = new RichObject[T](o)

  implicit def toRichPsiElement(e: PsiElement) = new RichPsiElement {override def delegate = e}

  implicit def toRichIterator[A](it: Iterator[A]) = new RichIterator[A](it)

  class RichObject[T](v: T) {
    def toOption: Option[T] = if (v == null) None else Some(v)
  }

  class RichIterator[A](delegate: Iterator[A]) {
    def findByType[T <: A](aClass: Class[T]): Option[T] =
      delegate.find(aClass.isInstance(_)).map(_.asInstanceOf[T])

    def filterByType[T <: A](aClass: Class[T]): Iterator[T] =
      delegate.filter(aClass.isInstance(_)).map(_.asInstanceOf[T])
  }

  object Parent {
    def unapply(e: PsiElement): Option[PsiElement] = {
      if (e == null) {
        None
      } else {
        val parent = e.getParent
        if (parent == null) None else Some(parent)
      }
    }
  }

  object Resolved {
    def unapply(e: PsiReference): Option[PsiElement] = {
      if (e == null) {
        None
      } else {
        val target = e.resolve
        if (target == null) None else Some(target)
      }
    }
  }
}

