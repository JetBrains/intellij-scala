package org.jetbrains.plugins.scala.testingSupport.test

import com.intellij.psi.{PsiClass, PsiMethod, PsiModifier, PsiModifierList}
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.extensions.{ObjectExt, inReadAction}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass

import scala.collection.immutable

trait SuiteValidityChecker {
  def isValidSuite(clazz: PsiClass, suiteClass: PsiClass): Boolean
}

class SuiteValidityCheckerBase extends SuiteValidityChecker {

  override def isValidSuite(clazz: PsiClass, suiteClass: PsiClass): Boolean = inReadAction {
    isValidClass(clazz) &&
      !isAbstract(clazz) &&
      hasSuitableConstructor(clazz) &&
      ScalaPsiUtil.isInheritorDeep(clazz, suiteClass)
  }

  protected def isValidClass(clazz: PsiClass): Boolean =
    clazz.is[ScClass]

  private def isAbstract(clazz: PsiClass): Boolean = {
    val list: PsiModifierList = clazz.getModifierList
    list != null && list.hasModifierProperty(PsiModifier.ABSTRACT)
  }

  protected def hasSuitableConstructor(clazz: PsiClass): Boolean =
    hasPublicConstructor(clazz, maxParameters = 0)

  protected def hasPublicConstructor(clazz: PsiClass, maxParameters: Int): Boolean = {
    val constructors = allConstructors(clazz)
    constructors.exists(isPublicConstructor(_, maxParameters))
  }

  private def isPublicConstructor(con: PsiMethod, maxParameters: Int): Boolean =
    if (con.isConstructor && con.getParameterList.getParametersCount <= maxParameters)
      con match {
        case owner: ScModifierListOwner => owner.hasModifierProperty(PsiModifier.PUBLIC)
        case _                          => false
      }
    else false

  private def allConstructors(clazz: PsiClass): immutable.Seq[PsiMethod] =
    clazz match {
      case c: ScClass => c.secondaryConstructors.toList ::: c.constructor.toList
      case c          => c.getConstructors.toList
    }
}
