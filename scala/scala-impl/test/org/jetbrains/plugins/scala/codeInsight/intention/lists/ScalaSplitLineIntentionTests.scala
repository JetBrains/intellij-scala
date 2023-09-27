package org.jetbrains.plugins.scala.codeInsight.intention.lists

import com.intellij.codeInsight.CodeInsightBundle

sealed trait ScalaSplitLineIntentionTestBase {
  self: ScalaSplitJoinLineIntentionTestBase =>
  override final val familyName = CodeInsightBundle.message("intention.family.name.split.values")
  override protected final val testType = SplitJoinTestType.Split
}

final class ScalaSplitArgumentsIntentionTest
  extends ScalaSplitJoinArgumentsIntentionTestBase
    with ScalaSplitLineIntentionTestBase {
  override protected val intentionText: String = "Put arguments on separate lines"

  def testIntentionAvailableEverywhereInside(): Unit = {
    getFixture.addFileToProject("a.scala", "def foo(x: Int, y: Int): Unit = ???")

    checkIntentionIsNotAvailable(s"${CARET}foo(1 + 2 + 3, 4 + 5 + 6)")

    checkIntentionIsAvailable(s"foo$CARET(1 + 2 + 3, 4 + 5 + 6)")
    checkIntentionIsAvailable(s"foo(${CARET}1 + 2 + 3, 4 + 5 + 6)")
    checkIntentionIsAvailable(s"foo(1 + ${CARET}2 + 3, 4 + 5 + 6)")
  }
}

final class ScalaSplitParametersIntentionTest
  extends ScalaSplitJoinParametersIntentionTestBase
    with ScalaSplitLineIntentionTestBase {
  override protected val intentionText: String = "Put parameters on separate lines"

  def testIntentionAvailableEverywhereInside(): Unit = {
    checkIntentionIsNotAvailable(s"def ${CARET}foo(p1: String, p2: Option[Option[String]]): Unit = {}")

    checkIntentionIsAvailable(s"def foo$CARET(p1: String, p2: Option[Option[String]]): Unit = {}")
    checkIntentionIsAvailable(s"def foo(${CARET}p1: String, p2: Option[Option[String]]): Unit = {}")
    checkIntentionIsAvailable(s"def foo(p1: ${CARET}String, p2: Option[Option[String]]): Unit = {}")
    checkIntentionIsAvailable(s"def foo(p1: String, p2: Option[Option[${CARET}String]]): Unit = {}")
  }
}

final class ScalaSplitTupleTypesIntentionTest
  extends ScalaSplitJoinTupleTypesIntentionTestBase
    with ScalaSplitLineIntentionTestBase {
  override protected val intentionText: String = "Put tuple type elements on separate lines"

  def testIntentionAvailableEverywhereInside(): Unit = {
    checkIntentionIsNotAvailable(s"val x$CARET: (Int, String) = ???")

    checkIntentionIsAvailable(s"val x: $CARET(Int, String) = ???")
    checkIntentionIsAvailable(s"val x: (${CARET}Int, String) = ???")
    checkIntentionIsAvailable(s"val x: (In${CARET}t, String) = ???")
  }
}

final class ScalaSplitTuplesIntentionTest
  extends ScalaSplitJoinTuplesIntentionTestBase
    with ScalaSplitLineIntentionTestBase {
  override protected val intentionText: String = "Put tuple elements on separate lines"

  def testIntentionAvailableEverywhereInside(): Unit = {
    checkIntentionIsNotAvailable(s"val x $CARET= (1 + 2 + 3, 4 + 5 + 6")

    checkIntentionIsAvailable(s"val x = $CARET(1 + 2 + 3, 4 + 5 + 6")
    checkIntentionIsAvailable(s"val x = (${CARET}1 + 2 + 3, 4 + 5 + 6")
    checkIntentionIsAvailable(s"val x = (1 + ${CARET}2 + 3, 4 + 5 + 6")
  }
}

final class ScalaSplitTypeArgumentsIntentionTest
  extends ScalaSplitJoinTypeArgumentsIntentionTestBase
    with ScalaSplitLineIntentionTestBase {
  override protected val intentionText: String = "Put type arguments on separate lines"

  def testIntentionAvailableEverywhereInside(): Unit = {
    getFixture.addFileToProject("a.scala", "def foo[A, B]: Unit = {}")

    checkIntentionIsNotAvailable(s"${CARET}foo[String, Tuple2[Option[Int], Option[Int]]]")

    checkIntentionIsAvailable(s"foo$CARET[String, Tuple2[Option[Int], Option[Int]]]")
    checkIntentionIsAvailable(s"foo[${CARET}String, Tuple2[Option[Int], Option[Int]]]")
    checkIntentionIsAvailable(s"foo[String, ${CARET}Tuple2[Option[Int], Option[Int]]]")
    checkIntentionIsAvailable(s"foo[String, Tuple2[Option[Int], ${CARET}Option[Int]]]")
  }
}

final class ScalaSplitTypeParametersIntentionTest
  extends ScalaSplitJoinTypeParametersIntentionTestBase
    with ScalaSplitLineIntentionTestBase {
  override protected val intentionText: String = "Put type parameters on separate lines"

  def testIntentionAvailableEverywhereInside(): Unit = {
    checkIntentionIsNotAvailable(s"def ${CARET}foo[A, B <: CharSequence]: Unit = {}")

    checkIntentionIsAvailable(s"def foo$CARET[A, B <: CharSequence]: Unit = {}")
    checkIntentionIsAvailable(s"def foo[${CARET}A, B <: CharSequence]: Unit = {}")
    checkIntentionIsAvailable(s"def foo[A, ${CARET}B <: CharSequence]: Unit = {}")
    checkIntentionIsAvailable(s"def foo[A, B <:$CARET CharSequence]: Unit = {}")
  }
}
