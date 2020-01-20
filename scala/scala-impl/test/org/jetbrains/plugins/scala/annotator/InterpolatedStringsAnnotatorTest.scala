package org.jetbrains.plugins.scala
package annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.junit.Assert._

/**
 * User: Dmitry Naydanov
 * Date: 7/3/12
 */
class InterpolatedStringsAnnotatorTest extends base.ScalaLightCodeInsightFixtureTestAdapter {

  import InterpolatedStringsAnnotatorTest._

  private def collectAnnotatorMessages(text: String) = {
    val file = configureFromFileText(text).asInstanceOf[ScalaFile]

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    ScalaAnnotator.forProject(getProject).annotate(file.getLastChild)
    mock.annotations
  }

  private def emptyMessages(text: String): Unit = {
    val messages = collectAnnotatorMessages(text).isEmpty
    assertTrue(messages)
  }

  private def messageExists(text: String, message: String) {
    val annotatorMessages = collectAnnotatorMessages(text)
    if (!annotatorMessages.exists(_.toString == message)) {
      assertFalse("annotator messages is empty", annotatorMessages.isEmpty)
      assertEquals(message, annotatorMessages.head)
    }
  }

  def testCorrectInt(): Unit = {
    emptyMessages(Header + "a\"blah blah $i1 $i2 ${1 + 2 + 3} blah\"")
  }

  def testCorrectAny(): Unit = {
    emptyMessages(Header + "b\"blah blah ${1} $s1 ${s2 + c1}blah blah\"")
  }

  def testCorrectStrings(): Unit = {
    emptyMessages(Header + "c\"blah blah $s1 ${s2}\"")
  }

  def testMultiResolve(): Unit = {
    messageExists(Header + "_\"blah $s1 blah $s2 blah\"", "Error(_,Value '_' is not a member of StringContext)")
  }

  def testMultipleResolve(): Unit = {
    messageExists(Header + "c\"blah blah $i1 $i2\"", "Error(i1,Type mismatch, expected: String, actual: Int)")
  }

  def testMultipleResolve2(): Unit = {
    messageExists(Header + "c\"blah $i1 blah $s1 $i2\"", "Error(i2,Too many arguments for method c(String, String))")
  }
}

object InterpolatedStringsAnnotatorTest {

  @Language(value = "Scala")
  private val Header =
    """|class ExtendedContext {
       |  def a(ints: Int*) = ints.length
       |  def b(args: Any*) = args.length
       |  def c(s1: String, s2: String) = s1 + s2
       |  def d(i1: Int, s1: String) = i1 + s1.length
       |  def d(i1: Int, i2: Int) = i1 + i2
       |}
       |
       |implicit def extendStrContext(ctx: StringContext) = new ExtendedContext
       |
       |val i1 = 1; val i2 = 2; val s1 = "string1"; val s2 = "string2"; val c1 = 'c'
       |""".stripMargin
}
