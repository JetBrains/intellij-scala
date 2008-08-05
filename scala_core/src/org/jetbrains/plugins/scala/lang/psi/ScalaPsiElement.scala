package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.PsiElement

trait ScalaPsiElement extends PsiElement {
  protected def findChildByClass[T >: Null <: ScalaPsiElement](clazz : Class[T]) : T

  protected def findChildrenByClass[T >: Null <: ScalaPsiElement](clazz : Class[T]) : Array[T]

  protected def findChild[T >: Null <: ScalaPsiElement](clazz : Class[T]) : Option[T] = findChildByClass(clazz) match {
    case null => None
    case e => Some(e)
  }
}