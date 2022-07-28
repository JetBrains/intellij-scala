package org.jetbrains.plugins.scala.lang.implicits

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

class ImplicitsTest extends TypeInferenceTestBase {

  def testSCL7955(): Unit = doTest(
    s"""
       |class A[-T]
       |class B
       |trait C
       |trait D
       |trait E extends C with D
       |implicit val c: C = new C {}
       |implicit def d[T <: D]: T = sys.exit()
       |implicit def a[T](a: A[T])(implicit t: T): B = new B
       |val b: B = ${START}new A[E]$END
       |def foo(implicit z: A[E] => B) = 123
       |foo
       |//B
       |""".stripMargin
  )

  def testSCL13205(): Unit = {
    doTest(
      s"""
         |import scala.language.implicitConversions
         |
         |case class Echo(s:String)
         |
         |trait Echos {
         |  implicit def string(s:String):Echo = Echo(s)
         |  def echo(e:Echo):Unit
         |}
         |
         |object Test {
         |  def test3(E:Echos) = {
         |    import E.{string=>_, _}
         |    implicit def string1(s:String):Echo = Echo(s+" --- Custom implicit conversion")
         |    // works, but IDEA doesn't recognize
         |    echo(${START}"sss"$END)
         |  }
         |}
         |//Echo
      """.stripMargin)
  }

  def testSCL14535(): Unit = {
    doTest(
      s"""
         |object Repro {
         |  object Builder {
         |    class Step2[P, S]
         |    class Step3[P, S, B] {
         |      def run(): this.type = this
         |    }
         |    implicit def step2ToStep3[X, P, S](b: X)(implicit ev: X => Step2[P, S]): Step3[P, S, Unit] = new Step3[P, S, Unit]
         |  }
         |  val step2 = new Builder.Step2[String, Double]
         |
         |  ${START}step2.run()$END
         |}
         |//Repro.Builder.Step3[String, Double, Unit]
       """.stripMargin
    )
  }

  def testSCL7809(): Unit = doTest {
    """
      |class SCL7809 {
      |  implicit def longToString(s: Long): String = s.toString
      |  def useString(s: String) = s
      |  def useString(d: Boolean) = d
      |  /*start*/useString(1)/*end*/
      |}
      |//String
    """.stripMargin.trim
  }

  def testJavaRawStackOverflowSCL19526: Unit = {
    addFileToProject("JavaRaw.java",
      """
        |public class JavaRaw {
        |    public interface ResultKey<K, P extends ResultKey> { public String str(); }
        |    public interface TypedEnum<K, P extends ResultKey> implements ResultKey<K, P> {}
        |    public interface CalculationEnum<K> extends TypedEnum<K, Column> {}
        |    public interface Column<V extends String> extends ResultKey<V, Column> {}
        |}
        |""".stripMargin)
    doTest(
      s"""
        |class SCL19526 {
        |  def javaRaw1(x: M[JavaRaw.CalculationEnum[_]]): Unit = {
        |    ${START}x.extension.str()${END}
        |  }
        |  class M[A] { def a: A = ??? }
        |  object M {
        |    implicit def richM[A](ma: M[A]): { def extension: A } = ???
        |  }
        |}
        |// String
        |""".stripMargin)
  }
}