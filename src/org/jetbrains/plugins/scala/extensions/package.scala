package org.jetbrains.plugins.scala

import com.intellij.openapi.util.Computable
import com.intellij.openapi.application.ApplicationManager
import extensions.implementation._
import com.intellij.psi.{PsiElement, PsiMethod}
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.openapi.project.Project

/**
  * Pavel Fatin
  */

package object extensions {
  implicit def toPsiMethodExt(method: PsiMethod) = new PsiMethodExt(method)

  implicit def toTraversableExt[CC[X] <: Traversable[X], A](t: CC[A]): TraversableExt[CC, A] =
    new TraversableExt[CC, A](t)

  implicit def toSeqExt[CC[X] <: Seq[X], A](t: CC[A]): SeqExt[CC, A] =
    new SeqExt[CC, A](t)

  implicit def toIterableExt[CC[X] <: Seq[X], A](t: CC[A]): IterableExt[CC, A] =
    new IterableExt[CC, A](t)

  implicit def toObjectExt[T](o: T) = new ObjectExt[T](o)

  implicit def toBooleanExt[T](b: Boolean) = new BooleanExt(b)

  implicit def toPsiElementExt(e: PsiElement) = new PsiElementExt {
    override def repr = e
  }

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

  def postponeFormattingWithin[T](project: Project)(body: => T): T = {
    PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(new Computable[T]{
      def compute(): T = body
    })
  }

  def invokeLater[T](body: => T) {
    ApplicationManager.getApplication.invokeLater(new Runnable {
      def run() {
        body
      }
    })
  }

  /** Create a PartialFunction from a sequence of cases. Workaround for pattern matcher bug */
  def pf[A, B](cases: PartialFunction[A, B]*) = new PartialFunction[A, B] {
    def isDefinedAt(x: A): Boolean = cases.exists(_.isDefinedAt(x))

    def apply(v1: A): B = {
      val it = cases.iterator
      while (it.hasNext) {
        val caze = it.next()
        if (caze.isDefinedAt(v1))
          return caze(v1)
      }
      throw new MatchError(v1.toString)
    }
  }
}