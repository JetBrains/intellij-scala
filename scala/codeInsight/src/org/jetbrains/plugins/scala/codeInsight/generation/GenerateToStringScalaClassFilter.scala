package org.jetbrains.plugins.scala
package codeInsight
package generation

import com.intellij.psi.PsiClass
import org.jetbrains.generate.tostring.GenerateToStringClassFilter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper

final class GenerateToStringScalaClassFilter extends GenerateToStringClassFilter {

  override def canGenerateToString(clazz: PsiClass): Boolean = clazz match {
    case _: ScTemplateDefinition |
         _: PsiClassWrapper => false
    case _ => true
  }
}
