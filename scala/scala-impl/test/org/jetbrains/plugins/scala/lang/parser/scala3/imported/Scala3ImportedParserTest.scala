package org.jetbrains.plugins.scala.lang.parser.scala3.imported

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.impl.DebugUtil.psiToString
import com.intellij.psi.{PsiElement, PsiErrorElement, PsiFile}
import org.jetbrains.plugins.scala.Scala3Language
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.scala3.imported.Scala3ImportedParserTest.rangesDirectory
import org.junit.Assert._
import org.junit.runner.RunWith
import org.junit.runners.AllTests

import java.nio.file.Paths

@RunWith(classOf[AllTests])
abstract class Scala3ImportedParserTestBase(dir: String) extends ScalaFileSetTestCase(dir) {
  override protected def getLanguage: Language = Scala3Language.INSTANCE

  protected def findErrorElements(fileText: String, project: Project): (Seq[PsiErrorElement], PsiFile) = {
    val lightFile = createLightFile(fileText, project)
    val errors = lightFile
      .elements
      .collect { case error: PsiErrorElement => error }
      .filterNot(_.parentsInFile.exists(_.isComment))
      .toSeq

    errors -> lightFile
  }

  protected override def transform(testName: String, fileText: String, project: Project): String = {
    val (errors, lightFile) = findErrorElements(fileText, project)
    // TODO: also test that there no errors from annotator (type-agnostic)
    //  (we need to list all such annotators)
    //  e.g. see org.jetbrains.plugins.scala.annotator.modifiers.ModifierChecker
    //  It can also detect that the code was parsed incorrectly
    val hasErrorElements = errors.nonEmpty

    lazy val expected = psiToString(lightFile, false).replace(": " + lightFile.name, "")
    if (hasErrorElements != shouldHaveErrors) {
      println(fileText)
      println("-------")
      println(expected)
    }

    val msg = s"Found following errors: " + errors.mkString(", ")

    // check ranges
    lazy val interlaced = findInterlacedRanges(lightFile, testName)

    if (shouldHaveErrors) {
      assert(hasErrorElements || interlaced.nonEmpty, "Expected to find error elements or interlaced ranges, but found none.")
    } else {
      assertFalse(msg, hasErrorElements)
      assert(interlaced.isEmpty, "Following elements have conflicting ranges:\n" + {
        val maxStringLen = 50
        def clip(s: String): String = {
          if (s.length > maxStringLen) s.take(maxStringLen / 2 - 1) + ".." + s.takeRight(maxStringLen / 2 - 1)
          else s
        }.replace("\n", "\\n")

        interlaced
          .map { case (e, (range, name)) =>
            val rangeText = textInRange(lightFile, range)
            s"$e <-> $name:\n   ${trimRanges(lightFile, e.getTextRange)}[${clip(e.getText)}]\nvs $range[${clip(rangeText)}]"
          }
          .mkString("\n")
      })
    }

    // TODO: return real psi tree instead of empty one
    ""
  }

  def isStringPart(e: PsiElement): Boolean =
    ScalaTokenTypes.STRING_LITERAL_TOKEN_SET.contains(e.elementType)

  def findInterlacedRanges(root: PsiElement, testName: String): Seq[(PsiElement, (TextRange, String))] = {
    val ranges = RangeMap.fromFileOrEmpty(Paths.get(getTestDataPath, rangesDirectory, testName + ".ranges"))
    //val ignoredNames = Set("Import", "Export")
    for {
      e <- root.depthFirst()
      range = e.getTextRange
      interlaced = ranges.interlaced(if (isStringPart(e)) range else trimRanges(root, range)).toMap
      interlace <- interlaced
      //if !ignoredNames(interlace._2)
      if !isInterlaceBecauseOfSemicolon(range, interlace)
    } yield e -> interlace
  }.toSeq

  def textInRange(root: PsiElement, range: TextRange): String =
    root.getText.substring(range.getStartOffset, range.getEndOffset)

  def trimRanges(root: PsiElement, range: TextRange): TextRange = {
    val text = textInRange(root, range)
    val leadingWs = text.iterator.takeWhile(_.isWhitespace).size
    if (leadingWs == text.length) range
    else {
      val trailingWs = text.reverseIterator.takeWhile(_.isWhitespace).size
      new TextRange(range.getStartOffset + leadingWs, range.getEndOffset - trailingWs)
    }
  }

  // In the Scala3 Compiler semicolons might be attached to identifiers,
  // leading to interlaced ranges
  // Example (:
  //   Scala3:  left +[right ;]
  //   We:     [left + right];
  def isInterlaceBecauseOfSemicolon(intellijRange: TextRange, interlace: (TextRange, String)): Boolean = {
    val (scala3Range, text) = interlace

    // adjust ranges
    val newScala3Range = scala3Range.shiftEnd(-text.reverseIterator.count(c => c.isWhitespace || c == ';'))

    !newScala3Range.isEmpty && (scala3Range interlaces intellijRange)
  }

  protected def shouldHaveErrors: Boolean

  override protected def shouldPass = true
}


class Scala3ImportedParserTest extends Scala3ImportedParserTestBase(Scala3ImportedParserTest.directory) {
  override protected def shouldHaveErrors: Boolean = false
}

object Scala3ImportedParserTest {
  val directory = "/parser/scala3Import/success"
  val rangesDirectory = "/parser/scala3Import/ranges"
  def suite = new Scala3ImportedParserTest()
}


/**
 * If this tests fails because you fixed parser stuff,
 * run [[Scala3ImportedParserTest_Move_Fixed_Tests]].
 */
class Scala3ImportedParserTest_Fail extends Scala3ImportedParserTestBase(Scala3ImportedParserTest_Fail.directory) {
  override protected def shouldHaveErrors: Boolean = true
}

object Scala3ImportedParserTest_Fail {
  val directory = "/parser/scala3Import/fail"
  def suite = new Scala3ImportedParserTest_Fail()
}
