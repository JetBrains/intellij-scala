package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.StdTypes
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.junit.Assert.{assertTrue, fail}

import scala.jdk.CollectionConverters.CollectionHasAsScala

class MethodInvocationTest extends ScalaFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_9

  private def findMethodInvocationByScClassAndMethodName(clazz: ScClass, methodName: String): MethodInvocation = {

    val methods = clazz.methodsByName(methodName).toSeq
    val methodReferences = methods.map(_.method).flatMap(ReferencesSearch.search(_).findAll().asScala).map(_.getElement.getContext)
    val methodInvocation = methodReferences.collectFirst { case methodInvocation: MethodInvocation => methodInvocation }

    methodInvocation match {
      case Some(methodInvocation: MethodInvocation) => methodInvocation
      case _ =>
        fail(s"Couldn't find method invocation to method with name $methodName").asInstanceOf[Nothing]
    }
  }

  private def findMethodInvocationByClassNameAndMethodName(className: String, methodName: String): MethodInvocation = {

    val classes = ScalaPsiManager.instance(getProject).getClassesByName(className, GlobalSearchScope.fileScope(getFile))

    classes match {
      case Seq(clazz: ScClass) => findMethodInvocationByScClassAndMethodName(clazz, methodName)
      case Seq() =>
        fail(s"Couldn't find any definitions with name $className").asInstanceOf[Nothing]
      case seq =>
        fail(s"Expected to find single class with name $className, but got:\n${seq.mkString("\n")}").asInstanceOf[Nothing]
    }
  }

  private def assertParameterType(parameter: Parameter, expectedType: ScType): Unit = {

    val actualType = parameter.paramType

    assertTrue(
      s"Expected $expectedType but got $actualType instead",
      actualType == expectedType
    )
  }
  
  def test_method_invocation_matched_parameters_order(): Unit = {

    val text =
      """class A {
        |  def doubleFloatIntLongToUnit(d: Double, f: Float, i: Int, l: Long): Unit = {}
        |  doubleFloatIntLongToUnit(1.0, 2.0f, 3, 4L)
        |}
        |""".stripMargin

    myFixture.configureByText(s"${getTestName(false)}.scala", text)

    val parameters = findMethodInvocationByClassNameAndMethodName("A", "doubleFloatIntLongToUnit").matchedParameters

    assertTrue(parameters.size == 4)

    assertParameterType(parameters.head._2, StdTypes.instance.Double)
    assertParameterType(parameters(1)._2, StdTypes.instance.Float)
    assertParameterType(parameters(2)._2, StdTypes.instance.Int)
    assertParameterType(parameters(3)._2, StdTypes.instance.Long)
  }
}
