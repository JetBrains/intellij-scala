package org.jetbrains.plugins.scala.conversion.copy.plainText

import com.intellij.codeInsight.daemon.JavaErrorMessages
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.{IteratorExt, ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.parser.ErrMsg
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment

/**
  * Created by Kate Ustyuzhanina on 12/27/16.
  *
  * This code assume that Scala files don't have semicolon at the end of line at all and
  * Java files could miss semicolon at the end of line or last curly bracket.
  * Java file without any semicolon could be treated as scala
  */
object PlainTextCopyUtil {
  /**
    * Treat scala file as valid if it doesn't contain ";\n" or one word text or parsed correctly as scala and not parsed correctly as java
    */
  def isValidScalaFile(text: String, project: Project): Boolean = {
    def withLastSemicolon(text: String): Boolean = (!text.contains("\n") && text.contains(";")) || text.contains(";\n")

    def isOneWord(text: String): Boolean = !text.trim.contains(" ")

    if (withLastSemicolon(text) || isJavaClassWithPublic(text, project)) false
    else if (isOneWord(text)) true
    else createScalaFile(text, project).exists(isParsedCorrectly)
  }

  def isJavaClassWithPublic(text: String, project: Project): Boolean =
    createJavaFile(text, project).exists(_.getClasses.exists(_.hasModifierProperty("public")))

  def isValidJavaFile(text: String, project: Project): Boolean = createJavaFile(text, project).exists(isParsedCorrectly)

  def isParsedCorrectly(file: PsiFile): Boolean = {
    val errorElements = file.depthFirst().filterByType[PsiErrorElement].toList

    if (errorElements.isEmpty) true
    else {
      val allowedMessages = file match {
        case _: ScalaFile => scalaAllowedErrors
        case _            => javaAllowedErrors
      }
      val (allowed, notAllowed) =
        errorElements.partition(err => allowedMessages.contains(err.getErrorDescription))

      notAllowed.isEmpty && allowed.size <= 1
    }
  }

  def createScalaFile(text: String, project: Project): Option[ScalaFile] = Some(new ScalaCodeFragment(project, text))

  def createJavaFile(text: String, project: Project): Option[PsiJavaFile] =
    PsiFileFactory.getInstance(project).createFileFromText("Dummy.java", JavaFileType.INSTANCE, text).asOptionOf[PsiJavaFile]

  private val javaAllowedErrors: Set[String] = Set (
    JavaErrorMessages.message("expected.semicolon"), JavaErrorMessages.message("expected.rbrace")
  )

  private val scalaAllowedErrors: Set[String] = Set (
    ErrMsg("rbrace.expected"), ErrMsg("semi.expected")
  )
}
