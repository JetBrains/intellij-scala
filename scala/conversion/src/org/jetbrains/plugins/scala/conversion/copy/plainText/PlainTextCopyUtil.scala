package org.jetbrains.plugins.scala.conversion.copy.plainText


import com.intellij.codeInsight.daemon.JavaErrorBundle
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDefinitionWithAssignment
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaLanguage}

/**
  * This code assume that Scala files don't have semicolon at the end of line at all and
  * Java files could miss semicolon at the end of line or last curly bracket.
  * Java file without any semicolon could be treated as scala
  */
object PlainTextCopyUtil {

  private val JavaAllowedErrors = Set(
    JavaErrorBundle.message("expected.semicolon"),
    JavaErrorBundle.message("expected.rbrace")
  )

  private val ScalaAllowedErrors = Set(
    ScalaBundle.message("rbrace.expected"),
    ScalaBundle.message("semi.expected")
  )
  
  private val ErrorsAfterIncompleteDefinitionWithAssignment = Set(
    // Note, for some reason the error is different in some cases, see SCL-23798
    ScalaBundle.message("expression.expected"), //example: def foo = //implement me
    ScalaBundle.message("wrong.expression"), //example: def foo: String = //implement me
    ScalaBundle.message("wrong.type"), //example: type X =
  )

  /**
    * Treat the text as scala file if:
   *   1. it doesn't contain ";\n"
   *   1. or it is not parsed correctly as java code (using "public" modifier as a indicator)
   *   1. or the text is just one word identifier
   *   1. or the text is parsed as scala (allowing some parser errors, see [[createScalaCodeFragment]])
    */
  def looksLikeScalaFile(text: String, module: Module): Boolean = {
    def withLastSemicolon(text: String): Boolean = (!text.contains("\n") && text.contains(";")) || text.contains(";\n")

    def isOneWord(text: String): Boolean = !text.trim.contains(" ")

    if (withLastSemicolon(text) || isJavaClassWithPublic(text)(module.getProject))
      false
    else if (isOneWord(text))
      true
    else {
      val scalaFile = createScalaCodeFragment(text, module)
      scalaFile.isDefined
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
  def createScalaCodeFragment(text: String, module: Module): Option[ScalaCodeFragment] = {
    val language = module.languageLevel.map(_.getLanguage).getOrElse(ScalaLanguage.INSTANCE)
    val scalaCode = ScalaCodeFragment.create(text, language)(module.getProject)
    // allow multiple parser error in code that is supposed to be a scala code candidate
    Some(scalaCode).filter(isParsedCorrectly(_, maxAllowedParserErrors = Int.MaxValue))
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
    javaFile.exists(isParsedCorrectly(_, maxAllowedParserErrors = 1))
  }

  private def isParsedCorrectly(file: PsiFile, maxAllowedParserErrors: Int): Boolean = {
    val errorElements = file.depthFirst().filterByType[PsiErrorElement].toList

    if (errorElements.isEmpty) true
    else {
      val (allowedErrors, notAllowedErrors) = errorElements.partition(canIgnoreParserError(_, file))
      notAllowedErrors.isEmpty && allowedErrors.size <= maxAllowedParserErrors
    }
  }

  private def canIgnoreParserError(error: PsiErrorElement, file: PsiFile): Boolean = {
    val errorDescription = error.getErrorDescription
    file match {
      case _: ScalaFile =>
        ScalaAllowedErrors.contains(errorDescription) || isWrongExpressionByAssignment(error)
      case _ =>
        JavaAllowedErrors.contains(errorDescription)
    }
  }

  /**
   * @return true when there is no expression after assignment in a member definition {{{
   *    def foo(x: Int): String = //code goes here
   * }}}
   */
  private def isWrongExpressionByAssignment(error: PsiErrorElement): Boolean =
    ErrorsAfterIncompleteDefinitionWithAssignment.contains(error.getErrorDescription) &&
      error.getParent.is[ScDefinitionWithAssignment] &&
      error.prevSiblingNotWhitespaceComment.exists(_.elementType == ScalaTokenTypes.tASSIGN)

  def createJavaFile(text: String)
                    (implicit project: Project): Option[PsiJavaFile] =
    PsiFileFactory.getInstance(project).createFileFromText(
      "Dummy.java",
      JavaFileType.INSTANCE,
      text
    ).asOptionOf[PsiJavaFile]
}
