package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}

class ScalaSuperParametersTest extends SameSignatureCallParametersProviderTestBase {

  import ScalaCodeInsightTestBase._
  import icons.Icons.{PARAMETER, PATTERN_VAL}

  def testConstructorCall(): Unit = doCompletionTest(
    fileText =
      s"""class A(x: Int, y: Int) {
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |}
         |
         |class B(x: Int, y: Int, z: Int) extends A($CARET)
        """.stripMargin,
    resultText =
      s"""class A(x: Int, y: Int) {
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |}
         |
         |class B(x: Int, y: Int, z: Int) extends A(x, y, z)$CARET
        """.stripMargin,
    item = "x, y, z"
  )

  def testConstructorCall2(): Unit = doCompletionTest(
    fileText =
      s"""class A(x: Int, y: Int) {
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |}
         |
         |class B(x: Int, y: Int, z: Int) extends A($CARET)
        """.stripMargin,
    resultText =
      s"""class A(x: Int, y: Int) {
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |}
         |
         |class B(x: Int, y: Int, z: Int) extends A(x, y)$CARET
        """.stripMargin,
    item = "x, y"
  )

  def testConstructorCall2Smart(): Unit = doCompletionTest(
    fileText =
      s"""class A(x: Int, y: Int) {
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |}
         |
         |class B(x: Int, y: Int, z: Int) extends A($CARET)
        """.stripMargin,
    resultText =
      s"""class A(x: Int, y: Int) {
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |}
         |
         |class B(x: Int, y: Int, z: Int) extends A(x, y)$CARET
      """.stripMargin,
    item = "x, y",
    completionType = CompletionType.SMART
  )

  def testConstructorCallLookupElement(): Unit = checkLookupElement(
    fileText =
      s"""class A(x: Int, y: Int)
         |
         |class B(x: Int, y: Int) extends A($CARET)
        """.stripMargin,
    resultText =
      s"""class A(x: Int, y: Int)
         |
         |class B(x: Int, y: Int) extends A(x, y)$CARET
        """.stripMargin,
    item = "x, y",
    isSuper = true,
    icons = PARAMETER, PARAMETER
  )

  def testAfterParenthesisOnlyInConstructor(): Unit = checkNoCompletion(
    s"""class A(x: Int, y: Int)
       |
       |class B(x: Int, y: Int) extends A(x, $CARET)
       |""".stripMargin
  )

  def testBeforeParenthesisOnlyInConstructor(): Unit = checkNoCompletion(
    s"""class A(x: Int, y: Int)
       |
       |class B(x: Int, y: Int) extends A($CARET, y)
       |""".stripMargin
  )

  def testPositionInConstructor(): Unit = doCompletionTest(
    fileText =
      s"""class A(x: Int, y: Int, z: Int)
         |
         |class B(y: Int, z: Int) extends A(, $CARET)
         |""".stripMargin,
    resultText =
      s"""class A(x: Int, y: Int, z: Int)
         |
         |class B(y: Int, z: Int) extends A(, y, z)$CARET
         |""".stripMargin,
    item = "y, z"
  )

  def testEmptyConstructor(): Unit = checkNoCompletion(
    s"""class A()
       |
       |class B(x: Int, y: Int) extends A($CARET)
       |""".stripMargin
  )

  def testTooShortConstructor(): Unit = checkNoCompletion(
    s"""class A(x: Int)
       |
       |class B(x: Int, y: Int) extends A($CARET)
       |""".stripMargin
  )

  // should have (x = ???, y = ???) but not (x, y)
  def testNoNameMatchingConstructor(): Unit = checkNoCompletionWithoutTailText(
    fileText =
      s"""class A(x: Int, y: Int)
         |
         |class B(x: Int, z: Int) extends A($CARET)
         |""".stripMargin,
    lookupString = "x, y"
  )

  // should have (x = ???, y = ???) but not (x, y)
  def testNoTypeMatchingConstructor(): Unit = checkNoCompletionWithoutTailText(
    fileText =
      s"""class A(x: Int, y: Int)
         |
         |class B(x: Int, y: String) extends A($CARET)
         |""".stripMargin,
    lookupString = "x, y"
  )

  def testConstructorAssignment(): Unit = doRawCompletionTest(
    fileText =
      s"""class A(x: Int, y: Int)
         |
         |class B extends A($CARET)
         |""".stripMargin,
    resultText =
      s"""class A(x: Int, y: Int)
         |
         |class B extends A(x = ???, y = ???)$CARET
         |""".stripMargin,
  ) {
    hasItemText(_, "x, y")(tailText = " = ")
  }

  def testPositionInConstructorAssignment(): Unit = doCompletionTest(
    fileText =
      s"""class A(x: Int, y: Int, z: Int)
         |
         |class B extends A(, $CARET)
         |""".stripMargin,
    resultText =
      s"""class A(x: Int, y: Int, z: Int)
         |
         |class B extends A(, y = ???, z = ???)$CARET
         |""".stripMargin,
    item = "y, z"
  )

  def testConstructorAssignmentLookupElement(): Unit = checkLookupElement(
    fileText =
      s"""class A(x: Int, y: Int)
         |
         |class B(x: Int) extends A(x$CARET)
         |""".stripMargin,
    resultText =
      s"""class A(x: Int, y: Int)
         |
         |class B(x: Int) extends A(x = ???, y = ???)$CARET
         |""".stripMargin,
    item = "x, y",
    isSuper = false,
    icons = PARAMETER, PARAMETER
  )

  //todo fix for Scala 3
  @RunWithScalaVersions(Array(
    TestScalaVersion.Scala_2_12,
  ))
  def testConstructorCallAfterNew(): Unit = doCompletionTest(
    fileText =
      s"""class A(x: Int, y: Int)
         |
         |val x: Int = ???
         |val y: Int = ???
         |
         |new A($CARET)
        """.stripMargin,
    resultText =
      s"""class A(x: Int, y: Int)
         |
         |val x: Int = ???
         |val y: Int = ???
         |
         |new A(x, y)$CARET
        """.stripMargin,
    item = "x, y"
  )

  @RunWithScalaVersions(Array(
    TestScalaVersion.Scala_2_12,
  ))
  def testConstructorCallAfterNew2(): Unit = doCompletionTest(
    fileText =
      s"""class A(x: Int, y: Int) {
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |}
         |
         |val x: Int = ???
         |val y: Int = ???
         |val z: Int = ???
         |
         |new A($CARET)
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
         |new A(x, y, z)$CARET
        """.stripMargin,
    item = "x, y, z"
  )

  @RunWithScalaVersions(Array(
    TestScalaVersion.Scala_2_12,
  ))
  def testConstructorCallAfterNew3(): Unit = doCompletionTest(
    fileText =
      s"""class A(x: Int, y: Int) {
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |}
         |
         |val x: Int = ???
         |val y: Int = ???
         |val z: Int = ???
         |
         |new A($CARET)
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
         |new A(x, y)$CARET
        """.stripMargin,
    item = "x, y"
  )

  @RunWithScalaVersions(Array(
    TestScalaVersion.Scala_2_12,
  ))
  def testConstructorCallAfterNew3Smart(): Unit = doCompletionTest(
    fileText =
      s"""class A(x: Int, y: Int) {
         |  def this(x: Int, y: Int, z: Int) = this(x, y)
         |}
         |
         |val x: Int = ???
         |val y: Int = ???
         |val z: Int = ???
         |
         |new A($CARET)
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
         |new A(x, y)$CARET
        """.stripMargin,
    item = "x, y",
    completionType = CompletionType.SMART
  )

  @RunWithScalaVersions(Array(
    TestScalaVersion.Scala_2_12,
  ))
  def testConstructorCallAfterNewLookupElement(): Unit = checkLookupElement(
    fileText =
      s"""class A(x: Int, y: Int)
         |
         |val x: Int = ???
         |val y: Int = ???
         |
         |new A($CARET)
        """.stripMargin,
    resultText =
      s"""class A(x: Int, y: Int)
         |
         |val x: Int = ???
         |val y: Int = ???
         |
         |new A(x, y)$CARET
        """.stripMargin,
    item = "x, y",
    isSuper = true,
    icons = PATTERN_VAL, PATTERN_VAL
  )

  @RunWithScalaVersions(Array(
    TestScalaVersion.Scala_2_12,
  ))
  def testAfterParenthesisOnlyInConstructorAfterNew(): Unit = checkNoCompletion(
    fileText =
      s"""class A(x: Int, y: Int)
         |
         |val x: Int = ???
         |val y: Int = ???
         |
         |new A(x, $CARET)
        """.stripMargin
  )

  def testBeforeParenthesisOnlyInConstructorAfterNew(): Unit = checkNoCompletion(
    fileText =
      s"""class A(x: Int, y: Int)
         |
         |val x: Int = ???
         |val y: Int = ???
         |
         |new A($CARET, y)
        """.stripMargin
  )

  @RunWithScalaVersions(Array(
    TestScalaVersion.Scala_2_12,
  ))
  def testPositionInConstructorAfterNew(): Unit = doCompletionTest(
    fileText =
      s"""class A(x: Int, y: Int, z: Int)
         |
         |val y: Int = ???
         |val z: Int = ???
         |
         |new A(, $CARET)
        """.stripMargin,
    resultText =
      s"""class A(x: Int, y: Int, z: Int)
         |
         |val y: Int = ???
         |val z: Int = ???
         |
         |new A(, y, z)$CARET
        """.stripMargin,
    item = "y, z"
  )

  def testEmptyConstructorAfterNew(): Unit = checkNoCompletion(
    s"""class A()
       |
       |val x: Int = ???
       |val y: Int = ???
       |
       |new A($CARET)
       |""".stripMargin
  )

  def testTooShortConstructorAfterNew(): Unit = checkNoCompletion(
    s"""class A(x: Int)
       |
       |val x: Int = ???
       |val y: Int = ???
       |
       |new A($CARET)
       |""".stripMargin
  )

  // should have (x = ???, y = ???) but not (x, y)
  def testNoNameMatchingConstructorAfterNew(): Unit = checkNoCompletionWithoutTailText(
    fileText =
      s"""class A(x: Int, y: Int)
         |
         |val x: Int = ???
         |val z: Int = ???
         |
         |new A($CARET)
         |""".stripMargin,
    lookupString = "x, y"
  )

  // should have (x = ???, y = ???) but not (x, y)
  def testNoTypeMatchingConstructorAfterNew(): Unit = checkNoCompletionWithoutTailText(
    fileText =
      s"""class A(x: Int, y: Int)
         |
         |val x: Int = ???
         |val y: String = ???
         |
         |new A($CARET)
         |""".stripMargin,
    lookupString = "x, y"
  )

  def testConstructorAssignmentAfterNew(): Unit = doRawCompletionTest(
    fileText =
      s"""class A()(x: Int, y: Int)
         |
         |new A()(x$CARET)
         |""".stripMargin,
    resultText =
      s"""class A()(x: Int, y: Int)
         |
         |new A()(x = ???, y = ???)$CARET
         |""".stripMargin,
  ) {
    hasItemText(_, "x, y")(tailText = " = ")
  }

  def testPositionInConstructorAssignmentAfterNew(): Unit = doCompletionTest(
    fileText =
      s"""class A(x: Int, y: Int, z: Int)
         |
         |new A(, $CARET)
         |""".stripMargin,
    resultText =
      s"""class A(x: Int, y: Int, z: Int)
         |
         |new A(, y = ???, z = ???)$CARET
         |""".stripMargin,
    item = "y, z"
  )

  def testConstructorAssignmentLookupElementAfterNew(): Unit = checkLookupElement(
    fileText =
      s"""class A(x: Int, y: Int)
         |
         |new A(x$CARET)
         |""".stripMargin,
    resultText =
      s"""class A(x: Int, y: Int)
         |
         |new A(x = ???, y = ???)$CARET
         |""".stripMargin,
    item = "x, y",
    isSuper = false,
    icons = PARAMETER, PARAMETER
  )

  def testSuperCall(): Unit = doCompletionTest(
    fileText =
      s"""class A {
         |  def foo(x: Int, y: Int, z: Int) = 1
         |  def foo(x: Int, y: Int) = 2
         |}
         |
         |class B extends A {
         |  override def foo(x: Int, y: Int, z: Int) = {
         |    super.foo($CARET)
         |  }
         |}
        """.stripMargin,
    resultText =
      s"""class A {
         |  def foo(x: Int, y: Int, z: Int) = 1
         |  def foo(x: Int, y: Int) = 2
         |}
         |
         |class B extends A {
         |  override def foo(x: Int, y: Int, z: Int) = {
         |    super.foo(x, y)$CARET
         |  }
         |}
        """.stripMargin,
    item = "x, y"
  )

  def testSuperCall2(): Unit = doCompletionTest(
    fileText =
      s"""class A {
         |  def foo(x: Int, y: Int, z: Int) = 1
         |  def foo(x: Int, y: Int) = 2
         |}
         |
         |class B extends A {
         |  override def foo(x: Int, y: Int, z: Int) = {
         |    super.foo($CARET)
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""class A {
         |  def foo(x: Int, y: Int, z: Int) = 1
         |  def foo(x: Int, y: Int) = 2
         |}
         |
         |class B extends A {
         |  override def foo(x: Int, y: Int, z: Int) = {
         |    super.foo(x, y, z)$CARET
         |  }
         |}
      """.stripMargin,
    item = "x, y, z"
  )

  def testSuperCallLookupElement(): Unit = checkLookupElement(
    fileText =
      s"""class A {
         |  def foo(x: Int, y: Int) = 42
         |}
         |
         |class B extends A {
         |  override def foo(x: Int, y: Int) =
         |    super.foo($CARET)
         |}
         |""".stripMargin,
    resultText =
      s"""class A {
         |  def foo(x: Int, y: Int) = 42
         |}
         |
         |class B extends A {
         |  override def foo(x: Int, y: Int) =
         |    super.foo(x, y)$CARET
         |}
         |""".stripMargin,
    item = "x, y",
    isSuper = true,
    icons = PARAMETER, PARAMETER
  )

  def testAfterParenthesisOnlyInSuperMethod(): Unit = checkNoCompletion(
    s"""class A {
       |  def foo(x: Int, y: Int) = 42
       |}
       |
       |class B extends A {
       |  override def foo(x: Int, y: Int) =
       |    super.foo(x, $CARET)
       |}
       |""".stripMargin
  )

  def testBeforeParenthesisOnlyInSuperMethod(): Unit = checkNoCompletion(
    s"""class A {
       |  def foo(x: Int, y: Int) = 42
       |}
       |
       |class B extends A {
       |  override def foo(x: Int, y: Int) =
       |    super.foo($CARET, y)
       |}
       |""".stripMargin
  )

  def testPositionInSuperMethod(): Unit = doCompletionTest(
    fileText =
      s"""class A {
         |  def foo(x: Int, y: Int, z: Int) = 42
         |}
         |
         |class B extends A {
         |  override def foo(x: Int, y: Int, z: Int) =
         |    super.foo(, $CARET)
         |}
         |""".stripMargin,
    resultText =
      s"""class A {
         |  def foo(x: Int, y: Int, z: Int) = 42
         |}
         |
         |class B extends A {
         |  override def foo(x: Int, y: Int, z: Int) =
         |    super.foo(, y, z)$CARET
         |}
         |""".stripMargin,
    item = "y, z"
  )

  def testEmptySuperMethod(): Unit = checkNoCompletion(
    s"""class A {
       |  def foo() = 42
       |}
       |
       |class B extends A {
       |  override def foo() =
       |    super.foo($CARET)
       |}
       |""".stripMargin
  )

  def testTooShortSuperMethod(): Unit = checkNoCompletion(
    s"""class A {
       |  def foo(x: Int) = 42
       |}
       |
       |class B extends A {
       |  override def foo(x: Int) =
       |    super.foo($CARET)
       |}
       |""".stripMargin
  )

  def testNoNameMatchingSuperMethod(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class A {
         |  def foo(x: Int, y: Int) = 42
         |}
         |
         |class B extends A {
         |  override def foo(x: Int, z: Int) =
         |    super.foo($CARET)
         |}
         |""".stripMargin,
    item = "x, y"
  )

  def testMethodCall(): Unit = doCompletionTest(
    fileText =
      s"""class A {
         |  def foo(x: Int, y: Int) = 1
         |}
         |
         |class B extends A {
         |  def bar(x: Int, y: Int) = {
         |    foo($CARET)
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""class A {
         |  def foo(x: Int, y: Int) = 1
         |}
         |
         |class B extends A {
         |  def bar(x: Int, y: Int) = {
         |    foo(x, y)$CARET
         |  }
         |}
      """.stripMargin,
    item = "x, y"
  )

  def testQualifiedMethodCall(): Unit = doCompletionTest(
    fileText =
      s"""class A {
         |  def foo(x: Int, y: Int) = 1
         |}
         |
         |class B {
         |  private val a = new A
         |
         |  def bar(x: Int, y: Int) = {
         |    a.foo($CARET)
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""class A {
         |  def foo(x: Int, y: Int) = 1
         |}
         |
         |class B {
         |  private val a = new A
         |
         |  def bar(x: Int, y: Int) = {
         |    a.foo(x, y)$CARET
         |  }
         |}
      """.stripMargin,
    item = "x, y"
  )

  def testQualifiedMethodCallCompletionChar(): Unit = doCompletionTest(
    fileText =
      s"""class A {
         |  def foo(x: Int, y: Int) = 1
         |}
         |
         |class B {
         |  private val a = new A
         |
         |  def bar(x: Int, y: Int) = {
         |    a.foo($CARET)
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""class A {
         |  def foo(x: Int, y: Int) = 1
         |}
         |
         |class B {
         |  private val a = new A
         |
         |  def bar(x: Int, y: Int) = {
         |    a.foo(x, y)$CARET
         |  }
         |}
      """.stripMargin,
    item = "x, y",
    char = ')'
  )

  def testMethodCallLookupElement(): Unit = checkLookupElement(
    fileText =
      s"""class A {
         |  def foo(x: Int, y: Int) = 42
         |}
         |
         |class B extends A {
         |  def bar(x: Int, y: Int) =
         |    foo($CARET)
         |}
         |""".stripMargin,
    resultText =
      s"""class A {
         |  def foo(x: Int, y: Int) = 42
         |}
         |
         |class B extends A {
         |  def bar(x: Int, y: Int) =
         |    foo(x, y)$CARET
         |}
         |""".stripMargin,
    item = "x, y",
    isSuper = false,
    icons = PARAMETER, PARAMETER
  )

  def testAfterParenthesisOnlyInMethodCall(): Unit = checkNoCompletion(
    s"""class A {
       |  def foo(x: Int, y: Int) = 42
       |}
       |
       |class B extends A {
       |  def bar(x: Int, y: Int) =
       |    foo(x, $CARET)
       |}
       |""".stripMargin
  )

  def testBeforeParenthesisOnlyInMethodCall(): Unit = checkNoCompletion(
    s"""class A {
       |  def foo(x: Int, y: Int) = 42
       |}
       |
       |class B extends A {
       |  def bar(x: Int, y: Int) =
       |    foo($CARET, y)
       |}
       |""".stripMargin
  )

  def testPositionInMethodCall(): Unit = doCompletionTest(
    fileText =
      s"""class A {
         |  def foo(x: Int, y: Int, z: Int) = 42
         |}
         |
         |class B extends A {
         |  def bar(y: Int, z: Int) =
         |    foo(, $CARET)
         |}
         |""".stripMargin,
    resultText =
      s"""class A {
         |  def foo(x: Int, y: Int, z: Int) = 42
         |}
         |
         |class B extends A {
         |  def bar(y: Int, z: Int) =
         |    foo(, y, z)$CARET
         |}
         |""".stripMargin,
    item = "y, z"
  )

  def testEmptyMethod(): Unit = checkNoCompletion(
    s"""class A {
       |  def foo() = 42
       |}
       |
       |class B extends A {
       |  def bar() =
       |    foo($CARET)
       |}
       |""".stripMargin
  )

  def testTooShortMethod(): Unit = checkNoCompletion(
    s"""class A {
       |  def foo(x: Int) = 42
       |}
       |
       |class B extends A {
       |  def bar(x: Int) =
       |    foo($CARET)
       |}
       |""".stripMargin
  )

  def testNoNameMatchingMethod(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class A {
         |  def foo(x: Int, y: Int) = 42
         |}
         |
         |class B extends A {
         |  def bar(x: Int, z: Int) =
         |    foo($CARET)
         |}
         |""".stripMargin,
    item = "x, y"
  )

  def testNoTypeMatchingMethod(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class A {
         |  def foo(x: Int, y: Int) = 42
         |}
         |
         |class B extends A {
         |  def bar(x: Int, y: String) =
         |    foo($CARET)
         |}
         |""".stripMargin,
    item = "x, y"
  )

  def testCaseClass(): Unit = doCompletionTest(
    fileText =
      s"""case class A(x: Int, y: Int)
         |
         |class B {
         |  def bar(x: Int, y: Int) = {
         |    A($CARET)
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""case class A(x: Int, y: Int)
         |
         |class B {
         |  def bar(x: Int, y: Int) = {
         |    A(x, y)$CARET
         |  }
         |}
      """.stripMargin,
    item = "x, y"
  )

  def testCaseClassLookupElement(): Unit = checkLookupElement(
    fileText =
      s"""case class A(x: Int, y: Int)
         |
         |class B {
         |  def bar(x: Int, y: Int) =
         |    A($CARET)
         |}
         |""".stripMargin,
    resultText =
      s"""case class A(x: Int, y: Int)
         |
         |class B {
         |  def bar(x: Int, y: Int) =
         |    A(x, y)$CARET
         |}
         |""".stripMargin,
    item = "x, y",
    isSuper = false,
    icons = PARAMETER, PARAMETER
  )

  def testBeforeParenthesisOnlyInCaseClass(): Unit = checkNoCompletion(
    s"""case class A(x: Int, y: Int)
       |
       |class B {
       |  def bar(x: Int, y: Int) =
       |    A(x, $CARET)
       |}
       |""".stripMargin
  )

  def testAfterParenthesisOnlyInCaseClass(): Unit = checkNoCompletion(
    s"""case class A(x: Int, y: Int)
       |
       |class B {
       |  def bar(x: Int, y: Int) =
       |    A($CARET, y)
       |}
       |""".stripMargin
  )

  def testPositionInCaseClass(): Unit = doCompletionTest(
    fileText =
      s"""case class A(x: Int, y: Int, z: Int)
         |
         |class B {
         |  def bar(y: Int, z: Int) =
         |    A(, $CARET)
         |}
         |""".stripMargin,
    resultText =
      s"""case class A(x: Int, y: Int, z: Int)
         |
         |class B {
         |  def bar(y: Int, z: Int) =
         |    A(, y, z)$CARET
         |}
         |""".stripMargin,
    item = "y, z"
  )

  def testEmptyCaseClassArgumentsList(): Unit = checkNoCompletion(
    s"""case class A()
       |
       |class B {
       |  def bar() =
       |    A($CARET)
       |}
       |""".stripMargin
  )

  def testTooShortCaseClassArgumentsList(): Unit = checkNoCompletion(
    s"""case class A(x: Int)
       |
       |class B {
       |  def bar(x: Int) =
       |    A($CARET)
       |}
       |""".stripMargin
  )

  // should have (x = ???, y = ???) but not (x, y)
  def testNoNameMatchingCaseClass(): Unit = checkNoCompletionWithoutTailText(
    fileText =
      s"""case class A(x: Int, y: Int)
         |
         |class B {
         |  def bar(x: Int, z: Int) =
         |    A($CARET)
         |}
         |""".stripMargin,
    lookupString = "x, y"
  )

  // should have (x = ???, y = ???) but not (x, y)
  def testNoTypeMatchingCaseClass(): Unit = checkNoCompletionWithoutTailText(
    fileText =
      s"""case class A(x: Int, y: Int)
         |
         |class B {
         |  def bar(x: Int, y: String) =
         |    A($CARET)
         |}
         |""".stripMargin,
    lookupString = "x, y"
  )

  def testCaseClassAssignment(): Unit = doRawCompletionTest(
    fileText =
      s"""final case class Foo()(foo: Int, bar: Int)
         |
         |Foo()(f$CARET)
         |""".stripMargin,
    resultText =
      s"""final case class Foo()(foo: Int, bar: Int)
         |
         |Foo()(foo = ???, bar = ???)$CARET
         |""".stripMargin,
  ) {
    hasItemText(_, "foo, bar")(tailText = " = ")
  }

  def testPhysicalApplyMethodAssignment(): Unit = doCompletionTest(
    fileText =
      s"""final class Foo private(val foo: Int,
         |                        val bar: Int,
         |                        val baz: Int)
         |
         |object Foo {
         |
         |  def apply(foo: Int,
         |            bar: Int,
         |            baz: Int) =
         |    new Foo(foo, bar, baz)
         |
         |  def apply(foo: Int,
         |            bar: Int) =
         |    new Foo(foo, bar, 42)
         |}
         |
         |Foo(f$CARET)
         |""".stripMargin,
    resultText =
      s"""final class Foo private(val foo: Int,
         |                        val bar: Int,
         |                        val baz: Int)
         |
         |object Foo {
         |
         |  def apply(foo: Int,
         |            bar: Int,
         |            baz: Int) =
         |    new Foo(foo, bar, baz)
         |
         |  def apply(foo: Int,
         |            bar: Int) =
         |    new Foo(foo, bar, 42)
         |}
         |
         |Foo(foo = ???, bar = ???, baz = ???)$CARET
         |""".stripMargin,
    item = "foo, bar, baz"
  )

  def testPhysicalApplyMethodAssignment2(): Unit = doCompletionTest(
    fileText =
      s"""final class Foo private(val foo: Int,
         |                        val bar: Int,
         |                        val baz: Int)
         |
         |object Foo {
         |
         |  def apply(foo: Int,
         |            bar: Int,
         |            baz: Int) =
         |    new Foo(foo, bar, baz)
         |
         |  def apply(foo: Int,
         |            bar: Int) =
         |    new Foo(foo, bar, 42)
         |}
         |
         |Foo(f$CARET)
         |""".stripMargin,
    resultText =
      s"""final class Foo private(val foo: Int,
         |                        val bar: Int,
         |                        val baz: Int)
         |
         |object Foo {
         |
         |  def apply(foo: Int,
         |            bar: Int,
         |            baz: Int) =
         |    new Foo(foo, bar, baz)
         |
         |  def apply(foo: Int,
         |            bar: Int) =
         |    new Foo(foo, bar, 42)
         |}
         |
         |Foo(foo = ???, bar = ???)$CARET
         |""".stripMargin,
    item = "foo, bar"
  )

  def testApplyCallAssignmentLookupElement(): Unit = checkLookupElement(
    fileText =
      s"""final case class Foo(foo: Int, bar: Int)
         |
         |Foo(f$CARET)
         |""".stripMargin,
    resultText =
      s"""final case class Foo(foo: Int, bar: Int)
         |
         |Foo(foo = ???, bar = ???)$CARET
         |""".stripMargin,
    item = "foo, bar",
    isSuper = false,
    icons = PARAMETER, PARAMETER
  )

  def testCaseClassCompletionChar(): Unit = doCompletionTest(
    fileText =
      s"""final case class Foo(foo: Int, bar: Int)
         |
         |Foo(f$CARET)
         |""".stripMargin,
    resultText =
      s"""final case class Foo(foo: Int, bar: Int)
         |
         |Foo(foo, bar)$CARET
         |""".stripMargin,
    item = "foo, bar",
    char = ')'
  )

  def testAfterParenthesisOnlyInApplyCall(): Unit = checkNoCompletion(
    s"""final case class Foo(foo: Int, bar: Int)
       |
       |Foo(foo, $CARET)
       |""".stripMargin
  )

  def testBeforeParenthesisOnlyInApplyCall(): Unit = checkNoCompletion(
    s"""final case class Foo(foo: Int, bar: Int)
       |
       |Foo($CARET, bar)
       |""".stripMargin
  )

  def testPositionInApplyCallAssignment(): Unit = doCompletionTest(
    fileText =
      s"""final case class Foo(foo: Int, bar: Int, baz: Int)
         |
         |Foo(, $CARET)
         |""".stripMargin,
    resultText =
      s"""final case class Foo(foo: Int, bar: Int, baz: Int)
         |
         |Foo(, bar = ???, baz = ???)$CARET
         |""".stripMargin,
    item = "bar, baz"
  )

  def testEmptyCaseClass(): Unit = checkNoCompletion(
    s"""final case class Foo()
       |
       |Foo(f$CARET)
       |""".stripMargin
  )

  def testTooShortCaseClass(): Unit = checkNoCompletion(
    s"""final case class Foo(foo: Int)
       |
       |Foo(f$CARET)
       |""".stripMargin
  )

  def testNonApplyMethod(): Unit = checkNoCompletion(
    s"""object Foo {
       |  def baz(foo: Int, bar: Int): Unit = {}
       |}
       |
       |Foo.baz(f$CARET)
       |""".stripMargin
  )

  def testClauseLookupElement(): Unit = checkLookupElement(
    fileText =
      s"""def foo(bar: Int, baz: String): Int = 42
         |
         |def foo(bar: Int): Int = {
         |  val baz = ""
         |  foo(b$CARET)
         |}
         |""".stripMargin,
    resultText =
      s"""def foo(bar: Int, baz: String): Int = 42
         |
         |def foo(bar: Int): Int = {
         |  val baz = ""
         |  foo(bar, baz)$CARET
         |}
         |""".stripMargin,
    item = "bar, baz",
    isSuper = false,
    icons = PARAMETER, PATTERN_VAL
  )

  def testClauseLookupElement2(): Unit = checkLookupElement(
    fileText =
      s"""def foo(bar: Int,
         |        baz: String): Unit = {}
         |
         |var bar = 42
         |"" match {
         |  case baz => foo(b$CARET)
         |}
         |""".stripMargin,
    resultText =
      s"""def foo(bar: Int,
         |        baz: String): Unit = {}
         |
         |var bar = 42
         |"" match {
         |  case baz => foo(bar, baz)$CARET
         |}
         |""".stripMargin,
    item = "bar, baz",
    isSuper = false,
    icons = PATTERN_VAL, PATTERN_VAL
  )

  def testClauseLookupElement3(): Unit = checkNoBasicCompletion(
    fileText =
      s"""import java.util.{Collections, List}
         |import Thread._
         |
         |def emptyList = Collections.emptyList[String]
         |
         |def foo(emptyList: List[String],
         |        currentThread: Thread,
         |        defaultUncaughtExceptionHandler: UncaughtExceptionHandler): Unit = {}
         |
         |foo(e$CARET)
         |""".stripMargin,
    item = "emptyList, currentThread, defaultUncaughtExceptionHandler",
  )

  def testClauseLookupElementAccessAll(): Unit = doCompletionTest(
    fileText =
      s"""import java.util.{Collections, List}
         |import Thread._
         |
         |def emptyList = Collections.emptyList[String]
         |
         |def foo(emptyList: List[String],
         |        currentThread: Thread,
         |        defaultUncaughtExceptionHandler: UncaughtExceptionHandler): Unit = {}
         |
         |foo(emptyL$CARET)
         |""".stripMargin,
    resultText =
      s"""import java.util.{Collections, List}
         |import Thread._
         |
         |def emptyList = Collections.emptyList[String]
         |
         |def foo(emptyList: List[String],
         |        currentThread: Thread,
         |        defaultUncaughtExceptionHandler: UncaughtExceptionHandler): Unit = {}
         |
         |foo(emptyList, currentThread, defaultUncaughtExceptionHandler)$CARET
         |""".stripMargin,
    item = "emptyList, currentThread, defaultUncaughtExceptionHandler",
    time = 2
  )

  def testPositionInClause(): Unit = doCompletionTest(
    fileText =
      s"""def foo(bar: Int,
         |        baz: String,
         |        barBaz: Boolean): Unit =
         |  foo(, $CARET)
         |""".stripMargin,
    resultText =
      s"""def foo(bar: Int,
         |        baz: String,
         |        barBaz: Boolean): Unit =
         |  foo(, baz, barBaz)$CARET
         |""".stripMargin,
    item = "baz, barBaz"
  )

  def testEmptyClause(): Unit = checkNoCompletion(
    s"""def foo() = 42
       |
       |foo(f$CARET)
       |""".stripMargin
  )

  def testTooShortClause(): Unit = checkNoCompletion(
    s"""def foo(bar: Int) = 42
       |
       |foo(b$CARET)
       |""".stripMargin
  )

  def testNoNameMatchingClause(): Unit = checkNoCompletion(
    s"""def foo(bar: Int, baz: String): Int = 42
       |
       |def foo(bar: Int): Int = {
       |  val barBaz = ""
       |  foo(b$CARET)
       |}
       |""".stripMargin
  )

  def testNoTypeMatchingClause(): Unit = checkNoCompletion(
    s"""def foo(bar: Int, baz: String): Int = 42
       |
       |def foo(bar: Int): Int = {
       |  val baz = 42
       |  foo(b$CARET)
       |}
       |""".stripMargin
  )

  def testMultipleClause(): Unit = doCompletionTest(
    fileText =
      s"""def foo(foo: Int)
         |       (bar: Int, baz: String): Int = 42
         |
         |def foo(bar: Int): Int = {
         |  val baz = ""
         |  foo()(b$CARET)
         |}
         |""".stripMargin,
    resultText =
      s"""def foo(foo: Int)
         |       (bar: Int, baz: String): Int = 42
         |
         |def foo(bar: Int): Int = {
         |  val baz = ""
         |  foo()(bar, baz)$CARET
         |}
         |""".stripMargin,
    item = "bar, baz"
  )

  def testMultipleClausePosition(): Unit = checkNoCompletion(
    s"""def foo(bar: Int, baz: String)(): Int = 42
       |
       |def foo(bar: Int): Int = {
       |  val baz = ""
       |  foo()($CARET)
       |}
       |""".stripMargin
  )
}
