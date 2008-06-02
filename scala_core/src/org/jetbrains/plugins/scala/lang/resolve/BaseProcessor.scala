package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.scope._
import com.intellij.psi._
import com.intellij.lang.StdLanguages

import _root_.scala.collection.Set
import _root_.scala.collection.mutable.HashSet

import org.jetbrains.plugins.scala.lang.psi.api._
import toplevel.typedef.{ScObject, ScTypeDefinition}
import statements.{ScValue, ScVariable}
import statements.params.ScTypeParam

abstract class BaseProcessor(val kinds: Set[ResolveTargets]) extends PsiScopeProcessor {

  val candidates: HashSet[ScalaResolveResult] = new HashSet[ScalaResolveResult]

  def getHint [T](hintClass: Class[T]): T = {
    return null.asInstanceOf[T]
  }

  def handleEvent(event: PsiScopeProcessor.Event, associated: Object) = {}

  protected def kindMatches(element: PsiElement) : Boolean = kinds == null ||
    (element match {
      case _: PsiPackage => kinds contains ResolveTargets.PACKAGE
      case _: ScObject => kinds contains ResolveTargets.OBJECT
      case _: ScTypeParam => kinds contains ResolveTargets.CLASS
      case _: ScTypeDefinition => kinds contains ResolveTargets.CLASS
      case c: PsiClass if c.getLanguage == StdLanguages.JAVA => {
        if (kinds contains ResolveTargets.CLASS) true
        else {
          def isStaticCorrect(clazz: PsiClass): Boolean = {
            val cclazz = clazz.getContainingClass
            cclazz == null || (clazz.hasModifierProperty(PsiModifier.STATIC) && isStaticCorrect(cclazz))
          }
          isStaticCorrect(c)
        }
      }
      case _: ScValue => kinds contains ResolveTargets.VAL
      case _: ScVariable => kinds contains ResolveTargets.VAR
      case _: PsiMethod => kinds contains ResolveTargets.METHOD
      case _ => false
    })
}