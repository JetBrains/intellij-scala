
package org.jetbrains.plugins.scala.lang.psi.api.statements

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.junit.Assert.{assertEquals, fail}

class ScEnumCaseTest extends ScalaFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  private def assertEnumCaseKind(enumCaseName: String, expectedKind: ScEnumCaseKind): Unit = {
    val enumCase = findEnumCase(enumCaseName)
    assertEquals(s"Enum case kind mismatch for $enumCaseName", expectedKind, enumCase.enumKind)
  }

  private def findEnumCase(name: String): ScEnumCase = {
    val classes = ScalaPsiManager.instance(getProject).getClassesByName(name, GlobalSearchScope.fileScope(getFile))
    classes match {
      case Seq(enumCase: ScEnumCase) => enumCase
      case Seq() =>
        fail(s"Couldn't find any definitions with name $name").asInstanceOf[Nothing]
      case seq =>
        fail(s"Expected to find single enum case with name $name, but got:\n${seq.mkString("\n")}").asInstanceOf[Nothing]
    }
  }

  def testEnumCaseKind_EnumWithoutParameters(): Unit = {
    val text =
      """enum MyEnum:
        |  case MyCase1_WithoutParameters
        |  case MyCase2_WithEmptyParameters()
        |  case MyCase3_WithParameters(x: Int)
        |  case MyCase4_WithTypeParameters[T](x: T)
        |  case MyCase5_WithExtendsList extends MyEnum
        |  case MyCase6_WithParametersAndExtendsList(y: Int) extends MyEnum
        |""".stripMargin

    myFixture.configureByText(s"${getTestName(false)}.scala", text)

    assertEnumCaseKind("MyCase1_WithoutParameters", ScEnumCaseKind.SingletonCase)
    assertEnumCaseKind("MyCase2_WithEmptyParameters", ScEnumCaseKind.ClassCase)
    assertEnumCaseKind("MyCase3_WithParameters", ScEnumCaseKind.ClassCase)
    assertEnumCaseKind("MyCase4_WithTypeParameters", ScEnumCaseKind.ClassCase)
    assertEnumCaseKind("MyCase5_WithExtendsList", ScEnumCaseKind.SingletonCase)
    assertEnumCaseKind("MyCase6_WithParametersAndExtendsList", ScEnumCaseKind.ClassCase)
  }

  def testEnumCaseKind_EnumWithParameters(): Unit = {
    val text =
      """enum MyEnumWithParameter(val p: Int):
        |  case MyCase1_WithExtendsList extends MyEnumWithParameter(1)
        |  case MyCase2_WithExtendsList extends MyEnumWithParameter(2)
        |  case MyCase3_WithExtendsList() extends MyEnumWithParameter(3)
        |""".stripMargin

    myFixture.configureByText(s"${getTestName(false)}.scala", text)

    assertEnumCaseKind("MyCase1_WithExtendsList", ScEnumCaseKind.SingletonCase)
    assertEnumCaseKind("MyCase2_WithExtendsList", ScEnumCaseKind.SingletonCase)
    assertEnumCaseKind("MyCase3_WithExtendsList", ScEnumCaseKind.ClassCase)
  }
}