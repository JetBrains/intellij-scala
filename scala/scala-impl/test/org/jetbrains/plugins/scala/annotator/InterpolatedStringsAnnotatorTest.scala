package org.jetbrains.plugins.scala.annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.junit.Assert

/**
 * User: Dmitry Naydanov
 * Date: 7/3/12
 */

class InterpolatedStringsAnnotatorTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  private def collectAnnotatorMessages(text: String) = {
    configureFromFileTextAdapter("dummy.scala", text)
    val mock = new AnnotatorHolderMock(getFileAdapter)

    ScalaAnnotator.forProject(getProjectAdapter).annotate(getFileAdapter.asInstanceOf[ScalaFile].getLastChild, mock)
    mock.annotations
  }
  
  private def emptyMessages(text: String) {
    assert(collectAnnotatorMessages(text).isEmpty)
  }

  private def messageExists(text: String, message: String) {
    val annotatorMessages = collectAnnotatorMessages(text)
    if (!annotatorMessages.exists(_.toString == message)) {
      Assert.assertTrue("annotator messages is empty", annotatorMessages.nonEmpty)
      Assert.assertEquals(message, annotatorMessages.head)
    }
  }

  @Language(value = "Scala")
  private val header =
    """
       class ExtendedContext {
         def a(ints: Int*) = ints.length
         def b(args: Any*) = args.length
         def c(s1: String, s2: String) = s1 + s2
         def d(i1: Int, s1: String) = i1 + s1.length
         def d(i1: Int, i2: Int) = i1 + i2
       }

       implicit def extendStrContext(ctx: StringContext) = new ExtendedContext

       val i1 = 1; val i2 = 2; val s1 = "string1"; val s2 = "string2"; val c1 = 'c'
       
    """.replace("\r", "")
  
  def testCorrectInt() {
    emptyMessages(header + "a\"blah blah $i1 $i2 ${1 + 2 + 3} blah\"")
  }
  
  def testCorrectAny() {
    emptyMessages(header + "b\"blah blah ${1} $s1 ${s2 + c1}blah blah\"")
  }
  
  def testCorrextStrings() {
    emptyMessages(header + "c\"blah blah $s1 ${s2}\"")
  }
  
  def testMultiResolve() {
    messageExists(header + "d\"blah $s1 blah $s2 blah\"", "Error(d,Value 'd' is not a member of StringContext)")
  }
  
  def testMultipleResolve() {
    messageExists(header + "c\"blah blah $i1 $i2\"", "Error(i1,Type mismatch, expected: String, actual: Int)")
  }
  
  def testMultipleResolve2() {
    messageExists(header + "c\"blah $i1 blah $s1 $i2\"", "Error(i2,Too many arguments for method c(String, String))")
  }
}
