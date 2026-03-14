package org.jetbrains.plugins.scala.annotator.element

import com.intellij.codeInsight.intention.{FileModifier, IntentionAction}
import com.intellij.codeInspection.{IntentionAndQuickFixAction, ProblemHighlightType}
import com.intellij.lang.ASTNode
import com.intellij.lexer.StringLiteralLexer
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.util.{Segment, TextRange}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.{PsiDocumentManager, PsiFile, StringEscapesTokenTypes}
import org.jetbrains.annotations.{Nls, Nullable}
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.highlighter.lexer.{ScalaInterpolatedStringLiteralLexer, ScalaMultilineStringLiteralLexer, ScalaStringLiteralLexer}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.{tINTERPOLATED_MULTILINE_STRING, tINTERPOLATED_STRING, tMULTILINE_STRING, tSTRING}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.project.{ScalaLanguageLevel, Version}
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaVersion}

import scala.util.Try

/** see also [[org.jetbrains.plugins.scala.annotator.element.ScInterpolatedStringLiteralAnnotator]] */
object ScStringLiteralAnnotator extends ElementAnnotator[ScStringLiteral] {

  private val StringLiteralSizeLimit = 65536
  private val StringCharactersCountLimit = StringLiteralSizeLimit / 4

  import scala.util.chaining.scalaUtilChainingOps

  private val SET_STRING                        = tSTRING.pipe(t => (t, TokenSet.create(t)))
  private val SET_MULTILINE_STRING              = tMULTILINE_STRING.pipe(t => (t, TokenSet.create(t)))
  private val SET_INTERPOLATED_STRING           = tINTERPOLATED_STRING.pipe(t => (t, TokenSet.create(t)))
  private val SET_INTERPOLATED_MULTILINE_STRING = tINTERPOLATED_MULTILINE_STRING.pipe(t => (t, TokenSet.create(t)))

  override def annotate(literal: ScStringLiteral, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    if (annotateTooLongString(literal))
      return

    annotateInvalidEscapeSequences(literal)
  }

  private def annotateInvalidEscapeSequences(literal: ScStringLiteral)
                                            (implicit holder: ScalaAnnotationHolder): Unit = {
    val isInterpolated = literal.is[ScInterpolatedStringLiteral]
    val isMultiline = literal.isMultiLineString

    val (tokenType, tokenSet) = (isInterpolated, isMultiline) match {
      case (false, false) => SET_STRING
      case (false, true)  => SET_MULTILINE_STRING
      case (true, false)  => SET_INTERPOLATED_STRING
      case (true, true)   => SET_INTERPOLATED_MULTILINE_STRING
    }

    val isRaw = literal.asOptionOf[ScInterpolatedStringLiteral].exists(_.kind == ScInterpolatedStringLiteral.Kind.Raw)

    val scalaMinorVersion = literal.scalaMinorVersion
    val isSource3Enabled = literal.isSource3Enabled
    val unicodeEscapesRaw = literal.source3Options.unicodeEscapesRaw

    //note: under the hood noUnicodeEscapesInRawStrings is calculated based
    val noUnicodeEscapesInRawStrings = literal.noUnicodeEscapesInRawStrings

    val lexer: ScalaStringLiteralLexer = (isInterpolated, isMultiline) match {
      case (false, false) => new ScalaStringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, tokenType)
      case (false, true)  => new ScalaMultilineStringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, tokenType, noUnicodeEscapesInRawStrings = noUnicodeEscapesInRawStrings)
      case (true, _)  => new ScalaInterpolatedStringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, tokenType, isRaw, isMultiline, noUnicodeEscapesInRawStrings = noUnicodeEscapesInRawStrings)
    }

    val stringLeafNodes = literal.getNode.getChildren(tokenSet)
    stringLeafNodes.foreach(annotateInvalidEscapeSequences(
      literal,
      _,
      lexer,
      isMultiline = isMultiline,
      isInterpolated = isInterpolated,
      isRawInterpolated = isRaw,
      scalaVersion = scalaMinorVersion,
      isSource3Enabled = isSource3Enabled,
      unicodeEscapesRaw = unicodeEscapesRaw,
    ))
  }

  // NOTE: in a platform, lexer is reused during highlighting, so we also reuse to catch potential issues
  private def annotateInvalidEscapeSequences(
    literal: ScLiteral,
    node: ASTNode,
    lexer: ScalaStringLiteralLexer,
    isMultiline: Boolean,
    isInterpolated: Boolean,
    isRawInterpolated: Boolean,
    scalaVersion: Option[ScalaVersion],
    isSource3Enabled: Boolean,
    unicodeEscapesRaw: Boolean,
  )(implicit holder: ScalaAnnotationHolder): Unit = {
    lexer.start(node.getChars)

    var tokenType = lexer.getTokenType
    while (tokenType != null) {
      def range = TextRange.create(lexer.getTokenStart, lexer.getTokenEnd).shiftRight(node.getStartOffset)

      tokenType match {
        case StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN =>
          holder.createErrorAnnotation(range, ScalaBundle.message("string.literal.invalid.escape.character"))
        case StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN   =>
          holder.createErrorAnnotation(range, ScalaBundle.message("string.literal.invalid.unicode.escape"))
        case StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN if isRawInterpolated || isMultiline && !isInterpolated =>
          scalaVersion match {
            case Some(v) if v.languageLevel == ScalaLanguageLevel.Scala_2_13 && v.minorVersion >= Version("14") =>
              registerUnicodeEscapeWarningOrErrorIfNeeded(
                literal,
                range,
                lexer.getTokenText,
                isSource3Enabled = isSource3Enabled,
                unicodeEscapesRaw = unicodeEscapesRaw,
                isRawInterpolated = isRawInterpolated
              )
            case _ =>
          }
        case _ =>
      }

      lexer.advance()
      tokenType = lexer.getTokenType
    }
  }

  private def registerUnicodeEscapeWarningOrErrorIfNeeded(
    literal: ScLiteral,
    range: => TextRange,
    unicodeSequence: String,
    isRawInterpolated: Boolean,
    isSource3Enabled: Boolean,
    unicodeEscapesRaw: Boolean,
  )(implicit holder: ScalaAnnotationHolder): Unit = {
    val unicodeSequenceBody = unicodeSequence.drop(2) //drop \u from \u0123 and leave just 0123
    //NOTE: don't create a fix in case of an invalid Unicode sequence
    val char = Try(Integer.parseInt(unicodeSequenceBody, 16).toChar).toOption

    val document = PsiDocumentManager.getInstance(literal.getProject).getDocument(holder.getCurrentAnnotationSession.getFile)

    // Use the range marker instead of just a range in case when the user types quickly before applying the quick fix.
    // In this case the original range might point to a wrong range of code
    lazy val segment: Segment = if (document != null)
      document.createRangeMarker(range)
    else
      range

    // This is a very rare edge-case.
    // Handle non-interpolated multiline string literal with a unicode escape of '\r' symbol ("""\u000D""")/
    // To truly represent this without using the Unicode escape sequence,
    // we would need to convert the string to a raw-interpolated string and use ${'\\r}.
    // But it would be too much for the fix, so we don't provide any.
    // Users should decide for themselves what to do
    val cantProvideQuickFix = char.contains('\r') && !isRawInterpolated
    lazy val quickFix: Option[IntentionAction] = if (cantProvideQuickFix) None else char.map(new ReplaceUnicodeEscapeWithLiteralCharacterQuickFix(
      segment,
      _,
      isInterpolated = isRawInterpolated,
    ))
    (isSource3Enabled, unicodeEscapesRaw) match {
      case (false, _) =>
        @Nls val message = if (isRawInterpolated)
          ScalaBundle.message("unicode.escapes.in.raw.interpolations.are.deprecated")
        else
          ScalaBundle.message("unicode.escapes.in.triple.quoted.strings.are.deprecated")
        holder.createWarningAnnotation(range, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, quickFix)
      case (true, false) =>
        @Nls val message = if (isRawInterpolated)
          ScalaBundle.message("unicode.escapes.in.raw.interpolations.are.ignored")
        else
          ScalaBundle.message("unicode.escapes.in.triple.quoted.strings.are.ignored")
        holder.createErrorAnnotation(range, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, quickFix)
      case (true, _) => //do nothing
    }
  }

  private def annotateTooLongString(literal: ScStringLiteral)
                                   (implicit holder: ScalaAnnotationHolder): Boolean = {
    val isTooLong = literal match {
      case interpolated: ScInterpolatedStringLiteral => isTooLongLiteral(interpolated, interpolated.getStringParts: _*)
      case ScStringLiteral(string)                   => isTooLongLiteral(literal, string)
      case _                                         => false
    }
    if (isTooLong) {
      holder.createErrorAnnotation(literal, ScalaBundle.message("string.literal.is.too.long"))
    }
    isTooLong
  }

  private def isTooLongLiteral(literal: ScStringLiteral, strings: String*): Boolean = {
    implicit val virtualFile: Option[VirtualFile] = literal.containingVirtualFile
    strings.exists(exceedsLimit)
  }

  private def exceedsLimit(string: String)
                          (implicit virtualFile: Option[VirtualFile]): Boolean = string.length match {
    case length if length >= StringLiteralSizeLimit => true
    case length if length >= StringCharactersCountLimit => utf8Size(string) >= StringLiteralSizeLimit
    case _ => false
  }

  private def utf8Size(string: String)
                      (implicit virtualFile: Option[VirtualFile]): Int = {
    val lineSeparator = virtualFile
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

  /**
   * @param segment represents a range of the Unicode escape sequence.<br>
   *                The implementation can be a range marker when the quick fix is registered in the document or
   *                just a fixed range when the error was added in batch mode
   * @note Extending [[IntentionAndQuickFixAction]] in order the fix can be applied in the batch mode
   *       (when running "Inspect code" in a project/module)
   */
  private final class ReplaceUnicodeEscapeWithLiteralCharacterQuickFix(
    segment: Segment,
    unicodeSequenceChar: Char,
    isInterpolated: Boolean,
  ) extends IntentionAndQuickFixAction with DumbAware {

    override def getName: String = ScalaBundle.message("replace.with.literal.character", unicodeSequenceChar)

    override def getFamilyName: String = getName

    override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
      findDocument(project, file, editor) != null

    override def startInWriteAction(): Boolean = true

    override def getFileModifierForPreview(target: PsiFile): FileModifier = this

    override def applyFix(project: Project, file: PsiFile, @Nullable editor: Editor): Unit = {
      val range = TextRange.create(segment)
      val document = findDocument(project, file, editor)
      if (document != null) {
        val replacedString: String = unicodeSequenceChar match {
          case '$' if isInterpolated => "$$"
          case '\n'  => "${'\\n'}"
          case '\r'  => "${'\\r'}"
          case other => other.toString
        }

        document.replaceString(range.getStartOffset, range.getEndOffset, replacedString)
        document.commit(project)
      }
    }

    private def findDocument(project: Project, file: PsiFile, @Nullable editor: Editor): Document =
      if (editor != null) //NOTE: the editor can be null in batch mode
        editor.getDocument
      else
        PsiDocumentManager.getInstance(project).getDocument(file)
  }
}
