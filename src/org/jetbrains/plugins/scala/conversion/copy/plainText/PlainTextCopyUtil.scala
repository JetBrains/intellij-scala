package org.jetbrains.plugins.scala.conversion.copy.plainText

import com.intellij.codeInsight.daemon.JavaErrorMessages
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi._
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaCodeFragment
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.parser.ErrMsg
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import scala.collection.mutable

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
    lazy val javaPossibleErrors: mutable.HashSet[String] = mutable.HashSet[String](
      JavaErrorMessages.message("expected.semicolon"), JavaErrorMessages.message("expected.rbrace"))

    lazy val scalaPossibleErrors: mutable.HashSet[String] = mutable.HashSet[String](
      ErrMsg("rbrace.expected"), ErrMsg("semi.expected"))

    def handleFile(errors: mutable.HashSet[String]) = {
      !file.depthFirst().exists {
        case err: PsiErrorElement if errors.contains(err.getErrorDescription) => false
        case _: PsiErrorElement => true
        case _ => false
      }
    }

    file match {
      case _: ScalaFile => handleFile(scalaPossibleErrors)
      case _ => handleFile(javaPossibleErrors)
    }
  }

  def createScalaFile(text: String, project: Project): Option[ScalaFile] = Some(new ScalaCodeFragment(project, text))

  def createJavaFile(text: String, project: Project): Option[PsiJavaFile] =
    PsiFileFactory.getInstance(project).createFileFromText("Dummy.java", JavaFileType.INSTANCE, text).asOptionOf[PsiJavaFile]
}
