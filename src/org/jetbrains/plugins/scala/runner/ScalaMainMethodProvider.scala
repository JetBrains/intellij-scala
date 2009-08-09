package org.jetbrains.plugins.scala
package runner

import com.intellij.codeInsight.runner.JavaMainMethodProvider
import com.intellij.psi._
import lang.psi.api.ScalaFile
import lang.psi.api.toplevel.typedef.ScObject

/**
 * @author ilyas
 */

class ScalaMainMethodProvider extends JavaMainMethodProvider {
  def hasMainMethod(clazz: PsiClass) = findMainInClass(clazz) != null

  def findMainInClass(clazz: PsiClass): PsiMethod = clazz match {
    case o: ScObject if clazz.getContainingFile.asInstanceOf[ScalaFile].isScriptFile == false => {
      val mainMethods = o.findMethodsByName("main", true)
      for (m <- mainMethods) {
        if (isMainMethod(m)) return m
      }
      null
    }
    case _ => null
  }

  private def isMainMethod(m: PsiMethod) =
    m != null &&
    m.getReturnType == PsiType.VOID &&
    m.hasModifierProperty(PsiModifier.PUBLIC) &&
    {
      val params = m.getParameterList.getParameters
      params.length == 1 && {
        val tpe = params(0).getType
        tpe match {
          case pat: PsiArrayType => pat.getComponentType.equalsToText("java.lang.String")
          case _ => false
        }
      }
    }

  def isApplicable(clazz: PsiClass) = clazz.getContainingFile.isInstanceOf[ScalaFile]
}