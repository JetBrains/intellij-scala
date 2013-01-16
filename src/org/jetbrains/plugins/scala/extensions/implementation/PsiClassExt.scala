package org.jetbrains.plugins.scala
package extensions.implementation

import com.intellij.psi.{PsiMethod, PsiClass}
import lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition}

/**
 * User: Alefas
 * Date: 03.02.12
 */
class PsiClassExt(clazz: PsiClass) {
  /**
   * Second match branch is for Java only.
   */
  def qualifiedName: String = {
    clazz match {
      case t: ScTemplateDefinition => t.qualifiedName
      case _ => clazz.getQualifiedName
    }
  }

  def constructors: Array[PsiMethod] = {
    clazz match {
      case c: ScClass => c.constructors
      case _ => clazz.getConstructors
    }
  }
}
