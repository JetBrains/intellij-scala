package org.jetbrains.plugins.scala.lang.psi

import com.intellij.lang.{ASTNode, Language}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.{GlobalSearchScope, SearchScope}
import com.intellij.psi._

import javax.swing.Icon

abstract class AbstractPsiElementMock extends PsiElement {
  override def getIcon(flags: Int): Icon = null

  override def putUserData[T](key: Key[T], value: T): Unit = {}

  override def getUserData[T](key: Key[T]): T = null.asInstanceOf[T]

  override def isEquivalentTo(another: PsiElement) = false

  override def getNode: ASTNode = null

  override def getUseScope: SearchScope = null

  override def getResolveScope: GlobalSearchScope = null

  override def isPhysical = false

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement) = false

  override def putCopyableUserData[T](key: Key[T], value: T): Unit = {}

  override def getCopyableUserData[T](key: Key[T]): T = null.asInstanceOf[T]

  override def getReferences: Array[PsiReference] = null

  override def getReference: PsiReference = null

  override def isWritable = false

  override def isValid = false

  override def replace(newElement: PsiElement): PsiElement = null

  override def deleteChildRange(first: PsiElement, last: PsiElement): Unit = {}

  override def checkDelete(): Unit = {}

  override def delete(): Unit = {}

  override def addRangeAfter(first: PsiElement, last: PsiElement, anchor: PsiElement): PsiElement = null

  override def addRangeBefore(first: PsiElement, last: PsiElement, anchor: PsiElement): PsiElement = null

  override def addRange(first: PsiElement, last: PsiElement): PsiElement = null

  override def checkAdd(element: PsiElement): Unit = {}

  override def addAfter(element: PsiElement, anchor: PsiElement): PsiElement = null

  override def addBefore(element: PsiElement, anchor: PsiElement): PsiElement = null

  override def add(element: PsiElement): PsiElement = null

  override def copy: PsiElement = null

  override def acceptChildren(visitor: PsiElementVisitor): Unit = {}

  override def accept(visitor: PsiElementVisitor): Unit = {}

  override def textContains(c: Char) = false

  override def textMatches(element: PsiElement) = false

  override def textMatches(text: CharSequence) = false

  override def getOriginalElement: PsiElement = null

  override def getNavigationElement: PsiElement = null

  override def textToCharArray: Array[Char] = null

  override def getText = ""

  override def getTextOffset = 0

  override def findReferenceAt(offset: Int): PsiReference = null

  override def findElementAt(offset: Int): PsiElement = null

  override def getTextLength = 0

  override def getStartOffsetInParent = 0

  override def getTextRange: TextRange = null

  override def getContainingFile: PsiFile = null

  override def getManager: PsiManager = null

  override def getLanguage: Language = null

  override def getProject: Project = null
}