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

  def testInfixType(): Unit = {
    val line =
      if (version.isScala2)
        s"val ba${caretTag}r: A =:= (B <:< (B =:= B =:= A)) = foo()"
      else
        s"val ba${caretTag}r: A =:= B <:< (B =:= B =:= A) = foo()"

    doTest(
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
         |$line
     """.stripMargin
    )
  }

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
    s"""Seq(1, 2).map(_$CARET.toString)""",
    s"""Seq(1, 2).map((_: Int)$CARET.toString)""",
  )

  def testRemoveTypeAnnotationToUnderscoreParameter_CaretBeforeUnderscoreSection(): Unit = doTest(
    s"""Seq(1, 2).map((${CARET}_: Int).toString)""",
    s"""Seq(1, 2).map(${CARET}_.toString)""",
  )

  def testRemoveTypeAnnotationToUnderscoreParameter_CaretAfterUnderscoreSection(): Unit = doTest(
    s"""Seq(1, 2).map((_: Int$CARET).toString)""",
    s"""Seq(1, 2).map(_$CARET.toString)""",
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
    s"""Seq(1, 2).map(x$CARET => x.toString)""",
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

  def testAddTypeAnnotationToBindingPattern(): Unit = doTest(
    s"""Seq(1, 2).map { case ${CARET}x => "42" }""",
    s"""Seq(1, 2).map { case ${CARET}x: Int => "42" }""",
  )

  def testAddTypeAnnotationToBindingPattern_Nested(): Unit = doTest(
    s"""case class SomeCaseClass(foo: Int, bar: String, baz: String)
       |
       |(null: Option[SomeCaseClass]) match {
       |  case Some(SomeCaseClass(${CARET}foo, bar, _)) =>
       |}
       |""".stripMargin,
    s"""case class SomeCaseClass(foo: Int, bar: String, baz: String)
       |
       |(null: Option[SomeCaseClass]) match {
       |  case Some(SomeCaseClass(${CARET}foo: Int, bar, _)) =>
       |}
       |""".stripMargin,
  )

  def testAddTypeAnnotationToBindingPattern_Nested_CaretBeforeComma(): Unit = doTest(
    s"""case class SomeCaseClass(foo: Int, bar: String, baz: String)
       |
       |(null: Option[SomeCaseClass]) match {
       |  case Some(SomeCaseClass(foo$CARET, bar, _)) =>
       |}
       |""".stripMargin,
    s"""case class SomeCaseClass(foo: Int, bar: String, baz: String)
       |
       |(null: Option[SomeCaseClass]) match {
       |  case Some(SomeCaseClass(foo$CARET: Int, bar, _)) =>
       |}
       |""".stripMargin,
  )

  def testAddTypeAnnotationToBindingPattern_InValDefinition(): Unit = doTest(
    s"""val (v1, ${CARET}v2) = (1, "42")""".stripMargin,
    s"""val (v1, ${CARET}v2: String) = (1, "42")""".stripMargin,
  )

  def testRemoveTypeAnnotationFromBindingPattern(): Unit = doTest(
    s"""Seq(1, 2).map { case ${CARET}x: Int => "42" }""",
    s"""Seq(1, 2).map { case ${CARET}x => "42" }""",
  )

  def testRemoveTypeAnnotationFromBindingPattern_Nested(): Unit = doTest(
    s"""case class SomeCaseClass(foo: Int, bar: String, baz: String)
       |
       |(null: Option[SomeCaseClass]) match {
       |  case Some(SomeCaseClass(${CARET}foo: Int, bar, _)) =>
       |}
       |""".stripMargin,
    s"""case class SomeCaseClass(foo: Int, bar: String, baz: String)
       |
       |(null: Option[SomeCaseClass]) match {
       |  case Some(SomeCaseClass(${CARET}foo, bar, _)) =>
       |}
       |""".stripMargin,
  )

  def testRemoveTypeAnnotationFromBindingPattern_Nested_CaretBeforeComma(): Unit = doTest(
    s"""case class SomeCaseClass(foo: Int, bar: String, baz: String)
       |
       |(null: Option[SomeCaseClass]) match {
       |  case Some(SomeCaseClass(foo$CARET: Int, bar, _)) =>
       |}
       |""".stripMargin,
    s"""case class SomeCaseClass(foo: Int, bar: String, baz: String)
       |
       |(null: Option[SomeCaseClass]) match {
       |  case Some(SomeCaseClass(foo$CARET, bar, _)) =>
       |}
       |""".stripMargin,
  )

  def testRemoveTypeAnnotationFromBindingPattern_InValDefinition(): Unit = doTest(
    s"""val (v1, ${CARET}v2: String) = (1, "42")""".stripMargin,
    s"""val (v1, ${CARET}v2) = (1, "42")""".stripMargin,
  )

  def testAddTypeAnnotationToWildcardPattern(): Unit = doTest(
    s"""Seq(1, 2).map { case ${CARET}_ => "42" }""",
    s"""Seq(1, 2).map { case ${CARET}_: Int => "42" }""",
  )

  def testAddTypeAnnotationToWildcardPattern_Nested(): Unit = doTest(
    s"""case class SomeCaseClass(foo: Int, bar: String, baz: String)
       |
       |(null: Option[SomeCaseClass]) match {
       |  case Some(SomeCaseClass(${CARET}_, bar, _)) =>
       |}
       |""".stripMargin,
    s"""case class SomeCaseClass(foo: Int, bar: String, baz: String)
       |
       |(null: Option[SomeCaseClass]) match {
       |  case Some(SomeCaseClass(${CARET}_: Int, bar, _)) =>
       |}
       |""".stripMargin,
  )

  def testAddTypeAnnotationToWildcardPattern_Nested_CaretBeforeComma(): Unit = doTest(
    s"""case class SomeCaseClass(foo: Int, bar: String, baz: String)
       |
       |(null: Option[SomeCaseClass]) match {
       |  case Some(SomeCaseClass(_$CARET, bar, _)) =>
       |}
       |""".stripMargin,
    s"""case class SomeCaseClass(foo: Int, bar: String, baz: String)
       |
       |(null: Option[SomeCaseClass]) match {
       |  case Some(SomeCaseClass(_$CARET: Int, bar, _)) =>
       |}
       |""".stripMargin,
  )

  def testAddTypeAnnotationToWildcardPattern_InValDefinition(): Unit = doTest(
    s"""val (v1, ${CARET}_) = (1, "42")""".stripMargin,
    s"""val (v1, ${CARET}_: String) = (1, "42")""".stripMargin,
  )

  def testRemoveTypeAnnotationFromWildcardPattern(): Unit = doTest(
    s"""Seq(1, 2).map { case ${CARET}_: Int => "42" }""",
    s"""Seq(1, 2).map { case ${CARET}_ => "42" }""",
  )

  def testRemoveTypeAnnotationFromWildcardPattern_Nested(): Unit = doTest(
    s"""case class SomeCaseClass(foo: Int, bar: String, baz: String)
       |
       |(null: Option[SomeCaseClass]) match {
       |  case Some(SomeCaseClass(${CARET}_: Int, bar, _)) =>
       |}
       |""".stripMargin,
    s"""case class SomeCaseClass(foo: Int, bar: String, baz: String)
       |
       |(null: Option[SomeCaseClass]) match {
       |  case Some(SomeCaseClass(${CARET}_, bar, _)) =>
       |}
       |""".stripMargin,
  )

  def testRemoveTypeAnnotationFromWildcardPattern_Nested_CaretBeforeComma(): Unit = doTest(
    s"""case class SomeCaseClass(foo: Int, bar: String, baz: String)
       |
       |(null: Option[SomeCaseClass]) match {
       |  case Some(SomeCaseClass(_$CARET: Int, bar, _)) =>
       |}
       |""".stripMargin,
    s"""case class SomeCaseClass(foo: Int, bar: String, baz: String)
       |
       |(null: Option[SomeCaseClass]) match {
       |  case Some(SomeCaseClass(_$CARET, bar, _)) =>
       |}
       |""".stripMargin,
  )

  def testRemoveTypeAnnotationFromWildcardPattern_InValDefinition(): Unit = doTest(
    s"""val (v1, ${CARET}v2: String) = (1, "42")""".stripMargin,
    s"""val (v1, ${CARET}v2) = (1, "42")""".stripMargin,
  )

  def testTypeAliasInRefinementWithPotentialNameCollision_CompoundTypeWithRefinement(): Unit = doTest(
    s"""object Parsers {
       |  trait Builder
       |
       |
       |  val value1$CARET = (??? : {
       |    type Builder = Parsers.Builder
       |  })
       |}
       |""".stripMargin,
    s"""object Parsers {
       |  trait Builder
       |
       |
       |  val value1$CARET: {type Builder = Parsers.Builder} = (??? : {
       |    type Builder = Parsers.Builder
       |  })
       |}
       |""".stripMargin
  )

  // Overriding a member of a parent with the very same type adds nothing to the parent, so the
  // refinement of the approximated anonymous class doesn't keep it in Scala 3.
  // Whether it is kept isn't observable by conformance, which is why this is tested here rather than in
  // [[org.jetbrains.plugins.scala.lang.typeInference.RefinementApproximationTest]].
  // See `TypeOps.classBound` in the Scala 3 compiler.
  def testRefinementOfEquallyTypedParentMember(): Unit = {
    val annotation =
      if (version.isScala2) "Object {def toString(): String}"
      else                  "Object"

    doTest(
      s"""object Test {
         |  val value$CARET = new Object {
         |    override def toString(): String = "test"
         |  }
         |}
         |""".stripMargin,
      s"""object Test {
         |  val value$CARET: $annotation = new Object {
         |    override def toString(): String = "test"
         |  }
         |}
         |""".stripMargin
    )
  }

  // Narrowing a member of a parent, `Object.toString` in this case, does add something to it,
  // so the refinement keeps the member in both versions.
  def testRefinementOfNarrowedParentMember_NewTemplateDefinition(): Unit = doTest(
    s"""object Test {
       |  val value$CARET = new Object {
       |    override def toString(): "test" = "test"
       |  }
       |}
       |""".stripMargin,
    s"""object Test {
       |  val value$CARET: Object {def toString(): "test"} = new Object {
       |    override def toString(): "test" = "test"
       |  }
       |}
       |""".stripMargin
  )

  // Scala 3 approximates an anonymous class by its parents and only keeps the members they already
  // declare, and `AnyRef` doesn't declare a `Builder`, so the refinement is dropped. The name collision
  // this test is about is still covered for both versions by the explicit refinement above.
  // See `TypeOps.classBound` in the Scala 3 compiler.
  def testTypeAliasInRefinementWithPotentialNameCollision_NewTemplateDefinition(): Unit = {
    val annotation =
      if (version.isScala2) "Object {type Builder = Parsers.Builder}"
      else                  "AnyRef"

    doTest(
      s"""object Parsers {
         |  trait Builder
         |
         |  val value2$CARET = new AnyRef {
         |    type Builder = Parsers.Builder
         |  }
         |}
         |""".stripMargin,
      s"""object Parsers {
         |  trait Builder
         |
         |  val value2$CARET: $annotation = new AnyRef {
         |    type Builder = Parsers.Builder
         |  }
         |}
         |""".stripMargin
    )
  }
}
