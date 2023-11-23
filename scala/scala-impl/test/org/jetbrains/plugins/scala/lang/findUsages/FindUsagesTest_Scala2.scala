package org.jetbrains.plugins.scala.lang.findUsages

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.findUsages.factory.ScalaFindUsagesConfiguration

class FindUsagesTest_Scala2 extends FindUsagesTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  def testFindObjectWithMembers(): Unit = {
    ScalaFindUsagesConfiguration.getInstance(getProject).getTypeDefinitionOptions.isMembersUsages = true

    doTest(
      s"""object ${CARET}Test {
         |  def foo() = ???
         |
         |  ${start}Test$end.${start}foo$end
         |}
         |""".stripMargin,
    )
  }

  def testUnaryOperator(): Unit = doTest(
    s"""class B {
       |  def ${CARET}unary_! : B = this
       |}
       |
       |object Test {
       |  val b = new B
       |  $start!${end}b
       |  b.${start}unary_!$end
       |  b.${start}unary_$$bang$end
       |}
       |""".stripMargin)

  def testFindClassInNewInstanceCreationAndInheritance(): Unit = doTest(
    s"""trait MyTrait1
       |trait MyTrait2
       |class ${CARET}MyClass extends MyTrait1 with MyTrait2
       |
       |class ScalaExtendsImplements {
       |  //new instance creation
       |  new ${start}MyClass$end with MyTrait1 with MyTrait2
       |  new ${start}MyClass$end
       |
       |  //new anonymous class instance creation
       |  new ${start}MyClass$end with MyTrait1 with MyTrait2 {}
       |  new ${start}MyClass$end {}
       |
       |  //inheritance
       |  class MyChildClass1 extends ${start}MyClass$end with MyTrait1 with MyTrait2
       |  class MyChildClass2 extends ${start}MyClass$end
       |}""".stripMargin
  )

  //NOTE: this test covers current behaviour
  //It might not reflect the 100% desired behaviour
  //(e.g. maybe in the future we decide that find usage on class should also show apply methods invocations)
  def testClassWithCompanionWithApplyMethod(): Unit = doTest(
    s"""class ${CARET}MyClassWithCompanionApplyMethod
       |object MyClassWithCompanionApplyMethod {
       |  def apply(x: Int): ${start}MyClassWithCompanionApplyMethod$end = ???
       |}
       |
       |new ${start}MyClassWithCompanionApplyMethod$end
       |MyClassWithCompanionApplyMethod(42)
       |""".stripMargin
  )

  //NOTE: this test covers current behaviour
  //It might not reflect the 100% desired behaviour
  //(e.g. maybe in the future we decide that find usage on class should also show apply methods invocations)
  def testCaseClassWithCompanionWithApplyMethod(): Unit = doTest(
    s"""case class ${CARET}MyCaseClassWithCompanionApplyMethod(x: Int)
       |object MyCaseClassWithCompanionApplyMethod {
       |  def apply(x: Int): ${start}MyCaseClassWithCompanionApplyMethod$end = ???
       |  def apply(x: String): ${start}MyCaseClassWithCompanionApplyMethod$end = ???
       |}
       |
       |new ${start}MyCaseClassWithCompanionApplyMethod$end(42)
       |MyCaseClassWithCompanionApplyMethod(42)
       |MyCaseClassWithCompanionApplyMethod("42")
       |""".stripMargin
  )

  //NOTE: this test covers current behaviour
  //It might not reflect the 100% desired behaviour
  //(e.g. maybe in the future we decide that find usage on class should also show apply methods invocations)
  def testCaseClass(): Unit = doTest(
    s"""case class ${CARET}MyCaseClass(x: Int)
       |
       |new ${start}MyCaseClass$end(42)
       |${start}MyCaseClass$end(42)
       |""".stripMargin
  )

  def testClassWithMultipleConstructors_FindFromDefinition(): Unit = doTest(
    s"""class ${CARET}MyClass(s: String) {
       |  def this(x: Int) = this(x.toString)
       |  def this(x: Short) = this(x.toInt)
       |}
       |new ${start}MyClass$end("test1")
       |new ${start}MyClass$end("test2")
       |new ${start}MyClass$end(42)
       |new ${start}MyClass$end(23)
       |val x: ${start}MyClass$end = ???
       |""".stripMargin
  )

  def testClassWithMultipleConstructors_FromPrimaryConstructorInvocation(): Unit = doTest(
    s"""class MyClass(s: String) {
       |  def this(x: Int) = ${start}this$end(x.toString)
       |  def this(x: Short) = this(x.toInt)
       |}
       |new $CARET${start}MyClass$end("test1")
       |new ${start}MyClass$end("test2")
       |new MyClass(42)
       |new MyClass(23)
       |val x: MyClass = ???
       |""".stripMargin
  )

  def testClassWithMultipleConstructors_FromPrimaryConstructorInvocation_WithEmptyParameters(): Unit = doTest(
    s"""class MyClass {
       |  def this(x: Int) = this(x.toString)
       |  def this(x: Short) = this(x.toInt)
       |}
       |new $CARET${start}MyClass$end
       |new ${start}MyClass$end()
       |new MyClass(42)
       |new MyClass(23)
       |val x: MyClass = ???
       |""".stripMargin
  )

  def testClassWithMultipleConstructors_FromSecondaryConstructorInvocation(): Unit = doTest(
    s"""class MyClass(s: String) {
       |  def this(x: Int) = this(x.toString)
       |  def this(x: Short) = ${start}this$end(x.toInt)
       |}
       |
       |new MyClass("test1")
       |new MyClass("test2")
       |new $CARET${start}MyClass$end(42)
       |new ${start}MyClass$end(23)
       |val x: MyClass = ???
       |""".stripMargin
  )

  def testFindTypeOverriders(): Unit = {
    doTest(
      s"""trait FindMyMembers {
         |  type MyType$CARET
         |  def methodInTrait(): Unit = {
         |    val x: ${start}MyType$end = ???
         |  }
         |}
         |
         |class FindMyMembersImpl extends FindMyMembers {
         |  override type MyType = String
         |
         |  def methodInImpl(): Unit = {
         |    val x: ${start}MyType$end
         |  }
         |}
         |""".stripMargin)
  }

  def testFindUsagesOfValOverriders(): Unit = {
    doTest(
      s"""
         |trait FindMyMembers {
         |  val findMyVal$CARET: Int
         |  def methodInTrait(): Unit = {
         |    println(${start}findMyVal$end)
         |  }
         |}
         |
         |class FindMyMembersImpl extends FindMyMembers {
         |  override val findMyVal: Int = 1
         |
         |  def methodInImpl(): Unit = {
         |    println(${start}findMyVal$end)
         |  }
         |}
    """.stripMargin)
  }

  def testFindUsagesOfMethodOverriders(): Unit = doTest(
    s"""class BaseClass {
       |  def ${CARET}foo(): String = ???
       |  def foo(x: Int): String = ???
       |}
       |
       |class ChildClass extends BaseClass {
       |  override def foo(): String = ???
       |  override def foo(x: Int): String = ???
       |}
       |
       |object Usage {
       |  val base: BaseClass = ???
       |  val child: ChildClass = ???
       |
       |  base.${start}foo$end()
       |  base.foo(42)
       |
       |  child.${start}foo$end()
       |  child.foo(42)
       |}""".stripMargin
  )

  def testFindUsagesOfMethodOverriders_Complex(): Unit = doTest(
    s"""class BaseClass[T <: AnyRef] {
       |  def ${CARET}foo[E1, E2 <: T](p1: T, p2: E1, p3: E2)
       |                      (implicit x: Long, y: T): Tuple2[T, E1] = ???
       |}
       |
       |class ChildClass extends BaseClass[String] {
       |  override def foo[E1, E2 <: String](p1: String, p2: E1, p3: E2)
       |                                    (implicit x: Long, y: String): (String, E1) = ???
       |}
       |
       |object Usage {
       |  implicit val x: Long = ???
       |  implicit val y: String = ???
       |
       |  val base: BaseClass[AnyRef] = ???
       |  val child: ChildClass = ???
       |
       |  base.${start}foo$end(???, ???, ???)
       |  child.${start}foo$end(???, ???, ???)
       |}
       |""".stripMargin
  )

  def testFindUsagesOfMethodOverriders_OverrideByVal(): Unit = doTest(
    s"""class BaseClass {
       |  def ${CARET}bar: String = ???
       |}
       |
       |class ChildClass extends BaseClass {
       |  override val bar: String = ???
       |}
       |
       |object Usage {
       |  val base: BaseClass = ???
       |  val child: ChildClass = ???
       |
       |  base.${start}bar$end
       |  child.${start}bar$end
       |}""".stripMargin
  )
}
