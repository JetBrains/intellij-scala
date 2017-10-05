package org.jetbrains.plugins.scala.codeInsight.generation

import com.intellij.psi.PsiClass
import org.jetbrains.generate.tostring.GenerateToStringClassFilter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper

/**
 * @author Nikolay.Tropin
 */
class GenerateToStringScalaClassFilter extends GenerateToStringClassFilter{
  override def canGenerateToString(psiClass: PsiClass): Boolean = psiClass match {
    case _: ScTemplateDefinition | _: PsiClassWrapper => false
    case _ => true
  }
}
