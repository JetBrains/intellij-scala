package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.junit.Assert.{assertFalse, assertTrue}

class ScFunctionDefinitionImplJvmFacingTest extends ScalaFixtureTestCase {

  private val TestClassName = "Foo"

  private def assertJvmFacingMethodContainerIsPhysicalClass(): Unit = {
    val clazz = ScalaPsiManager.instance(getProject).getClassesByName(TestClassName, GlobalSearchScope.fileScope(getFile)).head.asInstanceOf[ScClass]
    val method = clazz.functions.filter(_.name == "bar").head
    //noinspection ScalaWrongPlatformMethodsUsage
    assertTrue(method.getContainingClass == clazz)
  }

  def test_jvm_facing_containing_class_of_method_in_class(): Unit = {
    myFixture.configureByText(s"$TestClassName.scala",
      s"""class $TestClassName {
         |  def bar(): Unit = ()
         |}""".stripMargin)

    assertJvmFacingMethodContainerIsPhysicalClass()
  }

  def test_jvm_facing_containing_class_of_method_in_implicit_class(): Unit = {
    myFixture.configureByText(s"$TestClassName.scala",
      s"""object Scope {
         |  implicit class $TestClassName(d: Double) {
         |    def bar(): Unit = ()
         |  }
         |}""".stripMargin)

    assertJvmFacingMethodContainerIsPhysicalClass()
  }

  //noinspection ScalaWrongPlatformMethodsUsage
  def test_jvm_facing_containing_class_of_method_in_implicit_anyval_class(): Unit = {
    myFixture.configureByText(s"$TestClassName.scala",
      s"""object Scope {
         |  implicit class $TestClassName(d: Double) extends AnyVal {
         |    def bar0(): Unit = ()
         |    private def bar1(): Unit = ()
         |    protected def bar2(): Unit = ()
         |  }
         |}""".stripMargin)

    val clazz = ScalaPsiManager.instance(getProject).getClassesByName(TestClassName, GlobalSearchScope.fileScope(getFile)).head.asInstanceOf[ScClass]

    val method0 = clazz.functions.filter(_.name == "bar0").head
    val method1 = clazz.functions.filter(_.name == "bar1").head
    val method2 = clazz.functions.filter(_.name == "bar2").head

    assertFalse(method0.getContainingClass == clazz)
    assertTrue(method0.getContainingClass.getName == clazz.getName + "$")

    assertTrue(method1.getContainingClass == clazz)
    assertTrue(method2.getContainingClass == clazz)

  }

  def test_jvm_facing_name_of_method_in_class(): Unit = {
    myFixture.configureByText(s"$TestClassName.scala",
      s"""class $TestClassName {
         |  def bar(): Unit = ()
         |}""".stripMargin)

    val clazz = ScalaPsiManager.instance(getProject).getClassesByName(TestClassName, GlobalSearchScope.fileScope(getFile)).head.asInstanceOf[ScClass]
    val method = clazz.functions.filter(_.name == "bar").head

    //noinspection ScalaWrongPlatformMethodsUsage
    assertTrue(method.getName == "bar")
  }

  def test_jvm_facing_name_of_method_in_implicit_class(): Unit = {
    myFixture.configureByText(s"$TestClassName.scala",
      s"""object Scope {
         |  implicit class $TestClassName(val d: Double) {
         |    def bar(): Unit = ()
         |  }
         |}""".stripMargin)

    val clazz = ScalaPsiManager.instance(getProject).getClassesByName(TestClassName, GlobalSearchScope.fileScope(getFile)).head.asInstanceOf[ScClass]
    val method = clazz.functions.filter(_.name == "bar").head

    //noinspection ScalaWrongPlatformMethodsUsage
    assertTrue(method.getName == "bar")
  }

  //noinspection ScalaWrongPlatformMethodsUsage
  def test_jvm_facing_name_of_method_in_implicit_anyval_class(): Unit = {
    myFixture.configureByText(s"$TestClassName.scala",
      s"""object Scope {
         |  implicit class $TestClassName(val d: Double) extends AnyVal {
         |    def bar0(): Unit = ()
         |    private def bar1(): Unit = ()
         |    protected def bar2(): Unit = ()
         |  }
         |}""".stripMargin)

    val clazz = ScalaPsiManager.instance(getProject).getClassesByName(TestClassName, GlobalSearchScope.fileScope(getFile)).head.asInstanceOf[ScClass]

    val method0 = clazz.functions.filter(_.name == "bar0").head
    val method1 = clazz.functions.filter(_.name == "bar1").head
    val method2 = clazz.functions.filter(_.name == "bar2").head

    assertTrue(method0.getName == "bar0$extension")
    assertTrue(method1.getName == "bar1")
    assertTrue(method2.getName == "bar2")
  }
}
