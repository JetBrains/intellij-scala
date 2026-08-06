package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.icons.Icons.{FIELD_VAL, VAL}
import org.jetbrains.plugins.scala.lang.completion3.base.SameSignatureCallParametersProviderTestBase
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}
import org.junit.Test

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
class Scala3SameSignatureCallParametersProviderTest extends SameSignatureCallParametersProviderTestBase {

  import org.jetbrains.plugins.scala.icons.Icons.PARAMETER
  import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase._

  /// extends TRAIT

  @Test
  def testTraitConstructorCall(): Unit = doCompletionTest(
    fileText =
      s"""trait A(x: Int, y: Int)
         |
         |class B(x: Int, y: Int, z: Int) extends A($CARET)
        """.stripMargin,
    resultText =
      s"""trait A(x: Int, y: Int)
         |
         |class B(x: Int, y: Int, z: Int) extends A(x, y)$CARET
        """.stripMargin,
    item = "x, y"
  )

  @Test
  def testTraitConstructorCallSmart(): Unit = doCompletionTest(
    fileText =
      s"""trait A(x: Int, y: Int)
         |
         |class B(x: Int, y: Int, z: Int) extends A($CARET)
        """.stripMargin,
    resultText =
      s"""trait A(x: Int, y: Int)
         |
         |class B(x: Int, y: Int, z: Int) extends A(x, y)$CARET
      """.stripMargin,
    item = "x, y",
    completionType = CompletionType.SMART
  )

  @Test
  def testTraitConstructorCallLookupElement(): Unit = checkLookupElement(
    fileText =
      s"""trait A(x: Int, y: Int)
         |
         |class B(x: Int, y: Int) extends A($CARET)
        """.stripMargin,
    resultText =
      s"""trait A(x: Int, y: Int)
         |
         |class B(x: Int, y: Int) extends A(x, y)$CARET
        """.stripMargin,
    item = "x, y",
    isSuper = true,
    icons = PARAMETER, PARAMETER
  )

  @Test
  def testAfterParenthesisOnlyInTraitConstructor(): Unit = checkNoCompletion(
    s"""trait A(x: Int, y: Int)
       |
       |class B(x: Int, y: Int) extends A(x, $CARET)
       |""".stripMargin
  )

  @Test
  def testBeforeParenthesisOnlyInTraitConstructor(): Unit = checkNoCompletion(
    s"""trait A(x: Int, y: Int)
       |
       |class B(x: Int, y: Int) extends A($CARET, y)
       |""".stripMargin
  )

  @Test
  def testPositionInTraitConstructor(): Unit = doCompletionTest(
    fileText =
      s"""trait A(x: Int, y: Int, z: Int)
         |
         |class B(y: Int, z: Int) extends A(, $CARET)
         |""".stripMargin,
    resultText =
      s"""trait A(x: Int, y: Int, z: Int)
         |
         |class B(y: Int, z: Int) extends A(, y, z)$CARET
         |""".stripMargin,
    item = "y, z"
  )

  @Test
  def testEmptyTraitConstructor(): Unit = checkNoCompletion(
    s"""trait A()
       |
       |class B(x: Int, y: Int) extends A($CARET)
       |""".stripMargin
  )

  @Test
  def testTooShortTraitConstructor(): Unit = checkNoCompletion(
    s"""trait A(x: Int)
       |
       |class B(x: Int, y: Int) extends A($CARET)
       |""".stripMargin
  )

  @Test
  def testNoNameMatchingTraitConstructor(): Unit = checkNoCompletion(
    fileText =
      s"""trait A(x: Int, y: Int)
         |
         |class B(x: Int, z: Int) extends A($CARET)
         |""".stripMargin
  )

  @Test
  def testNoTypeMatchingTraitConstructor(): Unit = checkNoCompletion(
    fileText =
      s"""trait A(x: Int, y: Int)
         |
         |class B(x: Int, y: String) extends A($CARET)
         |""".stripMargin
  )

  /// new TRAIT

  @Test
  def testTraitConstructorCallAfterNew(): Unit = doCompletionTest(
    fileText =
      s"""object O:
         |  trait A(x: Int, y: Int)
         |
         |  val x: Int = ???
         |  val y: Int = ???
         |
         |  new A($CARET) {}
        """.stripMargin,
    resultText =
      s"""object O:
         |  trait A(x: Int, y: Int)
         |
         |  val x: Int = ???
         |  val y: Int = ???
         |
         |  new A(x, y)$CARET {}
        """.stripMargin,
    item = "x, y"
  )

  @Test
  def testTraitConstructorCallAfterNewSmart(): Unit = doCompletionTest(
    fileText =
      s"""object O:
         |  trait A(x: Int, y: Int)
         |
         |  val x: Int = ???
         |  val y: Int = ???
         |
         |  new A($CARET) {}
        """.stripMargin,
    resultText =
      s"""object O:
         |  trait A(x: Int, y: Int)
         |
         |  val x: Int = ???
         |  val y: Int = ???
         |
         |  new A(x, y)$CARET {}
        """.stripMargin,
    item = "x, y",
    completionType = CompletionType.SMART
  )

  @Test
  def testTraitConstructorCallAfterNewLookupElement(): Unit = checkLookupElement(
    fileText =
      s"""object O:
         |  trait A(x: Int, y: Int)
         |
         |  val x: Int = ???
         |  var y: Int = ???
         |
         |  new A($CARET) {}
        """.stripMargin,
    resultText =
      s"""object O:
         |  trait A(x: Int, y: Int)
         |
         |  val x: Int = ???
         |  var y: Int = ???
         |
         |  new A(x, y)$CARET {}
        """.stripMargin,
    item = "x, y",
    isSuper = true,
    icons = Icons.FIELD_VAL, Icons.FIELD_VAR
  )

  @Test
  def testAfterParenthesisOnlyInTraitConstructorAfterNew(): Unit = checkNoCompletion(
    fileText =
      s"""object O:
         |  trait A(x: Int, y: Int)
         |
         |  val x: Int = ???
         |  val y: Int = ???
         |
         |  new A(x, $CARET) {}
        """.stripMargin
  )

  @Test
  def testBeforeParenthesisOnlyInTraitConstructorAfterNew(): Unit = checkNoCompletion(
    fileText =
      s"""object O:
         |  trait A(x: Int, y: Int)
         |
         |  val x: Int = ???
         |  val y: Int = ???
         |
         |  new A($CARET, y) {}
        """.stripMargin
  )

  @Test
  def testPositionInTraitConstructorAfterNew(): Unit = doCompletionTest(
    fileText =
      s"""object O:
         |  trait A(x: Int, y: Int, z: Int)
         |
         |  val y: Int = ???
         |  val z: Int = ???
         |
         |  new A(, $CARET) {}
        """.stripMargin,
    resultText =
      s"""object O:
         |  trait A(x: Int, y: Int, z: Int)
         |
         |  val y: Int = ???
         |  val z: Int = ???
         |
         |  new A(, y, z)$CARET {}
        """.stripMargin,
    item = "y, z"
  )

  @Test
  def testEmptyTraitConstructorAfterNew(): Unit = checkNoCompletion(
    s"""object O:
       |  trait A()
       |
       |  val x: Int = ???
       |  val y: Int = ???
       |
       |  new A($CARET) {}
       |""".stripMargin
  )

  @Test
  def testTooShortTraitConstructorAfterNew(): Unit = checkNoCompletion(
    s"""object O:
       |  trait A(x: Int)
       |
       |  val x: Int = ???
       |  val y: Int = ???
       |
       |  new A($CARET) {}
       |""".stripMargin
  )

  @Test
  def testNoNameMatchingTraitConstructorAfterNew(): Unit = checkNoCompletion(
    fileText =
      s"""object O:
         |  trait A(x: Int, y: Int)
         |
         |  val x: Int = ???
         |  val z: Int = ???
         |
         |  new A($CARET) {}
         |""".stripMargin
  )

  @Test
  def testNoTypeMatchingTraitConstructorAfterNew(): Unit = checkNoCompletion(
    fileText =
      s"""object O:
         |  trait A(x: Int, y: Int)
         |
         |  val x: Int = ???
         |  val y: String = ???
         |
         |  new A($CARET) {}
         |""".stripMargin
  )

  /// ENUM

  @Test
  def testEnumConstructorCall(): Unit = doCompletionTest(
    fileText =
      s"""enum A(x: Int, y: Int):
         |  case B(x: Int, y: Int, z: Int) extends A($CARET)
        """.stripMargin,
    resultText =
      s"""enum A(x: Int, y: Int):
         |  case B(x: Int, y: Int, z: Int) extends A(x, y)
        """.stripMargin,
    item = "x, y"
  )

  @Test
  def testEnumConstructorCall2(): Unit = doCompletionTest(
    fileText =
      s"""enum A(x: Int, y: Int):
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |  case B(x: Int, y: Int, z: Int) extends A($CARET)
        """.stripMargin,
    resultText =
      s"""enum A(x: Int, y: Int):
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |  case B(x: Int, y: Int, z: Int) extends A(x, y, z)
        """.stripMargin,
    item = "x, y, z"
  )

  @Test
  def testEnumConstructorCall3(): Unit = doCompletionTest(
    fileText =
      s"""enum A(x: Int, y: Int):
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |  case B(x: Int, y: Int, z: Int) extends A($CARET)
        """.stripMargin,
    resultText =
      s"""enum A(x: Int, y: Int):
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |  case B(x: Int, y: Int, z: Int) extends A(x, y)$CARET
        """.stripMargin,
    item = "x, y"
  )

  @Test
  def testEnumConstructorCall3Smart(): Unit = doCompletionTest(
    fileText =
      s"""enum A(x: Int, y: Int):
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |  case B(x: Int, y: Int, z: Int) extends A($CARET)
        """.stripMargin,
    resultText =
      s"""enum A(x: Int, y: Int):
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |  case B(x: Int, y: Int, z: Int) extends A(x, y)$CARET
      """.stripMargin,
    item = "x, y",
    completionType = CompletionType.SMART
  )

  @Test
  def testEnumConstructorCallLookupElement(): Unit = checkLookupElement(
    fileText =
      s"""enum A(x: Int, y: Int):
         |  case B(x: Int, y: Int) extends A($CARET)
        """.stripMargin,
    resultText =
      s"""enum A(x: Int, y: Int):
         |  case B(x: Int, y: Int) extends A(x, y)$CARET
        """.stripMargin,
    item = "x, y",
    isSuper = true,
    icons = FIELD_VAL, FIELD_VAL
  )

  @Test
  def testAfterParenthesisOnlyInEnumConstructor(): Unit = checkNoCompletion(
    s"""enum A(x: Int, y: Int):
       |  case B(x: Int, y: Int) extends A(x, $CARET)
       |""".stripMargin
  )

  @Test
  def testBeforeParenthesisOnlyInEnumConstructor(): Unit = checkNoCompletion(
    s"""enum A(x: Int, y: Int):
       |  case B(x: Int, y: Int) extends A($CARET, y)
       |""".stripMargin
  )

  @Test
  def testPositionInEnumConstructor(): Unit = doCompletionTest(
    fileText =
      s"""enum A(x: Int, y: Int, z: Int):
         |  case B(y: Int, z: Int) extends A(, $CARET)
         |""".stripMargin,
    resultText =
      s"""enum A(x: Int, y: Int, z: Int):
         |  case B(y: Int, z: Int) extends A(, y, z)$CARET
         |""".stripMargin,
    item = "y, z"
  )

  @Test
  def testEmptyEnumConstructor(): Unit = checkNoCompletion(
    s"""enum A():
       |  case B(x: Int, y: Int) extends A($CARET)
       |""".stripMargin
  )

  @Test
  def testTooShortEnumConstructor(): Unit = checkNoCompletion(
    s"""enum A(x: Int):
       |  case B(x: Int, y: Int) extends A($CARET)
       |""".stripMargin
  )

  // should have (x = ???, y = ???) but not (x, y)
  @Test
  def testNoNameMatchingEnumConstructor(): Unit = checkNoCompletionWithoutTailText(
    fileText =
      s"""enum A(x: Int, y: Int):
         |  case B(x: Int, z: Int) extends A($CARET)
         |""".stripMargin,
    lookupString = "x, y"
  )

  // should have (x = ???, y = ???) but not (x, y)
  @Test
  def testNoTypeMatchingEnumConstructor(): Unit = checkNoCompletionWithoutTailText(
    fileText =
      s"""enum A(x: Int, y: Int):
         |  case B(x: Int, y: String) extends A($CARET)
         |""".stripMargin,
    lookupString = "x, y"
  )

  @Test
  def testEnumConstructorAssignment(): Unit = doRawCompletionTest(
    fileText =
      s"""enum A(x: Int, y: Int):
         |  case B extends A($CARET)
         |""".stripMargin,
    resultText =
      s"""enum A(x: Int, y: Int):
         |  case B extends A(x = ???, y = ???)$CARET
         |""".stripMargin,
  ) {
    hasItemText(_, "x, y")(tailText = " = ")
  }

  @Test
  def testPositionInEnumConstructorAssignment(): Unit = doCompletionTest(
    fileText =
      s"""enum A(x: Int, y: Int, z: Int):
         |  case B extends A(, $CARET)
         |""".stripMargin,
    resultText =
      s"""enum A(x: Int, y: Int, z: Int):
         |  case B extends A(, y = ???, z = ???)$CARET
         |""".stripMargin,
    item = "y, z"
  )

  @Test
  def testEnumConstructorAssignmentLookupElement(): Unit = checkLookupElement(
    fileText =
      s"""enum A(x: Int, y: Int):
         |  case B(x: Int) extends A(x$CARET)
         |""".stripMargin,
    resultText =
      s"""enum A(x: Int, y: Int):
         |  case B(x: Int) extends A(x = ???, y = ???)$CARET
         |""".stripMargin,
    item = "x, y",
    isSuper = false,
    icons = PARAMETER, PARAMETER
  )

  /// ENUM CASE

  @Test
  def testEnumCaseConstructorCall(): Unit = doCompletionTest(
    fileText =
      s"""object O:
         |  enum A:
         |    case B(x: Int, y: Int)
         |
         |  val x: Int = ???
         |  val y: Int = ???
         |
         |  A.B($CARET)
        """.stripMargin,
    resultText =
      s"""object O:
         |  enum A:
         |    case B(x: Int, y: Int)
         |
         |  val x: Int = ???
         |  val y: Int = ???
         |
         |  A.B(x, y)$CARET
        """.stripMargin,
    item = "x, y"
  )

  @Test
  def testEnumCaseConstructorCallSmart(): Unit = doCompletionTest(
    fileText =
      s"""object O:
         |  enum A:
         |    case B(x: Int, y: Int)
         |
         |  val x: Int = ???
         |  val y: Int = ???
         |
         |  A.B($CARET)
        """.stripMargin,
    resultText =
      s"""object O:
         |  enum A:
         |    case B(x: Int, y: Int)
         |
         |  val x: Int = ???
         |  val y: Int = ???
         |
         |  A.B(x, y)$CARET
        """.stripMargin,
    item = "x, y",
    completionType = CompletionType.SMART
  )

  @Test
  def testEnumCaseConstructorCallLookupElement(): Unit = checkLookupElement(
    fileText =
      s"""object O:
         |  enum A:
         |    case B(x: Int, y: Int)
         |
         |  val x: Int = ???
         |  var y: Int = ???
         |
         |  A.B($CARET)
        """.stripMargin,
    resultText =
      s"""object O:
         |  enum A:
         |    case B(x: Int, y: Int)
         |
         |  val x: Int = ???
         |  var y: Int = ???
         |
         |  A.B(x, y)$CARET
        """.stripMargin,
    item = "x, y",
    isSuper = false,
    icons = Icons.FIELD_VAL, Icons.FIELD_VAR
  )

  @Test
  def testAfterParenthesisOnlyInEnumCaseConstructor(): Unit = checkNoCompletion(
    fileText =
      s"""object O:
         |  enum A:
         |    case B(x: Int, y: Int)
         |
         |  val x: Int = ???
         |  val y: Int = ???
         |
         |  A.B(x, $CARET)
        """.stripMargin
  )

  @Test
  def testBeforeParenthesisOnlyInEnumCaseConstructor(): Unit = checkNoCompletion(
    fileText =
      s"""object O:
         |  enum A:
         |    case B(x: Int, y: Int)
         |
         |  val x: Int = ???
         |  val y: Int = ???
         |
         |  A.B($CARET, y)
        """.stripMargin
  )

  @Test
  def testPositionInEnumCaseConstructor(): Unit = doCompletionTest(
    fileText =
      s"""object O:
         |  enum A:
         |    case B(x: Int, y: Int, z: Int)
         |
         |  val y: Int = ???
         |  val z: Int = ???
         |
         |  A.B(, $CARET)
        """.stripMargin,
    resultText =
      s"""object O:
         |  enum A:
         |    case B(x: Int, y: Int, z: Int)
         |
         |  val y: Int = ???
         |  val z: Int = ???
         |
         |  A.B(, y, z)$CARET
        """.stripMargin,
    item = "y, z"
  )

  @Test
  def testEmptyEnumCaseConstructor(): Unit = checkNoCompletion(
    s"""object O:
       |  enum A:
       |    case B()
       |
       |  val x: Int = ???
       |  val y: Int = ???
       |
       |  A.B($CARET)
       |""".stripMargin
  )

  @Test
  def testTooShortEnumCaseConstructor(): Unit = checkNoCompletion(
    s"""object O:
       |  enum A:
       |    case B(x: Int)
       |
       |  val x: Int = ???
       |  val y: Int = ???
       |
       |  A.B($CARET)
       |""".stripMargin
  )

  // should have (x = ???, y = ???) but not (x, y)
  @Test
  def testNoNameMatchingEnumCaseConstructor(): Unit = checkNoCompletionWithoutTailText(
    fileText =
      s"""object O:
         |  enum A:
         |    case B(x: Int, y: Int)
         |
         |  val x: Int = ???
         |  val z: Int = ???
         |
         |  A.B($CARET)
         |""".stripMargin,
    lookupString = "x, y"
  )

  // should have (x = ???, y = ???) but not (x, y)
  @Test
  def testNoTypeMatchingConstructorAfterNew(): Unit = checkNoCompletionWithoutTailText(
    fileText =
      s"""object O:
         |  enum A:
         |    case B(x: Int, y: Int)
         |
         |  val x: Int = ???
         |  val y: String = ???
         |
         |  A.B($CARET)
         |""".stripMargin,
    lookupString = "x, y"
  )

  @Test
  def testEnumCaseConstructorAssignment(): Unit = doRawCompletionTest(
    fileText =
      s"""object O:
         |  enum A:
         |    case B()(x: Int, y: Int)
         |
         |  A.B()(x$CARET)
         |""".stripMargin,
    resultText =
      s"""object O:
         |  enum A:
         |    case B()(x: Int, y: Int)
         |
         |  A.B()(x = ???, y = ???)$CARET
         |""".stripMargin,
  ) {
    hasItemText(_, "x, y")(tailText = " = ")
  }

  @Test
  def testPositionInEnumCaseConstructorAssignment(): Unit = doCompletionTest(
    fileText =
      s"""object O:
         |  enum A:
         |    case B(x: Int, y: Int, z: Int)
         |
         |  A.B(, $CARET)
         |""".stripMargin,
    resultText =
      s"""object O:
         |  enum A:
         |    case B(x: Int, y: Int, z: Int)
         |
         |  A.B(, y = ???, z = ???)$CARET
         |""".stripMargin,
    item = "y, z"
  )

  @Test
  def testEnumCaseConstructorAssignmentLookupElement(): Unit = checkLookupElement(
    fileText =
      s"""object O:
         |  enum A:
         |    case B(x: Int, y: Int)
         |
         |  A.B(x$CARET)
         |""".stripMargin,
    resultText =
      s"""object O:
         |  enum A:
         |    case B(x: Int, y: Int)
         |
         |  A.B(x = ???, y = ???)$CARET
         |""".stripMargin,
    item = "x, y",
    isSuper = false,
    icons = PARAMETER, PARAMETER
  )

  /// Universal Apply

  @Test
  def testUniversalApplyConstructorCall(): Unit = checkLookupElement(
    fileText =
      s"""class A(x: Int, y: Int)
         |
         |val x: Int = ???
         |val y: Int = ???
         |
         |A($CARET)
        """.stripMargin,
    resultText =
      s"""class A(x: Int, y: Int)
         |
         |val x: Int = ???
         |val y: Int = ???
         |
         |A(x, y)$CARET
        """.stripMargin,
    item = "x, y",
    isSuper = false,
    icons = VAL, VAL
  )

  @Test
  def testUniversalApplyConstructorCall2(): Unit = checkLookupElement(
    fileText =
      s"""class A(x: Int, y: Int) {
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |}
         |
         |val x: Int = ???
         |val y: Int = ???
         |val z: Int = ???
         |
         |A($CARET)
        """.stripMargin,
    resultText =
      s"""class A(x: Int, y: Int) {
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |}
         |
         |val x: Int = ???
         |val y: Int = ???
         |val z: Int = ???
         |
         |A(x, y, z)$CARET
        """.stripMargin,
    item = "x, y, z",
    isSuper = false,
    icons = VAL, VAL
  )

  @Test
  def testUniversalApplyConstructorCall3(): Unit = checkLookupElement(
    fileText =
      s"""class A(x: Int, y: Int) {
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |}
         |
         |val x: Int = ???
         |val y: Int = ???
         |val z: Int = ???
         |
         |A($CARET)
        """.stripMargin,
    resultText =
      s"""class A(x: Int, y: Int) {
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |}
         |
         |val x: Int = ???
         |val y: Int = ???
         |val z: Int = ???
         |
         |A(x, y)$CARET
        """.stripMargin,
    item = "x, y",
    isSuper = false,
    icons = VAL, VAL
  )

  @Test
  def testUniversalApplyConstructorCall4(): Unit = checkLookupElement(
    fileText =
      s"""class A(x: Int, y: Int)
         |
         |A($CARET)
        """.stripMargin,
    resultText =
      s"""class A(x: Int, y: Int)
         |
         |A(x = ???, y = ???)$CARET
        """.stripMargin,
    item = "x, y",
    isSuper = false,
    icons = PARAMETER, PARAMETER
  )

  @Test
  def testUniversalApplyConstructorCall5(): Unit = checkNoCompletionWithoutTailText(
    fileText =
      s"""class A(x: Int, y: Int)
         |
         |A($CARET)
         |""".stripMargin,
    lookupString = "x, y"
  )

  @Test
  def testPositionInUniversalApply(): Unit = checkLookupElement(
    fileText =
      s"""class A(x: Int, y: Int, z: Int)
         |
         |val y: Int = ???
         |val z: Int = ???
         |
         |A(, $CARET)
        """.stripMargin,
    resultText =
      s"""class A(x: Int, y: Int, z: Int)
         |
         |val y: Int = ???
         |val z: Int = ???
         |
         |A(, y, z)$CARET
        """.stripMargin,
    item = "y, z",
    isSuper = false,
    icons = VAL, VAL
  )

  @Test
  def testPositionInUniversalApplyAssignment(): Unit = checkLookupElement(
    fileText =
      s"""class A(x: Int, y: Int, z: Int)
         |
         |A(, $CARET)
         |""".stripMargin,
    resultText =
      s"""class A(x: Int, y: Int, z: Int)
         |
         |A(, y = ???, z = ???)$CARET
         |""".stripMargin,
    item = "y, z",
    isSuper = false,
    icons = PARAMETER, PARAMETER
  )

  @Test
  def testBeforeParenthesisOnlyInUniversalApply(): Unit = checkNoCompletion(
    fileText =
      s"""class A(x: Int, y: Int)
         |
         |val x: Int = ???
         |val y: Int = ???
         |
         |A($CARET, y)
        """.stripMargin
  )

  @Test
  def testLeadingUsingClause(): Unit = checkLookupElement(
    fileText =
      s"""object A {
         |  def foo(using String)(x: Int, y: Int) = 1
         |  val x = 1
         |  val y = 2
         |  foo($CARET)
         |}
         |""".stripMargin,
    resultText =
      s"""object A {
         |  def foo(using String)(x: Int, y: Int) = 1
         |  val x = 1
         |  val y = 2
         |  foo(x, y)
         |}
         |""".stripMargin,
    item = "x, y",
    isSuper = false,
    icons = FIELD_VAL, FIELD_VAL
  )
}
