package org.jetbrains.plugins.scala
package extensions.implementation

import com.intellij.psi.{PsiModifier, PsiMethod, PsiClass}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScClass, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass

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

  def isEffectivelyFinal: Boolean = clazz match {
    case scClass: ScClass => scClass.hasFinalModifier
    case _: ScObject => true
    case synth: ScSyntheticClass if !Seq("AnyRef", "AnyVal").contains(synth.className) => true //wrappers for value types
    case _ => clazz.hasModifierProperty(PsiModifier.FINAL)
  }
}
