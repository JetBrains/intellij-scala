package org.jetbrains.plugins.scala
package lang.psi.types

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiParameterExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.ScType.ExtractClass

/**
 * Nikolay.Tropin
 * 2014-10-02
 */
object ValueClassType {
  def unapply(tp: ScType): Option[ScType] = {
    tp match {
      case _: ValType => None
      case ExtractClass(cl: ScClass) if isValueClass(cl) =>
        cl.constructors match {
          case Array(pc: ScPrimaryConstructor) =>
            pc.parameters.headOption.map(_.exactParamType())
          case _ => None
        }
      case _ => None
    }
  }

  def isValueClass(cl: PsiClass) = cl match {
    case scClass: ScClass => scClass.supers.map(_.qualifiedName).contains("scala.AnyVal")
    case _ => false
  }
}
