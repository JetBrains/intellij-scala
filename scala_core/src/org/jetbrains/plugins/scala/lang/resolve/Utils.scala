package org.jetbrains.plugins.scala.lang.resolve
import _root_.scala.collection.Set
import psi.api.statements.{ScTypeAlias, ScFun, ScVariable}
import psi.api.statements.params.{ScParameter, ScTypeParam}
import psi.api.base.ScFieldId
import com.intellij.psi._
import psi.api.base.patterns.ScBindingPattern
import psi.api.toplevel.typedef.{ScTypeDefinition, ScObject}
import psi.api.toplevel.packaging.ScPackaging

import ResolveTargets._

/**
 * @author ven
 */
object ResolveUtils {
  def kindMatches(element: PsiElement, kinds: Set[ResolveTargets.Value]): Boolean = kinds == null ||
          (element match {
            case _: PsiPackage => kinds contains PACKAGE
            case _: ScPackaging => kinds contains PACKAGE
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
                (kinds contains OBJECT) && isStaticCorrect(c)
              }
            }
            case patt: ScBindingPattern => {
              if (patt.getParent /*list of ids*/ .getParent.isInstanceOf[ScVariable])
                kinds contains VAR else kinds contains VAL
            }
            case patt: ScFieldId => {
              if (patt.getParent /*list of ids*/ .getParent.isInstanceOf[ScVariable])
                kinds contains VAR else kinds contains VAL
            }
            case _: ScParameter => kinds contains VAL
            case _: PsiMethod => kinds contains METHOD
            case _: ScFun => kinds contains METHOD
            case f: PsiField => (kinds contains VAR) || (f.hasModifierProperty(PsiModifier.FINAL) && kinds.contains(VAL))
            case _ => false
          })

}