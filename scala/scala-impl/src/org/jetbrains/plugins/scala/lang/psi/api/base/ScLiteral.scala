package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaType, api}

/**
 * @author Alexander Podkhalyuzin
 *         Date: 22.02.2008
 */
trait ScLiteral extends ScExpression
  with PsiLiteral
  with PsiLanguageInjectionHost {

  protected type V >: Null <: AnyRef

  override def getValue: V

  def isString: Boolean

  def isMultiLineString: Boolean

  def contentRange: TextRange
}

object ScLiteral {

  val CharQuote = "\'"

  def unapply(literal: ScLiteral): Option[String] = literal.getValue match {
    case string: String => Some(string)
    case _ => None
  }

  abstract class Value[V <: Any](val value: V) {

    def presentation: String = String.valueOf(value)

    def wideType(implicit project: Project): ScType

    protected final def cachedClass(fqn: String)
                                   (implicit project: Project): ScType =
      ElementScope(project).getCachedClass(fqn)
        .fold(api.Nothing: ScType)(ScalaType.designator)
  }
}