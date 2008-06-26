package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.scope._
import com.intellij.psi._
import com.intellij.lang.StdLanguages
import _root_.scala.collection.Set
import _root_.scala.collection.mutable.HashSet
import org.jetbrains.plugins.scala.lang.psi.api._
import toplevel.typedef. {ScObject, ScTypeDefinition}
import statements.{ScVariable, ScTypeAlias}
import statements.params.{ScTypeParam, ScParameter}
import base.patterns.ScBindingPattern
import base.ScFieldId
import psi.types._
import psi.ScalaPsiElement

object BaseProcessor {
  def unapply(p : BaseProcessor) = Some(p.kinds)
}

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

  import ResolveTargets._
  protected def kindMatches(element: PsiElement): Boolean = kinds == null ||
          (element match {
            case _: PsiPackage => kinds contains PACKAGE
            case _: ScObject => kinds contains OBJECT
            case _: ScTypeParam => kinds contains CLASS
            case _: ScTypeAlias => kinds contains CLASS
            case _: ScTypeDefinition => kinds contains CLASS
            case c: PsiClass => {
              if (kinds contains CLASS) true
              else {
                def isStaticCorrect(clazz: PsiClass): Boolean = {
                  val cclazz = clazz.getContainingClass
                  cclazz == null || (clazz.hasModifierProperty(PsiModifier.STATIC) && isStaticCorrect(cclazz))
                }
                isStaticCorrect(c)
              }
            }
            case patt: ScBindingPattern => {
              if (patt.getParent/*list of ids*/.getParent.isInstanceOf[ScVariable])
                kinds contains VAR else kinds contains VAL
            }
            case patt: ScFieldId => {
              if (patt.getParent/*list of ids*/.getParent.isInstanceOf[ScVariable])
                kinds contains VAR else kinds contains VAL
            }
            case _: ScParameter => kinds contains VAL
            case _: PsiMethod => kinds contains METHOD
            case _ => false
          })
}