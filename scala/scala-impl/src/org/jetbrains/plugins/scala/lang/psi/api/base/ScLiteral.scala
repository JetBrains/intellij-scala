package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import org.apache.commons.lang.StringEscapeUtils.escapeJava
import org.jetbrains.plugins.scala.lang.psi.api.base.literals._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaType, api}

/**
 * @author Alexander Podkhalyuzin
 *         Date: 22.02.2008
 */
trait ScLiteral extends ScExpression with PsiLiteral with PsiLanguageInjectionHost {

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

  trait NumericValue {

    def negate: Value[_] with NumericValue
  }

  object Value {

    def unapply(obj: Any): Option[Value[_]] = Option {
      obj match {
        case integer: Int => ScIntegerLiteral.Value(integer)
        case long: Long => ScLongLiteral.Value(long)
        case float: Float => ScFloatLiteral.Value(float)
        case double: Double => ScDoubleLiteral.Value(double)
        case boolean: Boolean => ScBooleanLiteral.Value(boolean)
        case character: Char => ScCharLiteral.Value(character)
        case string: String => StringValue(string)
        case symbol: Symbol => ScSymbolLiteral.Value(symbol)
        case _ => null
      }
    }
  }

  final case class StringValue(override val value: String) extends Value(value) {

    override def presentation: String = "\"" + escapeJava(super.presentation) + "\""

    override def wideType(implicit project: Project): ScType = cachedClass(CommonClassNames.JAVA_LANG_STRING)
  }
}