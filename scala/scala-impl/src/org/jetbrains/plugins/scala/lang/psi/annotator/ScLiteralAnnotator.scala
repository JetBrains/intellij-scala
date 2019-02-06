package org.jetbrains.plugins.scala.lang.psi.annotator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.quickfix.{AddLToLongLiteralFix, ConvertOctalToHexFix}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.Annotatable
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScPrefixExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeFromText
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.util.MultilineStringUtil
import org.jetbrains.plugins.scala.extensions._

import scala.collection.Seq


trait ScLiteralAnnotator extends Annotatable { self: ScLiteral =>

  abstract override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    this match {
      case _ if this.getFirstChild.getNode.getElementType == ScalaTokenTypes.tINTEGER => // the literal is a tINTEGER
        checkIntegerLiteral(holder)
      case _ =>
    }

    if (MultilineStringUtil.isTooLongStringLiteral(this)) {
      holder.createErrorAnnotation(this, ScalaBundle.message("too.long.string.literal"))
    }
  }

  private def checkIntegerLiteral(holder: AnnotationHolder) {
    val child = getFirstChild.getNode
    val text = getText
    val endsWithL = child.getText.endsWith("l") || child.getText.endsWith("L")
    val textWithoutL = if (endsWithL) text.substring(0, text.length - 1) else text
    val parent = getParent
    val scalaVersion = self.scalaLanguageLevel
    val isNegative = parent match {
      // only "-1234" is negative, "- 1234" should be considered as positive 1234
      case prefixExpr: ScPrefixExpr if prefixExpr.getChildren.length == 2 && prefixExpr.getFirstChild.getText == "-" => true
      case _ => false
    }
    val (number, base) = textWithoutL match {
      case t if t.startsWith("0x") || t.startsWith("0X") => (t.substring(2), 16)
      case t if t.startsWith("0") && t.length >= 2 => (t.substring(1), 8)
      case t => (t, 10)
    }

    // parse integer literal. the return is (Option(value), statusCode)
    // the Option(value) will be the real integer represented by the literal, if it cannot fit in Long, It's None
    // there is 3 value for statusCode:
    // 0 -> the literal can fit in Int
    // 1 -> the literal can fit in Long
    // 2 -> the literal cannot fit in Long
    def parseIntegerNumber(text: String, isNegative: Boolean): (Option[Long], Byte) = {
      var value = 0l
      val divider = if (base == 10) 1 else 2
      var statusCode: Byte = 0
      val limit = java.lang.Long.MAX_VALUE
      val intLimit = java.lang.Integer.MAX_VALUE
      var i = 0
      for (d <- number.map(_.asDigit)) {
        if (value > intLimit ||
          intLimit / (base / divider) < value ||
          intLimit - (d / divider) < value * (base / divider) &&
            // This checks for -2147483648, value is 214748364, base is 10, d is 8. This check returns false.
            // base 8 and 16 won't have this check because the divider is 2        .
            !(isNegative && intLimit == value * base - 1 + d)) {
          statusCode = 1
        }
        if (value < 0 ||
          limit / (base / divider) < value ||
          limit - (d / divider) < value * (base / divider) &&
            // This checks for Long.MinValue, same as the the previous Int.MinValue check.
            !(isNegative && limit == value * base - 1 + d)) {
          return (None, 2)
        }
        value = value * base + d
        i += 1
      }
      value = if (isNegative) -value else value
      if (statusCode == 0) (Some(value.toInt), 0) else (Some(value), statusCode)
    }

    if (base == 8) {
      val convertFix = new ConvertOctalToHexFix(this)
      scalaVersion match {
        case Some(ScalaLanguageLevel.Scala_2_10) =>
          val deprecatedMeaasge = "Octal number is deprecated in Scala-2.10 and will be removed in Scala-2.11"
          val annotation = holder.createWarningAnnotation(this, deprecatedMeaasge)
          annotation.setHighlightType(ProblemHighlightType.LIKE_DEPRECATED)
          annotation.registerFix(convertFix)
        case Some(version) if version >= ScalaLanguageLevel.Scala_2_11 =>
          val error = "Octal number is removed in Scala-2.11 and after"
          val annotation = holder.createErrorAnnotation(this, error)
          annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
          annotation.registerFix(convertFix)
          return
        case _ =>
      }
    }
    val (_, status) = parseIntegerNumber(number, isNegative)
    if (status == 2) { // the Integer number is out of range even for Long
      val error = "Integer number is out of range even for type Long"
      holder.createErrorAnnotation(this, error)
    } else {
      if (status == 1 && !endsWithL) {
        val error = "Integer number is out of range for type Int"
        val annotation = if (isNegative) holder.createErrorAnnotation(parent, error) else holder.createErrorAnnotation(this, error)

        val Long = projectContext.stdTypes.Long
        val conformsToTypeList = Seq(Long) ++ createTypeFromText("_root_.scala.math.BigInt", getContext, this)
        val shouldRegisterFix = (if (isNegative) parent.asInstanceOf[ScPrefixExpr] else this).expectedType().forall { x =>
          conformsToTypeList.exists(_.weakConforms(x))
        }

        if (shouldRegisterFix) {
          val addLtoLongFix: AddLToLongLiteralFix = new AddLToLongLiteralFix(this)
          annotation.registerFix(addLtoLongFix)
        }
      }
    }
  }
}
