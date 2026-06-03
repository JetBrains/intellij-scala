package org.jetbrains.plugins.scala.codeInsight.intention.types

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaBundle, ScalaVersion}

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
abstract class ConvertImplicitBoundsToImplicitParameterTestBase extends ScalaIntentionTestBase {

  override def familyName: String = ScalaBundle.message("family.name.convert.implicit.bounds")

  protected def doCommonTest(text: String, after: String): Unit = {
    doTest(text, after)
  }

  def test_one_bound(): Unit = doCommonTest(
    s"def test[${CARET}T: A]: Int = 0",
    s"def test[T](implicit a: A[T]): Int = 0"
  )

  def test_one_bound_with_empty_clause(): Unit = doCommonTest(
    s"def test[${CARET}T: A](): Unit = ()",
    s"def test[T]()(implicit a: A[T]): Unit = ()"
  )

  def test_two_bound(): Unit = doCommonTest(
    s"def test[${CARET}T1: A, T2: A](): Unit = ()",
    s"def test[T1, T2]()(implicit aT1: A[T1], aT2: A[T2]): Unit = ()"
  )

  def test_class(): Unit = doCommonTest(
    s"class Test[${CARET}T: A]",
    s"class Test[T](implicit a: A[T])"
  )
}

class ConvertImplicitBoundsToImplicitParameterTest_Scala2 extends ConvertImplicitBoundsToImplicitParameterTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version <= LatestScalaVersions.Scala_2_13

  def test_already_existing_clause(): Unit = doTest(
    s"def test[${CARET}T: A, TT: B](implicit x: X): Unit = ()",
    s"def test[T, TT](implicit x: X, a: A[T], b: B[TT]): Unit = ()"
  )
}

class ConvertImplicitBoundsToImplicitParameterTest_Scala3 extends ConvertImplicitBoundsToImplicitParameterTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version > LatestScalaVersions.Scala_2_13

  override def doCommonTest(text: String, expected: String): Unit = {
    val textWithUsing = text.replace("implicit", "using")
    val expectedWithUsing = expected.replace("implicit", "using")
    super.doCommonTest(textWithUsing, expectedWithUsing)
  }

  def test_already_existing_clause_using(): Unit = doTest(
    s"def test[${CARET}T: A, TT: B](using x: X): Unit = ()",
    "def test[T, TT](using x: X, a: A[T], b: B[TT]): Unit = ()"
  )

  def test_already_existing_clause_implicit(): Unit = doTest(
    s"def test[${CARET}T: A, TT: B](implicit x: X): Unit = ()",
    "def test[T, TT](using x: X, a: A[T], b: B[TT]): Unit = ()"
  )
}

class ConvertImplicitBoundsToImplicitParameterTest_Scala3_6 extends ConvertImplicitBoundsToImplicitParameterTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_6

  override def doCommonTest(text: String, expected: String): Unit = {
    val textWithUsing = text.replace("implicit", "using")
    val expectedWithUsing = expected.replace("implicit", "using")
    super.doCommonTest(textWithUsing, expectedWithUsing)
  }

  def test_already_existing_clause_using(): Unit = doTest(
    s"def test[${CARET}T: A, TT: B](using x: X): Unit = ()",
    "def test[T, TT](using x: X, a: A[T], b: B[TT]): Unit = ()"
  )

  def test_already_existing_clause_implicit(): Unit = doTest(
    s"def test[${CARET}T: A, TT: B](implicit x: X): Unit = ()",
    "def test[T, TT](using x: X, a: A[T], b: B[TT]): Unit = ()"
  )

  def testClauseReference(): Unit = doTest(
    s"def foo[${CARET}A : Parser as p](in: p.In): p.Out = ???",
    s"def foo[A](using p: Parser[A])(in: p.In): p.Out = ???",
  )

  def testClauseReferenceMiddle(): Unit = doTest(
    s"def foo[${CARET}A : Parser as p](x: NoRefHere)(in: p.In): p.Out = ???",
    s"def foo[A](x: NoRefHere)(using p: Parser[A])(in: p.In): p.Out = ???",
  )
}
