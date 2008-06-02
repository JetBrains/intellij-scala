package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

import com.intellij.lang.ASTNode
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.Nullable

import java.util.List;

trait ScalaPsiElement extends PsiElement {

  def childSatisfyPredicateForPsiElement(predicate: PsiElement => Boolean): PsiElement = {
    childSatisfyPredicateForPsiElement(predicate, getFirstChild, (e: PsiElement) => e.getNextSibling)
  }

  def childSatisfyPredicateForPsiElement(predicate: PsiElement => Boolean, startsWith: PsiElement): PsiElement = {
    childSatisfyPredicateForPsiElement(predicate, startsWith, (e: PsiElement) => e.getNextSibling)
  }

  def childSatisfyPredicateForPsiElement(predicate: PsiElement => Boolean, startsWith: PsiElement, direction: PsiElement => PsiElement): PsiElement = {
    def inner(e: PsiElement): PsiElement = if (e == null || predicate(e)) e else inner(direction(e))

    if (startsWith != null) inner(startsWith) else inner(getFirstChild)
  }

  def childSatisfyPredicateForElementType(predicate: IElementType => Boolean, startsWith: PsiElement): PsiElement = {
    childSatisfyPredicateForElementType(predicate, startsWith, (e: PsiElement) => e.getNextSibling)
  }

  def childSatisfyPredicateForElementType(predicate: IElementType => Boolean, startsWith: PsiElement, direction: PsiElement => PsiElement): PsiElement = {
    def inner(e: PsiElement): PsiElement = if (e == null || predicate(e.getNode.getElementType)) e else inner(direction(e))

    if (startsWith != null) inner(startsWith) else inner(getFirstChild)
  }

  def childSatisfyPredicateForElementType(predicate: IElementType => Boolean): PsiElement = {
    childSatisfyPredicateForElementType(predicate, getFirstChild, (e: PsiElement) => e.getNextSibling)
  }

  protected def findChildByClass[T >: Null <: ScalaPsiElement](clazz : Class[T]) : T

  protected def findChildrenByClass[T >: Null <: ScalaPsiElement](clazz : Class[T]) : Array[T]

  protected def findChild[T >: Null <: ScalaPsiElement](clazz : Class[T]) : Option[T] = findChildByClass(clazz) match {
    case null => None
    case e => Some(e)
  }
}