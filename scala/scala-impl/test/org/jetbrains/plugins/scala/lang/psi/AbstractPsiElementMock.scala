package org.jetbrains.plugins.scala.lang.psi

import com.intellij.openapi.util.Key
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{PsiElement, PsiElementVisitor, ResolveState}

/**
 * Pavel.Fatin, 11.05.2010
 */

abstract class AbstractPsiElementMock extends PsiElement {
  override def getIcon(flags: Int) = null

  override def putUserData[T](key: Key[T], value: T) = {}

  override def getUserData[T](key: Key[T]) = null.asInstanceOf[T]

  override def isEquivalentTo(another: PsiElement) = false

  override def getNode = null

  override def getUseScope = null

  override def getResolveScope = null

  override def isPhysical = false

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement) = false

  override def putCopyableUserData[T](key: Key[T], value: T) = {}

  override def getCopyableUserData[T](key: Key[T]) = null.asInstanceOf[T]

  override def getReferences = null

  override def getReference = null

  override def isWritable = false

  override def isValid = false

  override def replace(newElement: PsiElement) = null

  override def deleteChildRange(first: PsiElement, last: PsiElement) = {}

  override def checkDelete() = {}

  override def delete() = {}

  override def addRangeAfter(first: PsiElement, last: PsiElement, anchor: PsiElement) = null

  override def addRangeBefore(first: PsiElement, last: PsiElement, anchor: PsiElement) = null

  override def addRange(first: PsiElement, last: PsiElement) = null

  override def checkAdd(element: PsiElement) = {}

  override def addAfter(element: PsiElement, anchor: PsiElement) = null

  override def addBefore(element: PsiElement, anchor: PsiElement) = null

  override def add(element: PsiElement) = null

  override def copy = null

  override def acceptChildren(visitor: PsiElementVisitor) = {}

  override def accept(visitor: PsiElementVisitor) = {}

  override def textContains(c: Char) = false

  override def textMatches(element: PsiElement) = false

  override def textMatches(text: CharSequence) = false

  override def getOriginalElement = null

  override def getNavigationElement = null

  override def textToCharArray = null

  override def getText = ""

  override def getTextOffset = 0

  override def findReferenceAt(offset: Int) = null

  override def findElementAt(offset: Int) = null

  override def getTextLength = 0

  override def getStartOffsetInParent = 0

  override def getTextRange = null

  override def getContainingFile = null

  override def getManager = null

  override def getLanguage = null

  override def getProject = null
}