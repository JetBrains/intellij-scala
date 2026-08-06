package org.jetbrains.plugins.scala.lang.implicits

import com.intellij.testFramework.EditorTestUtil._
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
         |    echo($START"sss"$END)
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

  def testJavaRawStackOverflowSCL19526(): Unit = {
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
        |    ${START}x.extension.str()$END
        |  }
        |  class M[A] { def a: A = ??? }
        |  object M {
        |    implicit def richM[A](ma: M[A]): { def extension: A } = ???
        |  }
        |}
        |// String
        |""".stripMargin)
  }

  // NOTE: the StackOverflowError in SCL-24428 is actually reproduced during completion, not resolution
  // but I decided to add this test as well
  def testJavaRawStackOverflowSCL24428(): Unit = {
    addFileToProject("JavaRaw.java",
      """package java_raw;
        |
        |import java.lang.Comparable;
        |
        |interface ProcessorDefinition00<T extends ProcessorDefinition0> { }
        |abstract class ProcessorDefinition0<T extends ProcessorDefinition0<T>> implements ProcessorDefinition00 { abstract String foo0();}
        |abstract class ProcessorDefinition1<T extends ProcessorDefinition1<T>> implements Comparable<ProcessorDefinition1> { abstract int foo1();}
        |abstract class ProcessorDefinition2<T extends ProcessorDefinition2<T>> implements Comparable<Comparable<ProcessorDefinition2>> { abstract long foo2();}
        |abstract class ProcessorDefinition3<T extends ProcessorDefinition3<T>> implements Comparable<ProcessorDefinition3<?>> { abstract short foo3();}
        |""".stripMargin)

    configureFromFileText(
      "Example.scala",
      s"""package java_raw
         |
         |class Example {
         |  private def foo(): Unit = {
         |    val value0: ProcessorDefinition0[_] = ???
         |    val value1: ProcessorDefinition1[_] = ???
         |    val value2: ProcessorDefinition2[_] = ???
         |    val value3: ProcessorDefinition3[_] = ???
         |
         |    $SELECTION_START_TAG${CARET}value0.foo0()$SELECTION_END_TAG
         |    $SELECTION_START_TAG${CARET}value1.foo1()$SELECTION_END_TAG
         |    $SELECTION_START_TAG${CARET}value2.foo2()$SELECTION_END_TAG
         |    $SELECTION_START_TAG${CARET}value3.foo3()$SELECTION_END_TAG
         |
         |    ???
         |  }
         |}
         |""".stripMargin)

    typeInferenceFixture.assertTypeAtSelectionIndex(getFile, getEditor, 0, "String")
    typeInferenceFixture.assertTypeAtSelectionIndex(getFile, getEditor, 1, "Int")
    typeInferenceFixture.assertTypeAtSelectionIndex(getFile, getEditor, 2, "Long")
    typeInferenceFixture.assertTypeAtSelectionIndex(getFile, getEditor, 3, "Short")
  }

  def testSCL10141(): Unit = {
    doTest(
      s"""
         |case class BuildInfoResult(identifier: String, value: Any, typeExpr: Any)
         |
         |object BuildInfo {
         |
         |  case class BuildInfoTask() {
         |
         |    def entry[A](info: BuildInfoKey.Entry[A]): Option[BuildInfoResult] = {
         |      val typeExpr: Any = ???
         |      val result: Option[(String, A)] = info match {
         |        case BuildInfoKey.Mapped(from, fun) =>
         |        ${START}fun$END
         |        entry(from).map { r => fun(r.identifier -> r.value.asInstanceOf[A]) }
         |      }
         |      result.map(r => BuildInfoResult(r._1, r._2, typeExpr))
         |    }
         |  }
         |}
         |
         |object BuildInfoKey {
         |  case class Mapped[A, B](from: Entry[A], fun: ((String, A)) => (String, B))(implicit val manifest: Manifest[B]) extends Entry[B]
         |  sealed trait Entry[A] { def manifest: Manifest[A] }
         |}
         |//((String, Any)) => (String, A)
      """.stripMargin)
  }
}