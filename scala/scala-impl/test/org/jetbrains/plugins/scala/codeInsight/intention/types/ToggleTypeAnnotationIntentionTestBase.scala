package org.jetbrains.plugins.scala.codeInsight.intention.types

import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase
import org.jetbrains.plugins.scala.extensions.executeWriteActionCommand
import org.junit.Assert.{assertNotNull, assertTrue, fail}

abstract class ToggleTypeAnnotationIntentionTestBase extends ScalaIntentionTestBase {
  override def familyName: String = ToggleTypeAnnotation.FamilyName

  def testCollectionFactorySimplification(): Unit = doTest(
    "val v = Seq.empty[String]",
    "val v: Seq[String] = Seq.empty"
  )

  //for example for `val myValue = List.empty[String]` two options are shown: Seq[String] and List[String]
  def testCollectionFactorySimplification_MoreThenSingleValidTypeAnnotationCandidate(): Unit = {
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable)

    val text = "val v = List.empty[String]"
    myFixture.configureByText(fileType, text)
    val intention = findIntentionByName(familyName).getOrElse {
      fail("Intention is not found").asInstanceOf[Nothing]
    }

    executeWriteActionCommand("Test Intention Command")({
      intention.invoke(getProject, getEditor, getFile)
    })(getProject)

    val templateManager = TemplateManager.getInstance(getProject)
    val activeTemplate = templateManager.getActiveTemplate(getEditor)
    assertNotNull("Expected to find some active template but found none", activeTemplate)

    val success = templateManager.finishTemplate(getEditor)
    assertTrue("Expected template to finish", success)

    val expectedResultText = "val v: Seq[String] = List.empty"
    myFixture.checkResult(expectedResultText)
  }

  def testOptionFactorySimplification(): Unit = doTest(
    "val v = Option.empty[String]",
    "val v: Option[String] = Option.empty"
  )

  def testCompoundType(): Unit = doTest(
    """val foo = new Runnable {
      |  def helper(): Unit = ???
      |
      |  override def run(): Unit = ???
      |}""".stripMargin,
    """val foo: Runnable = new Runnable {
      |  def helper(): Unit = ???
      |
      |  override def run(): Unit = ???
      |}""".stripMargin
  )

  def testCompoundTypeWithTypeMember(): Unit = doTest(
    s"""
       |trait Foo {
       |  type X
       |}
       |
       |val f${caretTag}oo = new Foo {
       |  override type X = Int
       |
       |  def helper(x: X): Unit = ???
       |}
     """.stripMargin,
    s"""
       |trait Foo {
       |  type X
       |}
       |
       |val f${caretTag}oo: Foo {type X = Int} = new Foo {
       |  override type X = Int
       |
       |  def helper(x: X): Unit = ???
       |}
     """.stripMargin
  )

  def testInfixType(): Unit = doTest(
    s"""
       |trait A
       |
       |trait B
       |
       |def foo(): =:=[A, <:<[B, =:=[=:=[B, B], A]]] = ???
       |val ba${caretTag}r = foo()
     """.stripMargin,
    s"""
       |trait A
       |
       |trait B
       |
       |def foo(): =:=[A, <:<[B, =:=[=:=[B, B], A]]] = ???
       |val ba${caretTag}r: A =:= (B <:< (B =:= B =:= A)) = foo()
     """.stripMargin
  )

  def testInfixDifferentAssociativity(): Unit = doTest(
    s"""
       |trait +[A, B]
       |
       |trait ::[A, B]
       |
       |trait A
       |
       |def foo(): ::[+[A, +[::[A, A], A]], +[A, ::[A, A]]] = ???
       |val ba${caretTag}r = foo()
     """.stripMargin,
    s"""
       |trait +[A, B]
       |
       |trait ::[A, B]
       |
       |trait A
       |
       |def foo(): ::[+[A, +[::[A, A], A]], +[A, ::[A, A]]] = ???
       |val ba${caretTag}r: (A + ((A :: A) + A)) :: (A + (A :: A)) = foo()
     """.stripMargin
  )

  def testShowAsInfixAnnotation(): Unit = doTest(
    s"""
       |import scala.annotation.showAsInfix
       |
       |@showAsInfix class Map[A, B]
       |
       |def foo(): Map[Int, Map[Int, String]] = ???
       |val b${caretTag}ar = foo()
     """.stripMargin,
    s"""
       |import scala.annotation.showAsInfix
       |
       |@showAsInfix class Map[A, B]
       |
       |def foo(): Map[Int, Map[Int, String]] = ???
       |val b${caretTag}ar: Int Map (Int Map String) = foo()
     """.stripMargin
  )

  def testTupledFunction(): Unit = doTest(
    s"""class Test {
       |  def g(f: (String, Int) => Unit): Unit = {
       |    val ${caretTag}t = f.tupled // Add type annotation to value definition
       |  }
       |}""".stripMargin,
    s"""class Test {
       |  def g(f: (String, Int) => Unit): Unit = {
       |    val ${caretTag}t: ((String, Int)) => Unit = f.tupled // Add type annotation to value definition
       |  }
       |}""".stripMargin
  )

  // see SCL-16739
  def testParameterAtEnd(): Unit = doTest(
    s"""
       |class Seq[T] {
       |  def foreach(f: T => Unit): Unit = ()
       |}
       |
       |val strings: Seq[String] = new Seq
       |strings.foreach(abc$caretTag => println(abc))
       |""".stripMargin,
    s"""
       |class Seq[T] {
       |  def foreach(f: T => Unit): Unit = ()
       |}
       |
       |val strings: Seq[String] = new Seq
       |strings.foreach((abc: String)$caretTag => println(abc))
       |""".stripMargin
  )

  def testAddTypeToValPattern(): Unit = doTest(
    s"""
       |object Test {
       |  val (${caretTag}i, j) = (0, 1)
       |}
       |""".stripMargin,
    s"""
       |object Test {
       |  val (${caretTag}i: Int, j) = (0, 1)
       |}
       |""".stripMargin
  )

  def testRemoveTypeFromValPattern(): Unit = doTest(
    s"""
       |object Test {
       |  val (i: ${caretTag}Int, j) = (0, 1)
       |}
       |""".stripMargin,
    s"""
       |object Test {
       |  val (i$caretTag, j) = (0, 1)
       |}
       |""".stripMargin
  )

  def testAddTypeToMatchPattern(): Unit = doTest(
    s"""
       |object Test {
       |  0 match {
       |    case x$caretTag =>
       |  }
       |}
       |""".stripMargin,
    s"""
       |object Test {
       |  0 match {
       |    case x$caretTag: Int =>
       |  }
       |}
       |""".stripMargin
  )

  def testRemoveTypeFromMatchPattern(): Unit = doTest(
    s"""
       |object Test {
       |  0 match {
       |    case x$caretTag: Int =>
       |  }
       |}
       |""".stripMargin,
    s"""
       |object Test {
       |  0 match {
       |    case x$caretTag =>
       |  }
       |}
       |""".stripMargin
  )

  def testRemoveBaseClassesSerializableAndProduct(): Unit = doTest(
    s"""sealed trait MyTrait
      |
      |case object MyObject1 extends MyTrait
      |
      |case object MyObject2 extends MyTrait
      |
      |object Usage {
      |  val map$caretTag = Map(
      |    MyObject1 -> "111",
      |    MyObject2 -> "222"
      |  )
      |}
      |""".stripMargin,
    s"""sealed trait MyTrait
       |
       |case object MyObject1 extends MyTrait
       |
       |case object MyObject2 extends MyTrait
       |
       |object Usage {
       |  val map$caretTag: Map[MyTrait, String] = Map(
       |    MyObject1 -> "111",
       |    MyObject2 -> "222"
       |  )
       |}
       |""".stripMargin
  )

  def testAddTypeAnnotationWithTypeWildCard(): Unit = doTest(
    s"""
       |class Foo[T]
       |
       |abstract class A {
       |  def b(): Foo[_]
       |}
       |
       |class B extends A {
       |  protected def b$caretTag() = new Foo[_]
       |}
       |""".stripMargin,
    s"""
       |class Foo[T]
       |
       |abstract class A {
       |  def b(): Foo[_]
       |}
       |
       |class B extends A {
       |  protected def b$caretTag(): Foo[_] = new Foo[_]
       |}
       |""".stripMargin
  )

  def testAddTypeAnnotationToUnderscoreParameter_CaretBeforeUnderscore(): Unit = doTest(
    s"""Seq(1, 2).map(${CARET}_.toString)""",
    s"""Seq(1, 2).map($CARET(_: Int).toString)""",
  )

  def testAddTypeAnnotationToUnderscoreParameter_CaretAfterUnderscore(): Unit = doTest(
    s"""Seq(1, 2).map(_${CARET}.toString)""",
    s"""Seq(1, 2).map((_: Int)$CARET.toString)""",
  )

  def testRemoveTypeAnnotationToUnderscoreParameter_CaretBeforeUnderscoreSection(): Unit = doTest(
    s"""Seq(1, 2).map((${CARET}_: Int).toString)""",
    s"""Seq(1, 2).map(${CARET}_.toString)""",
  )

  def testRemoveTypeAnnotationToUnderscoreParameter_CaretAfterUnderscoreSection(): Unit = doTest(
    s"""Seq(1, 2).map((_: Int$CARET).toString)""",
    s"""Seq(1, 2).map(_${CARET}.toString)""",
  )

  def testRemoveTypeAnnotationToUnderscoreParameter_CaretInTheMiddleOfUnderscoreSection(): Unit = doTest(
    s"""Seq(1, 2).map((_: ${CARET}Int).toString)""",
    s"""Seq(1, 2).map(_$CARET.toString)""",
  )

  def testRemoveTypeAnnotationToUnderscoreParameter_CaretInTheMiddleOfUnderscoreSection_TypeWithDot(): Unit = doTest(
    s"""Seq(1, 2).map((_: scala$CARET.Int).toString)""",
    s"""Seq(1, 2).map(_$CARET.toString)""",
  )

  def testAddTypeAnnotationToLambdaParameter_CaretBeforeParameterName(): Unit = doTest(
    s"""Seq(1, 2).map(${CARET}x => x.toString)""",
    s"""Seq(1, 2).map($CARET(x: Int) => x.toString)""",
  )

  def testAddTypeAnnotationToLambdaParameter_CaretAfterParameterName(): Unit = doTest(
    s"""Seq(1, 2).map(x${CARET} => x.toString)""",
    s"""Seq(1, 2).map((x: Int)$CARET => x.toString)""",
  )

  def testRemoveTypeAnnotationToLambdaParameter_CaretBeforeParameterName(): Unit = doTest(
    s"""Seq(1, 2).map((${CARET}x: Int) => x.toString)""",
    s"""Seq(1, 2).map(${CARET}x => x.toString)""",
  )

  def testRemoveTypeAnnotationToLambdaParameter_CaretAfterParameterName(): Unit = doTest(
    s"""Seq(1, 2).map((x: Int$CARET) => x.toString)""",
    s"""Seq(1, 2).map(x$CARET => x.toString)""",
  )

  def testRemoveTypeAnnotationToLambdaParameter_CaretInTheMiddleOfParameter(): Unit = doTest(
    s"""Seq(1, 2).map((x: ${CARET}Int) => x.toString)""",
    s"""Seq(1, 2).map(x$CARET => x.toString)""",
  )

  def testRemoveTypeAnnotationToLambdaParameter_CaretInTheMiddleOfParameter_TypeWithDot(): Unit = doTest(
    s"""Seq(1, 2).map((x: scala$CARET.Int) => x.toString)""",
    s"""Seq(1, 2).map(x$CARET => x.toString)""",
  )
}
