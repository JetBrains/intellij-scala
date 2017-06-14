package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.testFramework.EditorTestUtil
import junit.framework.ComparisonFailure
import org.junit.Assert.assertTrue

/**
  * @author adkozlov
  */
class ScalaConversionCompletionTest extends AbstractConversionCompletionTest {

  import EditorTestUtil.{CARET_TAG => CARET}

  def testJavaConverters(): Unit = doCompletionTest(
    fileText =
      s"""
         |val ja = new java.util.ArrayList[Int]
         |ja.asSc$CARET
      """.stripMargin,
    resultText =
      s"""
         |val ja = new java.util.ArrayList[Int]
         |ja.asScala$CARET
      """.stripMargin,
    item = "asScala",
    time = 2
  )

  override protected val convertersNames: Seq[String] = Seq(
    "asScalaBufferConverter",
    "collectionAsScalaIterableConverter",
    "iterableAsScalaIterableConverter"
  ).map(shortName => s"scala.collection.JavaConverters.$shortName")
}

abstract class AbstractConversionCompletionTest extends ScalaCodeInsightTestBase {

  protected val convertersNames: Seq[String]

  override protected def checkResultByText(expectedFileText: String, ignoreTrailingSpaces: Boolean): Unit = {
    def runCheck(fileText: String) = try {
      super.checkResultByText(fileText, ignoreTrailingSpaces)
      true
    } catch {
      case _: ComparisonFailure => false
    }

    val expected = convertersNames.map { qualifiedName =>
      s"""
         |import $qualifiedName
         |$expectedFileText
       """.stripMargin
    }
    assertTrue(expected.exists(runCheck))
  }
}