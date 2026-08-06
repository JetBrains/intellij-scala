package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.types.ValueClassType.{ImplicitValueClass, ImplicitValueClassDumbMode}
import org.junit.Assert.fail

class ValueClassTypeTest extends SimpleTestCase {

  private val TestClassName = "Foo"

  private def doTest(clazz: ScClass, expectedToBeImplicitValueClass: Boolean): Unit = clazz match {
    case ImplicitValueClass(_) => if (!expectedToBeImplicitValueClass) fail(s"Expected $clazz not to be an implicit value class")
    case _ if expectedToBeImplicitValueClass => fail(s"Expected $clazz be an implicit value class")
    case _ =>
  }

  private def doDumbTest(clazz: ScClass, expectedToBeImplicitValueClass: Boolean): Unit = clazz match {
    case ImplicitValueClassDumbMode(_) => if (!expectedToBeImplicitValueClass) fail(s"Expected $clazz not to be an implicit value class")
    case _ if expectedToBeImplicitValueClass => fail(s"Expected $clazz be an implicit value class")
    case _ =>
  }

  def test_class(): Unit = {
    val clazz = s"class $TestClassName".parse[ScClass]
    doTest(clazz, expectedToBeImplicitValueClass = false)
    doDumbTest(clazz, expectedToBeImplicitValueClass = false)
  }

  def test_implicit_class(): Unit = {
    val clazz = s"object Scope { implicit class $TestClassName(i: Int) }"
      .parse[ScObject].allInnerTypeDefinitions
      .filter(_.name == TestClassName).head.asInstanceOf[ScClass]

    doTest(clazz, expectedToBeImplicitValueClass = false)
    doDumbTest(clazz, expectedToBeImplicitValueClass = false)
  }

  def test_implicit_value_class(): Unit = {
    val code = s"object Scope { implicit class $TestClassName(val i: Int) extends AnyVal }"
    val clazz = code.parse[ScObject].allInnerTypeDefinitions.filter(_.name == TestClassName).head.asInstanceOf[ScClass]
    doTest(clazz, expectedToBeImplicitValueClass = true)
    doDumbTest(clazz, expectedToBeImplicitValueClass = true)
  }
}
