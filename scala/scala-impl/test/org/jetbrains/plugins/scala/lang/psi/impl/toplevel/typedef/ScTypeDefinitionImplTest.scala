package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.junit.Assert.assertTrue

class ScTypeDefinitionImplTest extends ScalaFixtureTestCase {

  private val TestClassName = "Foo"

  def test_that_fails_on_purpose_in_order_to_prevent_merge(): Unit = throw new Exception

  private def assertFakeCompanionModuleExists(fakeCompanionModuleQualifiedName: Option[String]): Unit = {
    val clazz = ScalaPsiManager.instance(getProject).getClassesByName(TestClassName, GlobalSearchScope.fileScope(getFile)).head.asInstanceOf[ScClass]
    clazz.fakeCompanionModule match {
      case Some(fakeCompanionModule) =>

        assertTrue(s"No fake companion module was expected for $clazz, but one was found anyway",
          fakeCompanionModuleQualifiedName.nonEmpty)

        val expected = fakeCompanionModuleQualifiedName.get
        val actual = fakeCompanionModule.qualifiedName

        assertTrue(s"Fake companion module with FQN $expected was expected for $clazz, but got $actual",
          actual == expected)

      case _ =>
        assertTrue(
          s"""Fake companion module with FQN ${fakeCompanionModuleQualifiedName.getOrElse("")} was expected for $clazz,
             |but no fake companion module was found (by any name)""".stripMargin,
          fakeCompanionModuleQualifiedName.isEmpty)
    }
  }

  def test_fake_companion_module_of_class(): Unit = {
    myFixture.configureByText(s"$TestClassName.scala", s"class $TestClassName")
    assertFakeCompanionModuleExists(None)
  }

  def test_fake_companion_module_of_class_with_companion_object(): Unit = {
    myFixture.configureByText(s"$TestClassName.scala", s"class $TestClassName; object $TestClassName")
    assertFakeCompanionModuleExists(None)
  }

  def test_fake_companion_module_of_implicit_class(): Unit = {
    myFixture.configureByText(s"$TestClassName.scala",
      s"""object Scope {
         |  implicit class $TestClassName(d: Double)
         |}""".stripMargin)
    assertFakeCompanionModuleExists(None)
  }

  def test_fake_companion_module_of_implicit_anyval_class(): Unit = {
    myFixture.configureByText(s"$TestClassName.scala",
      s"""object Scope {
         |  implicit class $TestClassName(d: Double) extends AnyVal
         |}""".stripMargin)
    assertFakeCompanionModuleExists(Some(s"Scope.$TestClassName"))
  }

  def test_fake_companion_module_of_implicit_anyval_class_in_package_object(): Unit = {
    myFixture.configureByText(s"$TestClassName.scala",
      s"""package object Scope {
         |  implicit class $TestClassName(d: Double) extends AnyVal
         |}""".stripMargin)
    assertFakeCompanionModuleExists(Some(s"Scope.package$$$TestClassName"))
  }
}
