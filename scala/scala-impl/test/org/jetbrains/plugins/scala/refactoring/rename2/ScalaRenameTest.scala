package org.jetbrains.plugins.scala.refactoring.rename2

/**
 * User: Alefas
 * Date: 04.10.11
 */
class ScalaRenameTest extends ScalaRenameTestBase {
  def testRenameBeanProperty(): Unit = doRenameTest("y",
    """import reflect.BeanProperty
      |object X {
      |  @BeanProperty
      |  val x<caret> = 1
      |
      |  getX()
      |}
      |""",
    """
      |import reflect.BeanProperty
      |object X {
      |  @BeanProperty
      |  val y<caret> = 1
      |
      |  getY()
      |}
      """
  )

  def testRenameBooleanBeanProperty(): Unit = doRenameTest("y",
    """
      |import reflect.BooleanBeanProperty
      |object X {
      |  @BooleanBeanProperty
      |  val x<caret> = 1
      |
      |  isX()
      |}""",
    """
      |import reflect.BooleanBeanProperty
      |object X {
      |  @BooleanBeanProperty
      |  val y<caret> = 1
      |
      |  isY()
      |}"""
  )

  def testRenameBeanVarProperty(): Unit = doRenameTest("y",
    """
      |import reflect.BeanProperty
      |object X {
      |  @BeanProperty
      |  var x<caret> = 1
      |
      |  getX()
      |  setX(2)
      |}""",
    """
      |import reflect.BeanProperty
      |object X {
      |  @BeanProperty
      |  var y<caret> = 1
      |
      |  getY()
      |  setY(2)
      |}
      """
  )

  def testRenameBooleanBeanVarProperty(): Unit = doRenameTest("y",
    """
      |import reflect.BooleanBeanProperty
      |object X {
      |  @BooleanBeanProperty
      |  var x<caret> = 1
      |
      |  isX()
      |  setX(2)
      |}
      """,
    """
      |import reflect.BooleanBeanProperty
      |object X {
      |  @BooleanBeanProperty
      |  var y<caret> = 1
      |
      |  isY()
      |  setY(2)
      |}
      """
  )

  def testRenameNamingParameter(): Unit = doRenameTest("y",
    """
      |class Check {
      |  def method(<caret>attrib: String) = {
      |    CaseClass2(attrib = attrib)
      |  }
      |}
      |case class CaseClass2(attrib: String) {}
      """,
    """
      |class Check {
      |  def method(y: String) = {
      |    CaseClass2(attrib = y)
      |  }
      |}
      |case class CaseClass2(attrib: String) {}
      """
  )

  def testRenameCaseClass(): Unit = doRenameTest("Inde",
    """
      |class A {
      |
      |  case class Index()
      |
      |  Index() match {
      |    case Index() =>
      |  }
      |}
      |
      |class B {
      |
      |  case class Index()
      |
      |  Index() match {
      |    case <caret>Index() =>
      |  }
      |}
      """,
    """
      |class A {
      |
      |  case class Index()
      |
      |  Index() match {
      |    case Index() =>
      |  }
      |}
      |
      |class B {
      |
      |  case class Inde()
      |
      |  Inde() match {
      |    case Inde() =>
      |  }
      |}
      """
  )

  def testRenameInterpolatedStringPrefix(): Unit = doRenameTest("bbb",
    """
      |object AAA {
      |
      |  class BBB {
      |    def aa<caret>a(a: Any*) = a.length
      |  }
      |
      |  implicit def ctxToB(ctx: StringContext) = new BBB
      |
      |  val a = aaa"blah blah"
      |}""",
    """
      |object AAA {
      |
      |  class BBB {
      |    def bb<caret>b(a: Any*) = a.length
      |  }
      |
      |  implicit def ctxToB(ctx: StringContext) = new BBB
      |
      |  val a = bbb"blah blah"
      |}"""
  )

  def testObjectToCaseClass(): Unit = doRenameTest("I",
    """
      |object ObjectToCaseClass {
      |
      |  case class Test1(a: Int)
      |
      |  Test1(2)
      |  <caret>Test1.apply(1)
      |}
      """,
    """
      |object ObjectToCaseClass {
      |
      |  case class I(a: Int)
      |
      |  I(2)
      |  <caret>I.apply(1)
      |}
      """
  )

  def testCaseClassConstructor(): Unit = doRenameTest("I",
    """
      |object CaseClassConstructor {
      |
      |  case class Test1(a: Int)
      |
      |  new <caret>Test1(2)
      |  Test1.apply(1)
      |  Test1(1) match {
      |    case Test1(1) =>
      |  }
      |}
      """,
    """
      |object CaseClassConstructor {
      |
      |  case class I(a: Int)
      |
      |  new <caret>I(2)
      |  I.apply(1)
      |  I(1) match {
      |    case I(1) =>
      |  }
      |}
      """
  )

  def testCaseClassApply(): Unit = doRenameTest("I",
    """
      |object CaseClassApply {
      |
      |  case class Test1(a: Int)
      |
      |  new Test1(2)
      |  Test1.apply(1)
      |  <caret>Test1(1) match {
      |    case Test1(1) =>
      |  }
      |}
      """,
    """
      |object CaseClassApply {
      |
      |  case class I(a: Int)
      |
      |  new I(2)
      |  I.apply(1)
      |  I(1) match {
      |    case I(1) =>
      |  }
      |}
      """
  )

  def testCaseClassUnapply(): Unit = doRenameTest("I",
    """
      |object CaseClassUnapply {
      |
      |  case class Test1(a: Int)
      |
      |  new Test1(2)
      |  Test1.apply(1)
      |  Test1(1) match {
      |    case <caret>Test1(1) =>
      |  }
      |}
      """,
    """
      |object CaseClassUnapply {
      |
      |  case class I(a: Int)
      |
      |  new I(2)
      |  I.apply(1)
      |  I(1) match {
      |    case I(1) =>
      |  }
      |}
      """
  )

  def testSameNameObjectsApply(): Unit = doRenameTest("newName",
    """
      |trait HasApply {
      |  def apply(str:String) = ""
      |
      |  def unapply(s: String): Option[String] = Some(s)
      |}
      |
      |class CustomerContact1 {
      |  object circus extends HasApply
      |}
      |
      |
      |object CustomerContact2 {
      |  object c<caret>ircus extends HasApply
      |}
      |
      |object FindUsagesBug {
      |  def main(args: Array[String]): Unit = {
      |    CustomerContact2.circus("hello")
      |
      |    val CustomerContact2.circus(s) = "hello"
      |  }
      |}
      """,
    """
      |trait HasApply {
      |  def apply(str:String) = ""
      |
      |  def unapply(s: String): Option[String] = Some(s)
      |}
      |
      |class CustomerContact1 {
      |  object circus extends HasApply
      |}
      |
      |
      |object CustomerContact2 {
      |  object newName extends HasApply
      |}
      |
      |object FindUsagesBug {
      |  def main(args: Array[String]): Unit = {
      |    CustomerContact2.newName("hello")
      |
      |    val CustomerContact2.newName(s) = "hello"
      |  }
      |}
      """
  )

  def testSameNameObjectsApply2(): Unit = doRenameTest("newName",
    """
      |trait HasApply {
      |  def apply(str:String) = ""
      |
      |  def unapply(s: String): Option[String] = Some(s)
      |}
      |
      |class CustomerContact1 {
      |  object c<caret>ircus extends HasApply
      |}
      |
      |
      |object CustomerContact2 {
      |  object circus extends HasApply
      |}
      |
      |object FindUsagesBug {
      |  def main(args: Array[String]): Unit = {
      |    CustomerContact2.circus("hello")
      |
      |    val CustomerContact2.circus(s) = "hello"
      |  }
      |}
      """,
    """
      |trait HasApply {
      |  def apply(str:String) = ""
      |
      |  def unapply(s: String): Option[String] = Some(s)
      |}
      |
      |class CustomerContact1 {
      |  object newName extends HasApply
      |}
      |
      |
      |object CustomerContact2 {
      |  object circus extends HasApply
      |}
      |
      |object FindUsagesBug {
      |  def main(args: Array[String]): Unit = {
      |    CustomerContact2.circus("hello")
      |
      |    val CustomerContact2.circus(s) = "hello"
      |  }
      |}
      """
  )

  def testApplyDefinition(): Unit = doRenameTest("BBB",
    """object AAA {
      |  def <caret>apply(i: Int): Int = 123
      |}
      |object B {
      |  val a = AAA(123)
      |}
      |""",
    """object BBB {
      |  def apply(i: Int): Int = 123
      |}
      |object B {
      |  val a = BBB(123)
      |}
      |"""
  )

}