package org.jetbrains.plugins.scala.util.monads

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}
import com.intellij.psi.{PsiElement}

/**
 * @author ilyas
 */

trait MonadTransformer {

  type MonadLike[+T <: PsiElement] = {def flatMap[U <: ScType](f: T => TypeResult[U]): TypeResult[U]}

  implicit def wrap[T <: PsiElement](opt: Option[T]): MonadLike[T] = new {
    def flatMap[U <: ScType](f: T => TypeResult[U]): TypeResult[U] = opt match {
      case Some(elem) => f(elem)
      case None =>  Failure("No element found", None)
    }
  }
}