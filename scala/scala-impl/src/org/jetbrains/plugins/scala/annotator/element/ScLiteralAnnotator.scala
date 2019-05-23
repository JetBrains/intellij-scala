package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScPrefixExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeFromText
import org.jetbrains.plugins.scala.project._

object ScLiteralAnnotator extends ElementAnnotator[ScLiteral] {

  import lang.lexer.{ScalaTokenTypes => T}

  private val StringLiteralSizeLimit = 65536
  private val StringCharactersCountLimit = StringLiteralSizeLimit / 4

  override def annotate(literal: ScLiteral,
                        holder: AnnotationHolder,
                        typeAware: Boolean): Unit = {
    implicit val implicitHolder: AnnotationHolder = holder
    implicit val containingFile: PsiFile = literal.getContainingFile

    val node = literal.getFirstChild.getNode
    (literal, node.getElementType) match {
      case (_, T.tINTEGER) =>
        checkIntegerLiteral(node, literal)
      case (_, T.tSTRING |
               T.tWRONG_STRING |
               T.tMULTILINE_STRING) =>
        createStringIsTooLongAnnotation(literal) { literal =>
          Option(literal.getValue.asInstanceOf[String])
        }
      case (interpolatedStringLiteral: ScInterpolatedStringLiteral, _) =>
        createStringIsTooLongAnnotation(interpolatedStringLiteral)(_.getStringParts)
      case _ =>
    }
  }

  private def createStringIsTooLongAnnotation[L <: ScLiteral](literal: L)
                                                             (strings: L => Traversable[String])
                                                             (implicit holder: AnnotationHolder,
                                                              containingFile: PsiFile) =
    if (strings(literal).exists(stringIsTooLong)) {
      holder.createErrorAnnotation(literal, ScalaBundle.message("string.literal.is.too.long"))
    }

  private def stringIsTooLong(string: String)
                             (implicit containingFile: PsiFile): Boolean = string.length match {
    case length if length >= StringLiteralSizeLimit => true
    case length if length >= StringCharactersCountLimit => utf8Size(string) >= StringLiteralSizeLimit
    case _ => false
  }

  private def utf8Size(string: String)
                      (implicit containingFile: PsiFile): Int = {
    val lineSeparator = Option(containingFile)
      .flatMap(file => Option(file.getVirtualFile))
      .flatMap(virtualFile => Option(virtualFile.getDetectedLineSeparator))
      .getOrElse(Option(System.lineSeparator).getOrElse("\n"))

    string.map {
      case '\n' => lineSeparator.length
      case '\r' => 0
      case character if character >= 0 && character <= '\u007F' => 1
      case character if character >= '\u0080' && character <= '\u07FF' => 2
      case character if character >= '\u0800' && character <= '\uFFFF' => 3
      case _ => 4
    }.sum
  }

  private def checkIntegerLiteral(node: ASTNode, literal: ScLiteral)
                                 (implicit holder: AnnotationHolder) {
    val text = literal.getText
    val endsWithL = node.getText.endsWith("l") || node.getText.endsWith("L")
    val textWithoutL = if (endsWithL) text.substring(0, text.length - 1) else text
    val parent = literal.getParent
    val scalaVersion = literal.scalaLanguageLevel
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
      val convertFix = new quickfix.ConvertOctalToHexFix(literal)
      scalaVersion match {
        case Some(ScalaLanguageLevel.Scala_2_10) =>
          val deprecatedMeaasge = "Octal number is deprecated in Scala-2.10 and will be removed in Scala-2.11"
          val annotation = holder.createWarningAnnotation(literal, deprecatedMeaasge)
          annotation.setHighlightType(ProblemHighlightType.LIKE_DEPRECATED)
          annotation.registerFix(convertFix)
        case Some(version) if version >= ScalaLanguageLevel.Scala_2_11 =>
          val error = "Octal number is removed in Scala-2.11 and after"
          val annotation = holder.createErrorAnnotation(literal, error)
          annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
          annotation.registerFix(convertFix)
          return
        case _ =>
      }
    }
    val (_, status) = parseIntegerNumber(number, isNegative)
    if (status == 2) { // the Integer number is out of range even for Long
      val error = "Integer number is out of range even for type Long"
      holder.createErrorAnnotation(literal, error)
    } else {
      if (status == 1 && !endsWithL) {
        val error = "Integer number is out of range for type Int"
        val annotation = if (isNegative) holder.createErrorAnnotation(parent, error) else holder.createErrorAnnotation(literal, error)

        val Long = literal.projectContext.stdTypes.Long
        val conformsToTypeList = Seq(Long) ++ createTypeFromText("_root_.scala.math.BigInt", literal.getContext, literal)
        val shouldRegisterFix = (if (isNegative) parent.asInstanceOf[ScPrefixExpr] else literal).expectedType().forall { x =>
          conformsToTypeList.exists(_.weakConforms(x))
        }

        if (shouldRegisterFix) {
          annotation.registerFix(new quickfix.AddLToLongLiteralFix(literal))
        }
      }
    }
  }
}
