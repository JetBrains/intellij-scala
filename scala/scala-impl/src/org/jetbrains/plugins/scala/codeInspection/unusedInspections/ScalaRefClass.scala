package org.jetbrains.plugins.scala.codeInspection.unusedInspections

import com.intellij.codeInspection.reference._
import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiElement, SmartPsiElementPointer}
import org.jetbrains.uast.UClass

import java.util
import javax.swing.Icon

class ScalaRefClass(uClass: UClass, psi: PsiElement, manager: RefManager)
  extends RefElementImpl(psi.getContainingFile, manager) with RefClass {
  override def getBaseClasses: util.Set[RefClass] = util.Set.of()

  override def getSubClasses: util.Set[RefClass] = util.Set.of()

  override def getConstructors: util.List[RefMethod] = util.List.of()

  override def getInTypeReferences: util.Set[RefElement] = util.Set.of()

  override def getDefaultConstructor: RefMethod = null

  override def getLibraryMethods: util.List[RefMethod] = util.List.of()

  override def isAnonymous: Boolean = false

  override def isInterface: Boolean = false

  override def isUtilityClass: Boolean = false

  override def isAbstract: Boolean = false

  override def isApplet: Boolean = false

  override def isServlet: Boolean = false

  override def isTestCase: Boolean = false

  override def isLocalClass: Boolean = false

  override def getOutTypeReferences: util.Collection[RefClass] = util.List.of()

  override def isFinal: Boolean = false

  override def isStatic: Boolean = false

  override def isSyntheticJSP: Boolean = false

  override def getAccessModifier: String = ""

  override def getModule: RefModule = null

  override def getPointer: SmartPsiElementPointer[_] = null

  override def isReachable: Boolean = true

  override def isReferenced: Boolean = false

  override def getOutReferences: util.Collection[RefElement] = util.List.of()

  override def getInReferences: util.Collection[RefElement] = util.List.of()

  override def isEntry: Boolean = false

  override def isPermanentEntry: Boolean = false

  override def getContainingEntry: RefElement = null

  override def getName: String = "Foobaruuuzzle"

  override def getQualifiedName: String = "com.google.Foobaruuuzzle"

  override def getChildren: util.List[RefEntity] = util.List.of()

  override def accept(refVisitor: RefVisitor): Unit = {}

  override def getExternalName: String = "external.com.googkelfgkldf"

  override def isValid: Boolean = true

  override def getUserData[T](key: Key[T]): T = ???

  override def putUserData[T](key: Key[T], value: T): Unit = {}

  override def initialize(): Unit = {}

  override def getPsiElement: PsiElement = psi

  override def getUastElement: UClass = uClass
}