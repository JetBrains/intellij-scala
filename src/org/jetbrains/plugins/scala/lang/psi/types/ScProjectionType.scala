package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.psi.{PsiElement, PsiClass, PsiNamedElement}
import api.toplevel.typedef.ScTemplateDefinition
import com.intellij.psi.util.PsiTreeUtil
import resolve.ResolveUtils
import api.expr.ScNewTemplateDefinition
import result.TypingContext


/**
* @author ilyas
*/

/**
 * This type means type projection:
 * SomeType#member
 * member can be class or type alias
 */
case class ScProjectionType(projected: ScType, element: PsiNamedElement, subst: ScSubstitutor) extends ValueType {
  override def removeAbstracts = ScProjectionType(projected.removeAbstracts, element, subst)

  override def updateThisType(place: PsiElement): ScType = {
    ScProjectionType(projected.updateThisType(place), element, subst)
  }
}

/**
 * This type means type, which depends on place, where you want to get expression type.
 * For example
 *
 * class A {
 *   def foo: this.type = this
 * }
 *
 * class B extneds A {
 *   val z = foo // <- type in this place is B.this.type, not A.this.type
 * }
 *
 * So when expression is typed, we should replace all such types be return value.
 */
case class ScThisType(tp: ScType) extends ValueType {
  override def updateThisType(place: PsiElement): ScType = {
    def workWithClazz(clazz: ScTemplateDefinition): ScType = {
      var td: ScTemplateDefinition = ScalaPsiUtil.getPlaceTd(place)
      while (td != null) {
        if (td == clazz || td.isInheritor(clazz, true)) return ScThisType(td.getType(TypingContext.empty).getOrElse(return tp))
        td = ScalaPsiUtil.getPlaceTd(td)
      }
      tp.updateThisType(place)
    }
    tp match {
      case ScParameterizedType(ScDesignatorType(clazz: ScTemplateDefinition), _) => {
        workWithClazz(clazz)
      }
      case ScDesignatorType(clazz: ScTemplateDefinition) => {
        workWithClazz(clazz)
      }
      case _ => tp.updateThisType(place)
    }
  }
}

/**
 * This type means normal designator type.
 * It can be whether singleton type (v.type) or simple type (java.lang.String).
 * element can be any stable element, class, value or type alias
 */
case class ScDesignatorType(element: PsiNamedElement) extends ValueType {
  private var isStaticClass = false
  //You can use this method to check if it's Java class,
  // which is used for getting static context => no implicit conversion
  def isStatic = isStaticClass
  def this(elem: PsiNamedElement, isStaticClass: Boolean) {
    this(elem)
    this.isStaticClass = isStaticClass
  }
}