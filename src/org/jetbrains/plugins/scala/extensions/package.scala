package org.jetbrains.plugins.scala

import com.intellij.openapi.util.Computable
import com.intellij.openapi.application.ApplicationManager
import extensions.implementation._
import com.intellij.psi.{PsiElement, PsiMethod}

/**
 * Pavel Fatin
 */

package object extensions {
  implicit def toPsiMethodExt(method: PsiMethod) = new PsiMethodExt(method)

  implicit def toTraversableExt[CC[X] <: Traversable[X], A](t: CC[A]): TraversableExt[CC, A] =
    new TraversableExt[CC, A](t)

  implicit def toObjectExt[T](o: T) = new ObjectExt[T](o)

  implicit def toBooleanExt[T](b: Boolean) = new BooleanExt(b)

  implicit def toPsiElementExt(e: PsiElement) = new PsiElementExt {override def repr = e}

  implicit def toRichIterator[A](it: Iterator[A]) = new IteratorExt[A](it)


  def inWriteAction[T](body: => T): T = {
    ApplicationManager.getApplication.runWriteAction(new Computable[T] {
      def compute: T = body
    })
  }

  def inReadAction[T](body: => T): T = {
    ApplicationManager.getApplication.runReadAction(new Computable[T] {
      def compute: T = body
    })
  }
}