package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.extensions.ElementText
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
                                 (implicit holder: AnnotationHolder): Unit = {
    val text = literal.getText
    val endsWithL = node.getText.endsWith("l") || node.getText.endsWith("L")

    val numberText = text.substring(
      0,
      text.length - (if (endsWithL) 1 else 0)
    )

    val maybeParent = literal.getParent match {
      case prefixExpr: ScPrefixExpr =>
        // only "-1234" is negative, "- 1234" should be considered as positive 1234
        prefixExpr.getChildren match {
          case Array(ElementText("-"), _) => Some(prefixExpr)
          case _ => None
        }
      case _ => None
    }

    val (beginIndex, base, divider) = numberText match {
      case t if t.startsWith("0x") || t.startsWith("0X") => (2, 16, 2)
      case t if t.startsWith("0") && t.length >= 2 => (1, 8, 2)
      case _ => (0, 10, 1)
    }

    if (base == 8) {
      literal.scalaLanguageLevel match {
        case Some(version) if version >= ScalaLanguageLevel.Scala_2_11 =>
          createAnnotation(
            literal,
            "Octal number is removed in Scala-2.11 and after"
          )
          return
        case Some(ScalaLanguageLevel.Scala_2_10) =>
          createAnnotation(
            literal,
            "Octal number is deprecated in Scala-2.10 and will be removed in Scala-2.11",
            ProblemHighlightType.LIKE_DEPRECATED
          )
        case _ =>
      }
    }

    parseIntegerNumber(
      numberText.substring(beginIndex),
      base,
      divider,
      maybeParent.isDefined
    ) match {
      case None =>
        holder.createErrorAnnotation(
          literal,
          "Integer number is out of range even for type Long"
        )
      case Some(Right(_)) if !endsWithL =>
        val expression = maybeParent.getOrElse(literal)
        val annotation = holder.createErrorAnnotation(
          expression,
          "Integer number is out of range for type Int"
        )

        val Long = literal.projectContext.stdTypes.Long
        val conformsToTypeList = Seq(Long) ++ createTypeFromText("_root_.scala.math.BigInt", literal.getContext, literal)
        val shouldRegisterFix = expression.expectedType().forall { x =>
          conformsToTypeList.exists(_.weakConforms(x))
        }

        if (shouldRegisterFix) {
          annotation.registerFix(new quickfix.AddLToLongLiteralFix(literal))
        }
      case _ =>
    }
  }

  private def createAnnotation(literal: ScLiteral, message: String,
                               highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR)
                              (implicit holder: AnnotationHolder): Unit = {
    val annotation = highlightType match {
      case ProblemHighlightType.GENERIC_ERROR => holder.createErrorAnnotation(literal, message)
      case _ => holder.createWarningAnnotation(literal, message)
    }
    annotation.setHighlightType(highlightType)
    annotation.registerFix(new quickfix.ConvertOctalToHexFix(literal))
  }

  private[this] def parseIntegerNumber(number: String,
                                       base: Int, divider: Int,
                                       isNegative: Boolean) =
    stringToNumber(number, base, divider, isNegative)().map {
      case (value, false) => Left(value.toInt)
      case (value, _) => Right(value)
    }

  @annotation.tailrec
  private[this] def stringToNumber(number: String,
                                   base: Int,
                                   divider: Int,
                                   isNegative: Boolean)
                                  (index: Int = 0,
                                   value: Long = 0L,
                                   exceedsIntLimit: Boolean = false): Option[(Long, Boolean)] =
    if (index == number.length) {
      val newValue = if (isNegative) -value else value
      Some(newValue, exceedsIntLimit)
    } else {
      val digit = number(index).asDigit
      val newValue = value * base + digit

      def exceedsLimit(limit: Long) =
          limit / (base / divider) < value ||
          limit - (digit / divider) < value * (base / divider) &&
            !(isNegative && limit == newValue - 1)

      if (value < 0 || exceedsLimit(java.lang.Long.MAX_VALUE)) None
      else stringToNumber(number, base, divider, isNegative)(
        index + 1,
        newValue,
        value > Integer.MAX_VALUE || exceedsLimit(Integer.MAX_VALUE)
      )
    }
}
