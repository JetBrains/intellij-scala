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

  def testFindValOverriders(): Unit = {
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
         |  }
         |}
      """.stripMargin)
  }

  def testFindDefOverriders(): Unit = {
    doTest(
      s"""
         |trait FindMyMembers {
         |  def findMyDef$CARET: Int
         |  def methodInTrait(): Unit = {
         |    println(${start}findMyDef$end)
         |  }
         |}
         |
         |class FindMyMembersImpl extends FindMyMembers {
         |  override def findMyDef: Int = 1
         |
         |  def methodInImpl(): Unit = {
         |    println(${start}findMyDef$end)
         |  }
         |}
         |
         |class FindMyMembersImpl2 extends FindMyMembers {
         |  override val findMyDef: Int = 2
         |
         |  def methodInImpl2(): Unit = {
         |    println(${start}findMyDef$end)
         |  }
         |}
         |
         |class FindMyMembersImpl3 extends FindMyMembers {
         |  override var findMyDef: Int = 2
         |
         |  def methodInImpl3(): Unit = {
         |    ${start}findMyDef$end = 3
         |    println(${start}findMyDef$end)
         |  }
         |}
         |
         |class FindMyMembersImpl4 extends FindMyMembers {
         |  override val findMyDef: Int = 2
         |
         |  def methodInImpl4(): Unit = {
         |    ${start}findMyDef$end = 3
         |    println(${start}findMyDef$end)
         |  }
         |}
      """.stripMargin)
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
       |  //new anonimous class instance creation
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
}
