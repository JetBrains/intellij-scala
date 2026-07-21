package org.jetbrains.plugins.scala
package lang
package navigation

class GoToImplementationTest extends GoToImplementationTestBase {

  private def doTest(text: String): Unit = doGoToImplementationTest(text)

  def testTraitImplementation(): Unit = doTest(
    s"""
       |trait a {
       |  def f$CARET
       |}
       |trait b extends a {
       |  ${START}def f = 1$END
       |}
       |case class c(${START}override val f: Int = 2$END) extends b
       |case class d() extends b
      """.stripMargin
  )

  def testTraitImplementation2(): Unit = doTest(
    s"""
       |trait a {
       |  def f$CARET = 0
       |}
       |trait b extends a {
       |  ${START}def f = 1$END
       |}
       |case class c(${START}override val f: Int = 2$END) extends b
       |case class d() extends b
      """.stripMargin
  )

  def testAbstractClassImplementation(): Unit = doTest(
    s"""
       |abstract class a {
       |  def f$CARET
       |}
       |trait b extends a {
       |  ${START}def f = 1$END
       |}
       |case class c(${START}override val f: Int = 2$END) extends b
       |case class d() extends b
      """.stripMargin
  )

  def testAbstractClassImplementation2(): Unit = doTest(
    s"""
       |abstract class a {
       |  def f$CARET = 0
       |}
       |trait b extends a {
       |  ${START}def f = 1$END
       |}
       |case class c(${START}override val f: Int = 2$END) extends b
       |case class d() extends b
      """.stripMargin
  )

  def testAbstractOverride(): Unit = doTest(
    s"""
       |abstract class Writer {
       |  def pri${CARET}nt(str: String)
       |}
       |
       |class ConsoleWriter extends Writer {
       |  ${START}def print(str: String) = println(str)$END
       |}
       |
       |trait Uppercase extends Writer {
       |  ${START}abstract override def print(str: String) =
       |    super.print(str.toUpperCase())$END
       |}
       |
       |object Test {
       |  val writer = new ConsoleWriter with Uppercase
       |  writer.print("abc")
       |}
       |""".stripMargin
  )

  def testOverrideWithoutImplementationFromCall(): Unit = doTest(
    s"""
       |trait a {
       |  def f
       |}
       |
       |trait b extends a {
       |  override def f: Int
       |}
       |
       |class c extends b {
       |  ${START}override def f = 1$END
       |}
       |
       |object test {
       |  val x = new c()
       |  println(x.${CARET}f)
       |}
      """.stripMargin
  )

  def testAbstractMethodInTraitFromCall(): Unit = doTest(
    s"""
       |trait DeleteMe {
       |    def hello(): Unit
       |}
       |
       |class DeleteMeImpl extends DeleteMe {
       |    ${START}override def hello(): Unit = println("Hello")$END
       |}
       |
       |object DeleteMe {
       |    def main(args: Array[String]): Unit = {
       |        val deleteMe: DeleteMe = new DeleteMeImpl
       |        deleteMe.hel${CARET}lo()
       |    }
       |}
      """.stripMargin
  )

  def testMethodInTraitFromCall(): Unit = doTest(
    s"""
       |trait DeleteMe {
       |    ${START}def hello(): Unit = {}$END
       |}
       |
       |class DeleteMeImpl extends DeleteMe {
       |    ${START}override def hello(): Unit = println("Hello")$END
       |}
       |
       |object DeleteMe {
       |    def main(args: Array[String]): Unit = {
       |        val deleteMe: DeleteMe = new DeleteMeImpl
       |        deleteMe.hel${CARET}lo()
       |    }
       |}
      """.stripMargin
  )

  def testAbstractMethodInAbstractClassFromCall(): Unit = doTest(
    s"""
       |abstract class DeleteMe {
       |    def hello(): Unit
       |}
       |
       |class DeleteMeImpl extends DeleteMe {
       |    ${START}override def hello(): Unit = println("Hello")$END
       |}
       |
       |object DeleteMe {
       |    def main(args: Array[String]): Unit = {
       |        val deleteMe: DeleteMe = new DeleteMeImpl
       |        deleteMe.hel${CARET}lo()
       |    }
       |}
      """.stripMargin
  )

  def testMethodInAbstractClassFromCall(): Unit = doTest(
    s"""
       |abstract class DeleteMe {
       |    ${START}def hello(): Unit = {}$END
       |}
       |
       |class DeleteMeImpl extends DeleteMe {
       |    ${START}override def hello(): Unit = println("Hello")$END
       |}
       |
       |object DeleteMe {
       |    def main(args: Array[String]): Unit = {
       |        val deleteMe: DeleteMe = new DeleteMeImpl
       |        deleteMe.hel${CARET}lo()
       |    }
       |}
      """.stripMargin
  )

  //SCL-25367
  def testSCL25367_SelfTypeOverride_ConcreteClass(): Unit = doTest(
    s"""
       |abstract class AbstractBaseClass {
       |  def submitBtn$CARET: Option[String]
       |}
       |
       |final class ConcreteClass extends AbstractBaseClass with ConcreteClass.Elements
       |
       |object ConcreteClass {
       |  sealed trait Elements {
       |    this: ConcreteClass =>
       |
       |    ${START}override def submitBtn: Option[String] = Some("Submit")$END
       |  }
       |}
      """.stripMargin
  )

  def testSCL25367_SelfTypeOverride_BaseClass(): Unit = doTest(
    s"""
       |abstract class AbstractBaseClass {
       |  def submitBtn$CARET: Option[String]
       |}
       |
       |final class ConcreteClass extends AbstractBaseClass with ConcreteClass.Elements
       |
       |object ConcreteClass {
       |  sealed trait Elements {
       |    this: AbstractBaseClass =>
       |
       |    ${START}override def submitBtn: Option[String] = Some("Submit")$END
       |  }
       |}
      """.stripMargin
  )
}
