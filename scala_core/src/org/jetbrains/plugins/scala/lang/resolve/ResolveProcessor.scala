package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.scope._
import com.intellij.psi._
import com.intellij.lang.StdLanguages
import com.intellij.openapi.util.Key

import org.jetbrains.plugins.scala.lang.psi.api._
import toplevel.typedef.{ScObject, ScTypeDefinition}
import statements.{ScValue, ScVariable}

import _root_.scala.collection.Set
import _root_.scala.collection.immutable.HashSet

class ResolveProcessor(override val kinds: Set[ResolveTargets], val name: String) extends BaseProcessor(kinds) {

  def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    val nameSet = state.get(ResolverEnv.nameKey)
    val elName = if (nameSet == null) named.getName else nameSet
    if (named != null && elName == name) {
      if (kinds != null && !(element match {
        case _ : PsiPackage => kinds contains ResolveTargets.PACKAGE
        case _ : ScObject => kinds contains ResolveTargets.OBJECT
        case _ : ScTypeDefinition => kinds contains ResolveTargets.CLASS
        case c : PsiClass if c.getLanguage == StdLanguages.JAVA => {
          if (kinds contains ResolveTargets.CLASS) true
          else {
            def isStaticCorrect(clazz : PsiClass) : Boolean = {
              val cclazz = clazz.getContainingClass
              cclazz == null || (clazz.hasModifierProperty(PsiModifier.STATIC) && isStaticCorrect(cclazz))
            }
            isStaticCorrect(c)
          }
        }
        case _ : ScValue => kinds contains ResolveTargets.VAL
        case _ : ScVariable => kinds contains ResolveTargets.VAR
        case _ : PsiMethod => kinds contains ResolveTargets.METHOD
        case _ => true
      })) return true

      candidates += new ScalaResolveResult(named)
      return false //todo: for locals it is ok to terminate the walkup, later need more elaborate check
    }
    return true
  }
}

import ResolveTargets._
object StdKinds {
  val stableLastRef = HashSet.empty[ResolveTargets] + PACKAGE + OBJECT + VAL
  val stableNotLastRef = stableLastRef + CLASS
}

/*
class StableMemberProcessor(override val name: String) extends ResolveProcessor (null, name) {
  override def execute(element: PsiElement, state: ResolveState): Boolean =
    element match {
      case clazz : PsiClass =>
        if ((clazz.getLanguage eq StdLanguages.JAVA) && clazz.getContainingClass != null &&
         !clazz.hasModifierProperty(PsiModifier.STATIC)) true else super.execute(element, state)
      case _ => super.execute(element, state)
    }
}
*/

object ResolverEnv {
  val nameKey : Key[String] = Key.create("ResolverEnv.nameKey")
}