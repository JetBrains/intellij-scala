package org.jetbrains.plugins.scala.lang.psi.api.statements

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.junit.Assert.assertTrue

class ScParametersTest extends ScalaFixtureTestCase {

  private val TestClassName = "Foo"

  private def assertJvmFacingParameterCountAndType(methodName: String, expectedTypes: Seq[String]): Unit = {
    val clazz = ScalaPsiManager.instance(getProject).getClassesByName(TestClassName, GlobalSearchScope.fileScope(getFile)).head.asInstanceOf[ScClass]

    val method = clazz.functions.filter(_.name == methodName).head

    val parameters = method.getParameterList.getParameters

    assertTrue(s"Expected ${expectedTypes.size} parameters, but got ${parameters.length} instead", parameters.length == expectedTypes.size)

    parameters.toSeq.zip(expectedTypes).foreach { parameterAndExpectedType =>
      val actual = parameterAndExpectedType._1.getType.getPresentableText
      val expected = parameterAndExpectedType._2
      assertTrue(s"Expected $expected but got $actual instead", actual == expected)
    }
  }

  def test_jvm_facing_parameters_of_method_in_class(): Unit = {
    myFixture.configureByText(s"$TestClassName.scala",
      s"""class $TestClassName {
        |  def bar0(): Unit = ()
        |  def bar1(i: Int): Unit = ()
        |  def bar2(i: Int, s: String): Unit = ()
        |}""".stripMargin)

    assertJvmFacingParameterCountAndType("bar0", Seq.empty)
    assertJvmFacingParameterCountAndType("bar1", Seq("int"))
    assertJvmFacingParameterCountAndType("bar2", Seq("int", "String"))
  }

  def test_jvm_facing_parameters_of_method_in_implicit_class(): Unit = {
    myFixture.configureByText(s"$TestClassName.scala",
      s"""object Scope {
        |  implicit class $TestClassName(d: Double) {
        |    def bar0(): Unit = ()
        |    def bar1(i: Int): Unit = ()
        |    def bar2(i: Int, s: String): Unit = ()
        |  }
        |}""".stripMargin)

    assertJvmFacingParameterCountAndType("bar0", Seq.empty)
    assertJvmFacingParameterCountAndType("bar1", Seq("int"))
    assertJvmFacingParameterCountAndType("bar2", Seq("int", "String"))
  }

  def test_jvm_facing_parameters_of_method_in_implicit_anyval_class(): Unit = {
    myFixture.configureByText(s"$TestClassName.scala",
      s"""object Scope {
        |  implicit class $TestClassName(val d: Double) extends AnyVal {
        |    def bar0(): Unit = ()
        |    def bar1(i: Int): Unit = ()
        |    def bar2(i: Int, s: String): Unit = ()
        |    private def bar3(): Unit = ()
        |    protected def bar4(): Unit = ()
        |  }
        |}""".stripMargin)

    assertJvmFacingParameterCountAndType("bar0", Seq("double"))
    assertJvmFacingParameterCountAndType("bar1", Seq("double", "int"))
    assertJvmFacingParameterCountAndType("bar2", Seq("double", "int", "String"))
    assertJvmFacingParameterCountAndType("bar3", Seq.empty)
    assertJvmFacingParameterCountAndType("bar4", Seq.empty)
  }
}
