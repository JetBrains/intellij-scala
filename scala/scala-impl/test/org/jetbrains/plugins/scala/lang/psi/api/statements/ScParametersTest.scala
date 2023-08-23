package org.jetbrains.plugins.scala.lang.psi.api.statements

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.junit.Assert.assertTrue

class ScParametersTest extends ScalaFixtureTestCase {

  private val TestClassName = "Foo"

  /**
   * @param methodName    Name of the method for which parameter count and type assertions are to be executed
   * @param expectedTypes Types of the parameters that are expected to be in the method. Expected parameter count equals
   *                      expectedType.length
   *
   * @param jvmFacing     If `true`, the actual parameter list is derived from the generic JVM API, i.e.
   *                      [[org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters.getParameters]].
   *
   *                      If `false`, the actual parameter list is derived from the Scala-specific API, i.e.
   *                      [[org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters.params]].
   */
  private def assertParameterCountAndType(methodName: String, expectedTypes: Seq[String], jvmFacing: Boolean): Unit = {
    val clazz = ScalaPsiManager.instance(getProject).getClassesByName(TestClassName, GlobalSearchScope.fileScope(getFile)).head.asInstanceOf[ScClass]

    val method = clazz.functions.filter(_.name == methodName).head

    val parameters = if (jvmFacing) method.getParameterList.getParameters.toSeq else method.getParameterList.params

    assertTrue(s"Expected ${expectedTypes.length} parameters, but got ${parameters.length} instead", parameters.length == expectedTypes.length)

    parameters.zip(expectedTypes).foreach { parameterAndExpectedType =>
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

    assertParameterCountAndType("bar0", Seq.empty, jvmFacing = true)
    assertParameterCountAndType("bar1", Seq("int"), jvmFacing = true)
    assertParameterCountAndType("bar2", Seq("int", "String"), jvmFacing = true)
  }

  def test_jvm_facing_parameters_of_method_in_implicit_class(): Unit = {
    myFixture.configureByText(s"$TestClassName.scala",
      s"""object Scope {
        |  implicit class $TestClassName(val d: Double) {
        |    def bar0(): Unit = ()
        |    def bar1(i: Int): Unit = ()
        |    def bar2(i: Int, s: String): Unit = ()
        |  }
        |}""".stripMargin)

    assertParameterCountAndType("bar0", Seq.empty, jvmFacing = true)
    assertParameterCountAndType("bar1", Seq("int"), jvmFacing = true)
    assertParameterCountAndType("bar2", Seq("int", "String"), jvmFacing = true)
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

    assertParameterCountAndType("bar0", Seq("double"), jvmFacing = true)
    assertParameterCountAndType("bar1", Seq("double", "int"), jvmFacing = true)
    assertParameterCountAndType("bar2", Seq("double", "int", "String"), jvmFacing = true)
    assertParameterCountAndType("bar3", Seq.empty, jvmFacing = true)
    assertParameterCountAndType("bar4", Seq.empty, jvmFacing = true)
  }
  
  def test_scala_facing_parameters_of_method_in_class(): Unit = {
    myFixture.configureByText(s"$TestClassName.scala",
      s"""class $TestClassName {
        |  def bar0(): Unit = ()
        |  def bar1(i: Int): Unit = ()
        |  def bar2(i: Int, s: String): Unit = ()
        |}""".stripMargin)

    assertParameterCountAndType("bar0", Seq.empty, jvmFacing = false)
    assertParameterCountAndType("bar1", Seq("int"), jvmFacing = false)
    assertParameterCountAndType("bar2", Seq("int", "String"), jvmFacing = false)
  }

  def test_scala_facing_parameters_of_method_in_implicit_class(): Unit = {
    myFixture.configureByText(s"$TestClassName.scala",
      s"""object Scope {
        |  implicit class $TestClassName(val d: Double) {
        |    def bar0(): Unit = ()
        |    def bar1(i: Int): Unit = ()
        |    def bar2(i: Int, s: String): Unit = ()
        |  }
        |}""".stripMargin)

    assertParameterCountAndType("bar0", Seq.empty, jvmFacing = false)
    assertParameterCountAndType("bar1", Seq("int"), jvmFacing = false)
    assertParameterCountAndType("bar2", Seq("int", "String"), jvmFacing = false)
  }

  def test_scala_facing_parameters_of_method_in_implicit_anyval_class(): Unit = {
    myFixture.configureByText(s"$TestClassName.scala",
      s"""object Scope {
        |  implicit class $TestClassName(val d: Double) extends AnyVal {
        |    def bar0(): Unit = ()
        |    def bar1(i: Int): Unit = ()
        |    def bar2(i: Int, s: String): Unit = ()
        |  }
        |}""".stripMargin)

    assertParameterCountAndType("bar0", Seq.empty, jvmFacing = false)
    assertParameterCountAndType("bar1", Seq("int"), jvmFacing = false)
    assertParameterCountAndType("bar2", Seq("int", "String"), jvmFacing = false)
  }
}
