package scala.meta.annotations

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.junit.Assert

class MetaAnnotationBugsTest extends MetaAnnotationTestBase {
  def testSCL10965(): Unit = {
    compileMetaSource()
    myFixture.configureByText("Foo.scala",
      """
        |@repro sealed trait FooOp[A]
        |object FooOp {
        |  final case class StringOp(string: String) extends FooOp[String]
        |  final case class AOp[A](a: A) extends FooOp[A]
        |}
      """.stripMargin
    )
    val expectedExpansion =
      """{
        |  sealed trait FooOp[A]
        |  object FooOp {
        |    trait ForF[F[_]] {
        |      def stringOp(string: String): F[String]
        |      def aOp[A](a: A): F[A]
        |    }
        |    final case class StringOp(string: String) extends FooOp[String]()
        |    final case class AOp[A](a: A) extends FooOp[A]()
        |  }
        |}""".stripMargin
    myFixture.findClass("FooOp").asInstanceOf[ScTypeDefinition].getMetaExpansion match {
      case Right(tree)                      => Assert.assertEquals(expectedExpansion, tree.toString())
      case Left(reason) if reason.nonEmpty  => Assert.fail(reason)
      case Left("")                         => Assert.fail("Expansion was empty - did annotation even run?")
    }
  }

  def testSCL11099(): Unit = {
    compileMetaSource()
    val code =
      """
        |object App {
        |  @poly def <caret>fooOpToId[A](fooOp: FooOp[A]): Id[A] = fooOp match {
        |    case StringOp(string) => Right(string)
        |    case AOp(a) => Left(())
        |  }
        |}""".stripMargin
    val expansion =
      """
        |val fooOpToId: _root_.cats.arrow.FunctionK[FooOp, Id] = new _root_.cats.arrow.FunctionK[FooOp, Id] {
        |  def apply[A](fooOp: FooOp[A]): Id[A] = fooOp match {
        |    case StringOp(string) => Right(string)
        |    case AOp(a) => Left(())
        |  }
        |}""".stripMargin.trim
    checkExpansionEquals(code, expansion)
  }
}
