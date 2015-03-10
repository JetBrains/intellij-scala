package org.jetbrains.plugins.scala
package runner

import com.intellij.codeInsight.runner.JavaMainMethodProvider
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.{JavaArrayType, ScDesignatorType, ScType}

/**
 * @author ilyas
 */

class ScalaMainMethodProvider extends JavaMainMethodProvider {
  def hasMainMethod(clazz: PsiClass) = findMainInClass(clazz) != null


  def findMainInClass(clazz: PsiClass): PsiMethod = clazz match {
    case o: ScObject if !clazz.getContainingFile.asInstanceOf[ScalaFile].isScriptFile() =>
      for (method <- o.functionsByName("main")) {
        if (isMainMethod(method)) return method
      }
      null
    case _ => null
  }

  private def isMainMethod(method: PsiMethod): Boolean = {
    def checkTpe(tpe: ScType): Boolean = {
      val stringClass = ScalaPsiManager.instance(method.getProject).getCachedClass(method.getResolveScope, "java.lang.String")
      tpe.equiv(JavaArrayType(ScDesignatorType(stringClass)))
    }

    method.getReturnType == PsiType.VOID &&
      method.hasModifierProperty(PsiModifier.PUBLIC) &&
      (method match {
        case f: ScFunction =>
          val params = f.parameters
          params.length == 1 && checkTpe(params(0).getType(TypingContext.empty).getOrAny)
        case m: PsiMethod =>
          val params = m.getParameterList.getParameters
          params.length == 1 && checkTpe(ScType.create(params(0).getType, m.getProject, m.getResolveScope))
      })
  }


  def isApplicable(clazz: PsiClass) = false //clazz.getContainingFile.isInstanceOf[ScalaFile]
}