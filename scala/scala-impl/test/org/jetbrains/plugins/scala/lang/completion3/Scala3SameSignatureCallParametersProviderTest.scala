package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.completion3.base.SameSignatureCallParametersProviderTestBase

class Scala3SameSignatureCallParametersProviderTest extends SameSignatureCallParametersProviderTestBase {

  import org.jetbrains.plugins.scala.icons.Icons.PARAMETER
  import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase._

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0

  /// extends TRAIT

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

  def testAfterParenthesisOnlyInTraitConstructor(): Unit = checkNoCompletion(
    s"""trait A(x: Int, y: Int)
       |
       |class B(x: Int, y: Int) extends A(x, $CARET)
       |""".stripMargin
  )

  def testBeforeParenthesisOnlyInTraitConstructor(): Unit = checkNoCompletion(
    s"""trait A(x: Int, y: Int)
       |
       |class B(x: Int, y: Int) extends A($CARET, y)
       |""".stripMargin
  )

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

  def testEmptyTraitConstructor(): Unit = checkNoCompletion(
    s"""trait A()
       |
       |class B(x: Int, y: Int) extends A($CARET)
       |""".stripMargin
  )

  def testTooShortTraitConstructor(): Unit = checkNoCompletion(
    s"""trait A(x: Int)
       |
       |class B(x: Int, y: Int) extends A($CARET)
       |""".stripMargin
  )

  def testNoNameMatchingTraitConstructor(): Unit = checkNoCompletion(
    fileText =
      s"""trait A(x: Int, y: Int)
         |
         |class B(x: Int, z: Int) extends A($CARET)
         |""".stripMargin
  )

  def testNoTypeMatchingTraitConstructor(): Unit = checkNoCompletion(
    fileText =
      s"""trait A(x: Int, y: Int)
         |
         |class B(x: Int, y: String) extends A($CARET)
         |""".stripMargin
  )

  /// new TRAIT

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
    icons = PARAMETER, PARAMETER
  )

  def testAfterParenthesisOnlyInEnumConstructor(): Unit = checkNoCompletion(
    s"""enum A(x: Int, y: Int):
       |  case B(x: Int, y: Int) extends A(x, $CARET)
       |""".stripMargin
  )

  def testBeforeParenthesisOnlyInEnumConstructor(): Unit = checkNoCompletion(
    s"""enum A(x: Int, y: Int):
       |  case B(x: Int, y: Int) extends A($CARET, y)
       |""".stripMargin
  )

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

  def testEmptyEnumConstructor(): Unit = checkNoCompletion(
    s"""enum A():
       |  case B(x: Int, y: Int) extends A($CARET)
       |""".stripMargin
  )

  def testTooShortEnumConstructor(): Unit = checkNoCompletion(
    s"""enum A(x: Int):
       |  case B(x: Int, y: Int) extends A($CARET)
       |""".stripMargin
  )

  // should have (x = ???, y = ???) but not (x, y)
  def testNoNameMatchingEnumConstructor(): Unit = checkNoCompletionWithoutTailText(
    fileText =
      s"""enum A(x: Int, y: Int):
         |  case B(x: Int, z: Int) extends A($CARET)
         |""".stripMargin,
    lookupString = "x, y"
  )

  // should have (x = ???, y = ???) but not (x, y)
  def testNoTypeMatchingEnumConstructor(): Unit = checkNoCompletionWithoutTailText(
    fileText =
      s"""enum A(x: Int, y: Int):
         |  case B(x: Int, y: String) extends A($CARET)
         |""".stripMargin,
    lookupString = "x, y"
  )

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

}
