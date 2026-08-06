package org.jetbrains.plugins.scala.annotator

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.Message.Error

import scala.jdk.CollectionConverters.ListHasAsScala

/**
 * Contains highlighting tests, for which no better test class was found
 */
class Scala3HighlightingTestsMix extends ScalaHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  override def assertNoErrors(@Language("Scala 3") code: String): Unit =
    assertErrors(code, Nil: _*)

  override protected def messagesFromScalaCode(file: PsiFile): List[Message] = {
    getFixture.openFileInEditor(file.getVirtualFile)

    //using "true" editor highlighting (see TO-DO comment in ScalaHighlightingTestLike)
    val allInfo = getFixture.doHighlighting().asScala.toList
    val errors = allInfo.filter(_.`type`.getSeverity(null) == HighlightSeverity.ERROR)
    errors.map { info => Message.Error(info.getText, info.getDescription) }
  }

  //SCL-21604
  def testAccessCompanionObjectMembersInPresenceOfAnonymousUsingParameterWithCompanionType(): Unit = {
    assertNoErrors(
      s"""type MyClass = Int
         |object MyClass:
         |  def test(): String = ""
         |
         |def foo(using MyClass): Unit = {
         |  summon[MyClass]
         |  MyClass.test()
         |}
         |""".stripMargin
    )
  }

  //SCL-21604, SCL-21321
  def testAccessCompanionObjectMembersInPresenceOfAnonymousUsingParameterWithCompanionType_CompanionObjectUnresolved(): Unit = {
    assertMessagesText(
      """type MyClass = Int
        |
        |def foo(using MyClass): Unit = {
        |  summon[MyClass]
        |  MyClass.test()
        |}
        |""".stripMargin,
      """Error(MyClass,Cannot resolve symbol MyClass)
        |""".stripMargin
    )
  }

  //SCL-21834
  def testMultipleAnonymousParameters(): Unit = {
    assertNoErrors(
      """case class Company(name: String)
        |case class SalesRep(name: String)
        |
        |case class Invoice(customer: String)(using Company, SalesRep):
        |  override def toString = s"${summon[Company].name} / ${summon[SalesRep].name} - Customer: $customer"
        |
        |@main def test(): Unit =
        |  given Company = Company("Big Corp")
        |  given SalesRep = SalesRep("John")
        |  println(Invoice("Peter LTD"))
        |""".stripMargin
    )
  }

  // SCL-21795
  def testSetterWithUsingParameters(): Unit = {
    val code =
      """
        |class Foo {
        |  private var _x = 1
        |  def x(using String): Int = _x
        |  def x_=(y: Int)(using String): Unit = _x = y
        |}
        |
        |object Foo {
        |  def main(args: Array[String]): Unit = {
        |    val foo = Foo()
        |    given String = "foo"
        |    foo.x = 5
        |  }
        |}
        |""".stripMargin

    assertNothing(errorsFromScalaCode(code))
  }


  def testTypeMismatchUnappliedMethod(): Unit = {
    assertMessages(errorsFromScalaCode("given Int = 3; def f(int: Int)(using Int): Boolean = true; val v: Int = f(1)"))(
      Error("f(1)", "Expression of type Boolean doesn't conform to expected type Int")
    )
  }

  def testStdLibPatches(): Unit = assertNothing(errorsFromScalaCode(
    s"""import scala.language.dynamics
       |import _root_.scala.language.dynamics
       |
       |import scala.language.experimental.macros
       |import _root_.scala.language.experimental.macros
       |
       |import scala.language.noAutoTupling
       |import _root_.scala.language.noAutoTupling
       |
       |import scala.language.experimental.namedTypeArguments
       |import _root_.scala.language.experimental.namedTypeArguments""".stripMargin))

  //SCL-23916
  def testSCL23916_Example1(): Unit = {
    assertNoErrors(
      """import scala.deriving.Mirror.ProductOf
        |
        |given Option[String] = ???
        |val _ = summon[Option[String]] //OK
        |val _: Option[String] = summon[Option[String]] //OK
        |
        |case class MyClass(p1: String, p2: String)
        |val _ = summon[ProductOf[MyClass]] //OK
        |val _: ProductOf[MyClass] = summon[ProductOf[MyClass]] //BAD
        """.stripMargin
    )
  }

  //SCL-23916
  def testSCL23916_Example2(): Unit = {
    assertNoErrors(
      """case class A(x: Int, y: String)
        |summon[deriving.Mirror.ProductOf[A]].fromProduct(1 -> "a")
        """.stripMargin
    )
  }

  //SCL-23916
  def testSCL23916_Example3_Small(): Unit = {
    assertNoErrors(
      """import scala.deriving.Mirror.ProductOf
        |
        |trait ToTuple[E, T] extends (E => Option[T])
        |
        |case class MyClass(p1: String, p2: String)
        |
        |object usage {
        |  def productToTuple[T <: Product](using m: ProductOf[T]): ToTuple[T, m.MirroredElemTypes] = ???
        |  val toTuple1: ToTuple[MyClass, (String, String)] = productToTuple[MyClass]
        |}
        |""".stripMargin
    )
  }

//  //TODO: still doesn't work in 2026.1 (depends on SCL-20111/SCL-24637)
//  //SCL-23916
//  def testSCL23916_Example3_Big(): Unit = {
//    assertNoErrors(
//      """import scala.deriving.Mirror.ProductOf
//        |
//        |trait ToTuple[E, T] extends (E => Option[T])
//        |
//        |object ToTuple {
//        |  implicit def productToTuple[T <: Product](using m: ProductOf[T]): ToTuple[T, m.MirroredElemTypes] = ???
//        |}
//        |
//        |object usage {
//        |  case class MyClass(p1: String, p2: String)
//        |
//        |  // OK
//        |  summon[ProductOf[MyClass]]
//        |
//        |  // BAD
//        |  summon[ToTuple[MyClass, (String, String)]]
//        |  summon[ToTuple[MyClass, (String, String)]](using ToTuple.productToTuple(using summon[ProductOf[MyClass]]))
//        |
//        |  // OK
//        |  {
//        |    given toTuple: ToTuple[MyClass, (String, String)] = ???
//        |
//        |    summon[ToTuple[MyClass, (String, String)]](using toTuple)
//        |    summon[ToTuple[MyClass, (String, String)]]
//        |  }
//        |
//        |  // BAD
//        |  {
//        |    val productOf: ProductOf[MyClass] = ???
//        |
//        |    //Type mismatch.
//        |    //Required  : ToTuple[MyClass, productOf.MirroredElemTypes]
//        |    //Found     : ToTuple[MyClass, ProductOf[MyClass]#MirroredElemTypes ]
//        |    given toTuple: ToTuple[MyClass, productOf.MirroredElemTypes] = ToTuple.productToTuple[MyClass](using productOf)
//        |
//        |    summon[ToTuple[MyClass, productOf.MirroredElemTypes]](using toTuple)
//        |    summon[ToTuple[MyClass, productOf.MirroredElemTypes]]
//        |  }
//        |
//        |  // BAD
//        |  {
//        |    //Type mismatch.
//        |    //Required : ToTuple [MyClass, (String, String)]
//        |    //Found    : ToTuple [MyClass, m.MirroredElemTypes]
//        |    given toTuple: ToTuple[MyClass, (String, String)] = ToTuple.productToTuple[MyClass]
//        |
//        |    summon[ToTuple[MyClass, (String, String)]](using toTuple)
//        |    summon[ToTuple[MyClass, (String, String)]]
//        |  }
//        |}
//        """.stripMargin
//    )
//  }

  def testSCL23916_Example4(): Unit = assertNoErrors(
    """import scala.deriving.Mirror
      |
      |case class LoginData(email: String, age: Int)
      |def to[A <: Product](value: A)(using mirror: Mirror.ProductOf[A]): Option[mirror.MirroredElemTypes] = ???
      |val _: LoginData => Option[(String, Int)] = x => to[LoginData](x)
      |val _: LoginData => Option[(String, Int)] = to[LoginData]
      |""".stripMargin
  )
}
