package org.jetbrains.plugins.scala.annotator.modifiers

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.{PsiElement, PsiErrorElement}
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, Error, Message}
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

import java.io.File

class GeneratedModifierTest extends SimpleTestCase {

  def test_all(): Unit = {
    val text = {
      val ioFile: File = new File(GeneratedModifierTestGenerator.generatedModifiersTestFilePath)
      FileUtil.loadFile(ioFile, CharsetToolkit.UTF8)
    }

    val file = text.parse
    val allStmts = file.children.collect { case t: ScTypeDefinition => t }.toSeq
    assert(allStmts.size > 4000)

    for (stmt <- allStmts if !shouldSkip(stmt.getText); comment <- stmt.allComments.headOption) {
      val shouldSucceed = comment.getText.substring(3).toBoolean
      val messages = modifierMessages(stmt).filterByType[Error]

      assert (
        shouldSucceed == messages.isEmpty,
        if (!shouldSucceed) s"Expected errors, but found non in: ${stmt.getText}"
        else s"Expected no errors but found the following problems in ${stmt.getText}\n${messages.mkString("\n")}"
      )
    }
  }

  def shouldSkip(text: String): Boolean = {
    val line = text.linesIterator.lastOption.get.trim

    // skip if top level is annotated with override
    // we give an error correctly, but the generated tests do not because of overrides being checked after typer phase
    line.indexOf("override").toOption.filter(_ >= 0).exists(_ < line.indexOf('{'))
  }



  private def modifierMessages(element: PsiElement): Seq[Message] = {
    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(element.getContainingFile)
    element.depthFirst().foreach {
      //case _: ScDeclaration => return Seq(Error("", ""))
      case e: PsiErrorElement => return Seq(Error(e.getText, "Parse error!"))
      case modifierList: ScModifierList => ModifierChecker.checkModifiers(modifierList)
      case _ =>
    }
    mock.annotations
  }

}
