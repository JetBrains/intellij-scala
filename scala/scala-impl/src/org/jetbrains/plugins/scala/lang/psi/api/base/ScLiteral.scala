package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiAnnotationOwner, PsiElement, PsiLanguageInjectionHost, PsiLiteral}
import org.apache.commons.lang.StringEscapeUtils.escapeJava
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaType, api}

/**
  * @author Alexander Podkhalyuzin
  *         Date: 22.02.2008
  */

trait ScLiteral extends ScExpression with PsiLiteral with PsiLanguageInjectionHost {

  // This method works only for null literal (to avoid possibly dangerous usage)
  def typeForNullWithoutImplicits_=(`type`: Option[ScType])

  def typeForNullWithoutImplicits: Option[ScType]

  def isString: Boolean

  def isMultiLineString: Boolean

  def getAnnotationOwner(annotationOwnerLookUp: ScLiteral => Option[PsiAnnotationOwner with PsiElement]): Option[PsiAnnotationOwner]

  def isSymbol: Boolean

  def isChar: Boolean

  def contentRange: TextRange
}

object ScLiteral {

  def unapply(literal: ScLiteral) = Option(Value(literal))

  sealed abstract class Value[V <: Any](val value: V) {

    def presentation: String = String.valueOf(value)

    def wideType(implicit project: Project): ScType
  }

  sealed trait NumericValue {

    def negate: Value[_] with NumericValue
  }

  object Value {

    def apply(literal: ScLiteral): Value[_] =
      applyImpl(literal, literal.getFirstChild.getNode)

    def unapply(obj: Any): Option[Value[_]] = Option {
      obj match {
        case integer: Int => IntegerValue(integer)
        case long: Long => LongValue(long)
        case float: Float => FloatValue(float)
        case double: Double => DoubleValue(double)
        case boolean: Boolean => BooleanValue(boolean)
        case character: Char => CharacterValue(character)
        case string: String => StringValue(string)
        case symbol: Symbol => SymbolValue(symbol)
        case _ => null
      }
    }

    import lang.lexer.{ScalaTokenTypes => T}

    @annotation.tailrec
    private def applyImpl(literal: ScLiteral, node: ASTNode): Value[_] = {

      def value[T: reflect.ClassTag] = literal.getValue.asInstanceOf[T]

      (node.getElementType, node.getText) match {
        case (T.tIDENTIFIER, "-") => applyImpl(literal, node.getTreeNext)
        case (T.tINTEGER, text) =>
          if (text.matches(".*[lL]$")) LongValue(value[Long])
          else IntegerValue(value[Int]) // but a conversion exists to narrower types in case range fits
        case (T.tFLOAT, text) =>
          if (text.matches(".*[fF]$")) FloatValue(value[Float])
          else DoubleValue(value[Double])
        case (T.kTRUE |
              T.kFALSE, _) => BooleanValue(value[Boolean])
        case (T.tCHAR, _) => CharacterValue(value[Char])
        case (T.tSTRING |
              T.tMULTILINE_STRING, _) => StringValue(value[String])
        case (T.tWRONG_STRING, _) => Option(value[String]).map(StringValue).orNull
        case (T.tSYMBOL, _) => SymbolValue(value[Symbol])
        case (T.kNULL, _) => NullValue
        case _ => null
      }
    }
  }

  final case class IntegerValue(override val value: Int) extends Value(value) with NumericValue {

    override def negate = IntegerValue(-value)

    override def wideType(implicit project: Project): ScType = api.Int
  }

  final case class LongValue(override val value: Long) extends Value(value) {

    override def presentation: String = super.presentation + 'L'

    override def wideType(implicit project: Project): ScType = api.Long
  }

  final case class FloatValue(override val value: Float) extends Value(value) {

    override def presentation: String = super.presentation + 'f'

    override def wideType(implicit project: Project): ScType = api.Float
  }

  final case class DoubleValue(override val value: Double) extends Value(value) {

    override def wideType(implicit project: Project): ScType = api.Double
  }

  final case class BooleanValue(override val value: Boolean) extends Value(value) {

    override def wideType(implicit project: Project): ScType = api.Boolean
  }

  final case class CharacterValue(override val value: Char) extends Value(value) {

    override def presentation: String = '\'' + super.presentation + '\''

    override def wideType(implicit project: Project): ScType = api.Char
  }

  final case class StringValue(override val value: String) extends Value(value) {

    override def presentation: String = "\"" + escapeJava(super.presentation) + "\""

    override def wideType(implicit project: Project): ScType = cachedClass("java.lang.String")
  }

  final case class SymbolValue(override val value: Symbol) extends Value(value) {

    override def wideType(implicit project: Project): ScType = cachedClass("scala.Symbol")
  }

  final case object NullValue extends Value(null) {

    override def wideType(implicit project: Project): ScType = api.Null
  }

  private[this] def cachedClass(fqn: String)
                               (implicit project: Project) =
    ElementScope(project).getCachedClass(fqn)
      .fold(api.Nothing: ScType)(ScalaType.designator)
}