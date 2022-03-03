package org.jetbrains.plugins.scala.lang.completion3

import junit.framework.ComparisonFailure
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.Assert.fail
import org.junit.runner.RunWith

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest
))
@RunWith(classOf[MultipleScalaVersionsRunner])
class ScalaConversionCompletionTest extends AbstractConversionCompletionTest {

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

  override protected def convertersNames: Seq[String] = {
    val common = Seq(
      "scala.collection.JavaConverters.asScalaBufferConverter",
      "scala.collection.JavaConverters.collectionAsScalaIterableConverter",
      "scala.collection.JavaConverters.iterableAsScalaIterableConverter",
    )
    if (version >= ScalaVersion.Latest.Scala_2_13.withMinor(0))
      common :+ "scala.jdk.CollectionConverters.CollectionHasAsScala"
    else
      common
  }
}

abstract class AbstractConversionCompletionTest extends ScalaCodeInsightTestBase {

  protected def convertersNames: Seq[String]

  override protected def checkResultByText(expectedFileText: String, ignoreTrailingSpaces: Boolean): Unit = {
    def runCheck(fileText: String): Either[ComparisonFailure, Unit] = try {
      super.checkResultByText(fileText, ignoreTrailingSpaces)
      Right(())
    } catch {
      case cf: ComparisonFailure =>
        Left(cf)
    }

    val expectedTextCandidates: Seq[String] = convertersNames.map { qualifiedName =>
      s"""
         |import $qualifiedName
         |$expectedFileText
       """.stripMargin
    }
    val lazyCheckResults: LazyList[Either[ComparisonFailure, Unit]] = expectedTextCandidates.toList.to(LazyList).map(runCheck)
    if (lazyCheckResults.exists(_.isRight)) {
      //ok, success
    }
    else {
      val failure = lazyCheckResults.head.left.getOrElse(fail("expected at least single comparison failure").asInstanceOf[Nothing])
      fail(
        s"""Actual text doesn't match any of the expected, converters candidates:
           |${convertersNames.mkString("\n")}
           |(NOTE: Using single expected result in diff)
           |${failure.getMessage}""".stripMargin
      )
    }
  }
}