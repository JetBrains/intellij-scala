package org.jetbrains.plugins.scala.extensions.implementation

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
 * User: Alefas
 * Date: 03.02.12
 */
class PsiClassExt(clazz: PsiClass) {
  def qualifiedName: String = {
    clazz match {
      case t: ScTemplateDefinition => t.qualifiedName
      case _ => clazz.getQualifiedName
    }
  }
}
