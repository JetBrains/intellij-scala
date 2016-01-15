package org.jetbrains.plugins.scala.lang.psi

import com.intellij.openapi.util.Key
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{PsiElement, PsiElementVisitor, ResolveState}

/**
 * Pavel.Fatin, 11.05.2010
 */

abstract class AbstractPsiElementMock extends PsiElement {
  def getIcon(flags: Int) = null

  def putUserData[T](key: Key[T], value: T) = {}

  def getUserData[T](key: Key[T]) = null.asInstanceOf[T]

  def isEquivalentTo(another: PsiElement) = false

  def getNode = null

  def getUseScope = null

  def getResolveScope = null

  def isPhysical = false

  def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement) = false

  def putCopyableUserData[T](key: Key[T], value: T) = {}

  def getCopyableUserData[T](key: Key[T]) = null.asInstanceOf[T]

  def getReferences = null

  def getReference = null

  def isWritable = false

  def isValid = false

  def replace(newElement: PsiElement) = null

  def deleteChildRange(first: PsiElement, last: PsiElement) = {}

  def checkDelete() = {}

  def delete() = {}

  def addRangeAfter(first: PsiElement, last: PsiElement, anchor: PsiElement) = null

  def addRangeBefore(first: PsiElement, last: PsiElement, anchor: PsiElement) = null

  def addRange(first: PsiElement, last: PsiElement) = null

  def checkAdd(element: PsiElement) = {}

  def addAfter(element: PsiElement, anchor: PsiElement) = null

  def addBefore(element: PsiElement, anchor: PsiElement) = null

  def add(element: PsiElement) = null

  def copy = null

  def acceptChildren(visitor: PsiElementVisitor) = {}

  def accept(visitor: PsiElementVisitor) = {}

  def textContains(c: Char) = false

  def textMatches(element: PsiElement) = false

  def textMatches(text: CharSequence) = false

  def getOriginalElement = null

  def getNavigationElement = null

  def textToCharArray = null

  def getText = ""

  def getTextOffset = 0

  def findReferenceAt(offset: Int) = null

  def findElementAt(offset: Int) = null

  def getTextLength = 0

  def getStartOffsetInParent = 0

  def getTextRange = null

  def getContainingFile = null

  def getManager = null

  def getLanguage = null

  def getProject = null
}