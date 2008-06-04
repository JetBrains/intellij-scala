package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.scope._
import com.intellij.psi._
import com.intellij.lang.StdLanguages
import _root_.scala.collection.Set
import _root_.scala.collection.mutable.HashSet
import org.jetbrains.plugins.scala.lang.psi.api._
import toplevel.typedef. {ScObject, ScTypeDefinition}
import base.patterns.ScReferencePattern
import statements.ScVariable
import statements.params.{ScTypeParam, ScParameter}

abstract class BaseProcessor(val kinds: Set[ResolveTargets]) extends PsiScopeProcessor {

  val candidates: HashSet[ScalaResolveResult] = new HashSet[ScalaResolveResult]

  //java compatibility
  object MyElementClassHint extends ElementClassHint {
    def shouldProcess(c: Class[_]): Boolean = {
      if (kinds == null)  true
      else if (c.isAssignableFrom(classOf[PsiPackage])) kinds contains ResolveTargets.PACKAGE
      else if (c.isAssignableFrom(classOf[PsiClass])) (kinds contains ResolveTargets.CLASS) || (kinds contains ResolveTargets.OBJECT)
      else if (c.isAssignableFrom(classOf[PsiVariable])) (kinds contains ResolveTargets.VAR) || (kinds contains ResolveTargets.VAL)
      else if (c.isAssignableFrom(classOf[PsiMethod])) kinds contains (ResolveTargets.METHOD)
      else false
    }
  }

  def getHint[T](hintClass: Class[T]): T = {
    if (hintClass == classOf[ElementClassHint]) {
      return MyElementClassHint.asInstanceOf[T]
    }
    return null.asInstanceOf[T]
  }

  def handleEvent(event: PsiScopeProcessor.Event, associated: Object) = {
  }

  protected def kindMatches(element: PsiElement): Boolean = kinds == null ||
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
            case patt: ScReferencePattern => {
              if (patt.getParent/*list of ids*/.getParent.isInstanceOf[ScVariable])
                kinds contains ResolveTargets.VAR else kinds contains ResolveTargets.VAL
            }
            case _: ScParameter => kinds contains ResolveTargets.VAL
            case _: PsiMethod => kinds contains ResolveTargets.METHOD
            case _ => false
          })
}