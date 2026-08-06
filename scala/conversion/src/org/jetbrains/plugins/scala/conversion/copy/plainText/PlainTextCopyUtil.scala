package org.jetbrains.plugins.scala.conversion.copy.plainText

import com.intellij.codeInsight.daemon.JavaErrorBundle
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.editor.ScalaEditorUtils
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaLanguage}

/**
 * This code assume that Scala files don't have semicolon at the end of line at all and
 * Java files could miss semicolon at the end of line or last curly bracket.
 * Java file without any semicolon could be treated as scala
 */
private object PlainTextCopyUtil {

  private val JavaAllowedErrors = Set(
    JavaErrorBundle.message("expected.semicolon"),
    JavaErrorBundle.message("expected.rbrace")
  )

  private val ScalaAllowedErrors = Set(
    ScalaBundle.message("rbrace.expected"),
    ScalaBundle.message("semi.expected")
  )

  /**
   * Keywords that not present in other languages (primarily Java and Kotlin).
   * This list doesn't have to be exhaustive. It should contain some popular keywords
   * that might be a good indicator of a scala code
   */
  private val ScalaSpecificKeywords = TokenSet.create(
    ScalaTokenTypes.kDEF,
    //ScalaTokenTypes.kCASE, // exists in java switch
    ScalaTokenTypes.kMATCH,
    ScalaTokenTypes.kTYPE,
    ScalaTokenTypes.kIMPLICIT,
    ScalaTokenTypes.kSEALED,
    ScalaTokenTypes.kYIELD,
    ScalaTokenTypes.kWITH,
    ScalaTokenType.TraitKeyword,
    ScalaTokenType.ExportKeyword,
    ScalaTokenType.GivenKeyword,
    ScalaTokenType.UsingKeyword,
    ScalaTokenType.ExtensionKeyword,
    ScalaTokenType.OpaqueKeyword,
    ScalaTokenType.InlineKeyword,
    //ScalaTokenType.ClassKeyword, // exists in java and kotlin
    //ScalaTokenType.ObjectKeyword, // exists in kotlin
    //ScalaTokenTypes.kVAR, // exists in java and kotlin
    //ScalaTokenTypes.kVAL, // exists in kotlin
  )

  private def hasScalaSpecificKeyword(file: ScalaFile): Boolean = file
    .elements
    .exists {
      case leaf: LeafPsiElement => ScalaSpecificKeywords.contains(leaf.getElementType)
      case _ => false
    }

  /**
   * Treat the text as scala file if:
   *   1. it doesn't contain ";\n"
   *   1. or it is not parsed correctly as java code (using "public" modifier as a indicator)
   *   1. or the text is just one word identifier
   *   1. or the text is parsed as scala (allowing some parser errors, see [[createScalaCodeFragmentIfParsedTolerably]])
   */
  def looksLikeScalaFile(text: String, module: Module): Boolean = {
    def withLastSemicolon(text: String): Boolean = (!text.contains("\n") && text.contains(";")) || text.contains(";\n")

    def isOneWord(text: String): Boolean = !text.trim.contains(" ")

    if (isJavaClassWithPublic(text)(module.getProject))
      false
    else if (isOneWord(text))
      true
    else {
      val scalaCodeFragment = createScalaCodeFragmentIfParsedTolerably(text, module)
      scalaCodeFragment match {
        case Some(scalaFile) =>
          !withLastSemicolon(text) || hasScalaSpecificKeyword(scalaFile)
        case None => false
      }
    }
  }

  /**
   * Creates a scala code fragment if it's parsed correctly (except for some potentially incomplete code).<br>
   * For example, this text is detected as a valid scala code fragment: {{{
   *   class Example {
   *     def foo(x: Int): String = //missing implementation (parser error)
   *   }
   * }}}
   */
  def createScalaCodeFragmentIfParsedTolerably(text: String, module: Module): Option[ScalaFile] = {
    val language = module.languageLevel.map(_.getLanguage).getOrElse(ScalaLanguage.INSTANCE)
    val file = ScalaCodeFragment.create(text, language)(module.getProject)
    // allow multiple parser error in code that is supposed to be a scala code candidate
    Some(file).filter(isParsedCorrectlyWithTolerableErrors(_, maxAllowedParserErrors = Int.MaxValue))
  }

  private def isJavaClassWithPublic(text: String)
                                   (implicit project: Project): Boolean = {
    val javaFile = createJavaFile(text)
    javaFile.exists(_.getClasses.exists(_.hasModifierProperty("public")))
  }

  def isValidJavaFile(text: String)
                     (implicit project: Project): Boolean = {
    val javaFile = createJavaFile(text)
    // allow maximum 1 parser error in code that is supposed to be a java code candidate
    javaFile.exists(isParsedCorrectlyWithTolerableErrors(_, maxAllowedParserErrors = 1))
  }

  private def isParsedCorrectlyWithTolerableErrors(
    file: PsiFile,
    maxAllowedParserErrors: Int
  ): Boolean = {
    val errorElements = file.depthFirst().filterByType[PsiErrorElement].toList

    if (errorElements.isEmpty) true
    else {
      val allowedMessages = file match {
        case _: ScalaFile => ScalaAllowedErrors
        case _            => JavaAllowedErrors
      }
      val (allowed, notAllowed) =
        errorElements.partition(isToleratedError(_, allowedMessages))

      notAllowed.isEmpty && allowed.size <= maxAllowedParserErrors
    }
  }

  private def isToleratedError(e: PsiErrorElement, allowedMessages: Set[String]): Boolean =
    allowedMessages.contains(e.getErrorDescription) || ScalaEditorUtils.isIncompleteDefinitionError(e)

  def createJavaFile(text: String)
                    (implicit project: Project): Option[PsiJavaFile] =
    PsiFileFactory.getInstance(project).createFileFromText(
      "Dummy.java",
      JavaFileType.INSTANCE,
      text
    ).asOptionOf[PsiJavaFile]
}
