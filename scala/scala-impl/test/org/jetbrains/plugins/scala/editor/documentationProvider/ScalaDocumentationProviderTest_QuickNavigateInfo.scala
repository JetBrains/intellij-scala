package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.editor.documentationProvider.util.ScalaDocumentationsSectionsTestingBase
import org.jetbrains.plugins.scala.util.assertions.StringAssertions._

class ScalaDocumentationProviderTest_QuickNavigateInfo
  extends ScalaDocumentationProviderTestBase
    with ScalaDocumentationsSectionsTestingBase {

  private def moduleName: String = getModule.getName

  protected final def doGenerateQuickNavigateInfoBodyTest(
    fileContent: String,
    expectedBody: => String,
  ): Unit = {
    val actualDoc = generateQuickNavigateInfo(fileContent)
    val actualBody = extractSectionInner(actualDoc, "body", BodyStart, BodyEnd)
    assertDocHtml(expectedBody, actualBody)
  }

  def testSimpleClass_TestEntireHtml(): Unit = {
    val fileContent = s"""class ${|}MyClass"""

    val hintHint = HintUtil.getInformationHint
    val style = UIUtil.getCssFontDeclaration(hintHint.getTextFont, hintHint.getTextForeground, hintHint.getLinkForeground, hintHint.getUlImg)
    val expectedDoc =
      s"""<html>
         |<head>
         |$style
         |</head>
         |<body>
         |[$moduleName] default<br>class MyClass extends <a href="psi_element://java.lang.Object">Object</a></body>
         |</html>
         |""".stripMargin

    val actualDoc = generateQuickNavigateInfo(fileContent)
    //in quick doc new lines are visible (treated as if it's a `<br>`) but subsequent spaces are treated as one
    assertDocHtml(
      expectedDoc,
      actualDoc,
      HtmlSpacesComparisonMode.IgnoreNewLinesAndCollapseSpaces
    )
  }

  def testSimpleClass(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""class ${|}MyClass""",
      s"""[$moduleName] default<br>class MyClass extends <a href="psi_element://java.lang.Object">Object</a>"""
    )

  def testSimpleClassParam(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""class MyClass(s${|}tr: String)""",
      s"""MyClass <default><br>str: <a href="psi_element://java.lang.String">String</a>"""
    )

  def testSimpleClassParamWithDefaultValue(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""class MyClass(s${|}tr: String = "default value")""",
      s"""MyClass <default><br>str: <a href="psi_element://java.lang.String">String</a> = …"""
    )

  def testSimpleTypeAlias(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""type ${|}Foo = String""",
      s"""type Foo = <a href="psi_element://java.lang.String">String</a>
         |""".stripMargin
    )

  def testSimpleTrait(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""trait ${|}MyTrait""",
      s"""[$moduleName] default<br>trait MyTrait"""
    )

  def testSimpleObject(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""object ${|}MyObject""",
      s"""[$moduleName] default<br>object MyObject extends <a href="psi_element://java.lang.Object">Object</a>"""
    )

  def testClassWithModifiers(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""abstract sealed class ${|}MyClass""",
      s"""[$moduleName] default<br>abstract sealed class MyClass extends <a href="psi_element://java.lang.Object">Object</a>"""
    )

  def testClassWithModifiers_1(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""final class ${|}MyClass""",
      s"""[$moduleName] default<br>final class MyClass extends <a href="psi_element://java.lang.Object">Object</a>"""
    )

  def testClassWithGenericParameter(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""class ${|}Class[T]""",
      s"""[$moduleName] default<br>class Class[T] extends <a href="psi_element://java.lang.Object">Object</a>"""
    )

  def testClassWithGenericParameter_WithBounds(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""trait Trait[A]
         |class ${|}Class[T <: Trait[_ >: Object]]
         |""".stripMargin,
      s"""[$moduleName] default<br>class Class[T &lt;: <a href="psi_element://Trait">Trait</a>[_ &gt;: <a href="psi_element://java.lang.Object">Object</a>]] extends <a href="psi_element://java.lang.Object">Object</a>"""
    )

  def testClassWithGenericParameter_WithRecursiveBounds(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""trait Trait[T]
         |class ${|}Class2[T <: Trait[T]]
         |""".stripMargin,
      s"""[$moduleName] default<br>class Class2[T &lt;: <a href="psi_element://Trait">Trait</a>[T]] extends <a href="psi_element://java.lang.Object">Object</a>"""
    )

  def testClassWithGenericParameter_WithRecursiveBounds_1(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""trait Trait[T]
         |class ${|}Class4[T <: Trait[_ >: Trait[T]]]
         |""".stripMargin,
      s"""[$moduleName] default<br>class Class4[T &lt;: <a href="psi_element://Trait">Trait</a>[_ &gt;: <a href="psi_element://Trait">Trait</a>[T]]] extends <a href="psi_element://java.lang.Object">Object</a>"""
    )

  def testClassWithSuperWithGenerics(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""trait Trait[A]
         |abstract class ${|}Class extends Comparable[_ <: Trait[_ >: String]]
         |""".stripMargin,
      s"""[$moduleName] default<br>abstract class Class extends <a href="psi_element://java.lang.Comparable">Comparable</a>[_ &lt;: <a href="psi_element://Trait">Trait</a>[_ &gt;: <a href="psi_element://java.lang.String">String</a>]]""".stripMargin
    )

  def testClassExtendsListShouldNotContainWithObject(): Unit = {
    myFixture.addFileToProject("commons.scala",
      """class BaseClass
        |trait BaseTrait1
        |trait BaseTrait2
        |""".stripMargin
    )
    val classesWithoutObject = Seq(
      s"class ${|}MyClass2 extends BaseClass",
      s"class ${|}MyClass4 extends BaseTrait1",
      s"class ${|}MyClass3 extends BaseClass with BaseTrait1",
      s"class ${|}MyTrait1",
      s"class ${|}MyTrait2 extends BaseTrait1",
      s"class ${|}MyTrait3 extends BaseTrait1 with BaseTrait2"
    )
    // testing exact quick info value would be very noisy, it's enough to test just presence of ` with Object` which can be escaped!
    val withObjectRegex = "(\\s|\\n)with .*Object".r
    classesWithoutObject.foreach { content =>
      val quickInfo = generateQuickNavigateInfo(content)
      assertStringNotMatches(quickInfo, withObjectRegex)
    }
  }

  def testClassExtendsListShouldContainObjectIfThereAreNoBaseClasses(): Unit = {
    myFixture.addFileToProject("commons.scala",
      """class BaseClass
        |trait BaseTrait1
        |trait BaseTrait2
        |""".stripMargin
    )

    val classesWithObject = Seq(
      s"class ${|}MyClass1"
    )

    val extendsObjectRegex = "(\\s|\\n)extends .*Object".r
    classesWithObject.foreach { content =>
      val quickInfo = generateQuickNavigateInfo(content)
      assertStringMatches(quickInfo, extendsObjectRegex)
    }
  }

  def testValueDefinition(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""class Wrapper {
         |  val (field1, ${|}field2) = (42, "hello")
         |}""".stripMargin,
      """<a href="psi_element://Wrapper">Wrapper</a> <default><br>val field2: <a href="psi_element://java.lang.String">String</a> = "hello""""
    )

  def testValueDeclaration(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""abstract class Wrapper {
         |  val ${|}field2: String
         |}""".stripMargin,
      """<a href="psi_element://Wrapper">Wrapper</a> <default><br>val field2: <a href="psi_element://java.lang.String">String</a>"""
    )

  def testVariableDefinition(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""class Wrapper {
         |  var (field1, ${|}field2) = (42, "hello")
         |}""".stripMargin,
      """<a href="psi_element://Wrapper">Wrapper</a> <default><br>var field2: <a href="psi_element://java.lang.String">String</a> = "hello""""
    )

  def testVariableDeclaration(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""abstract class Wrapper {
         |  var ${|}field2: String
         |}""".stripMargin,
      s"""<a href="psi_element://Wrapper">Wrapper</a> <default><br>var field2: <a href="psi_element://java.lang.String">String</a>"""
    )

  def testValueWithModifiers(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""class Wrapper {
         |  protected final lazy val ${|}field2 = "hello"
         |}""".stripMargin,
      """<a href="psi_element://Wrapper">Wrapper</a> <default><br>protected final lazy val field2: <a href="psi_element://java.lang.String">String</a> = "hello""""
    )

  def testHigherKindedTypeParameters(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""object O {
         |  def ${|}f[A[_, B]] = 42
         |}""".stripMargin,
      """<a href="psi_element://O">O</a> <default><br>def f[A[_, B]]: <a href="psi_element://scala.Int">Int</a>"""
    )

  def testHigherKindedTypeParameters_1(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""trait ${|}T[X[_, Y[_, Z]]]
         |""".stripMargin,
      """[light_idea_test_case] default<br>trait T[X[_, Y[_, Z]]]"""
    )

  def testMethod(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""class X {
         | def ${|}f1 = 42
         |}
         |""".stripMargin,
      """<a href="psi_element://X">X</a> <default><br>def f1: <a href="psi_element://scala.Int">Int</a>"""
    )

  def testMethodWithAccessModifier(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""class X {
         |  protected def ${|}f1 = 42
         |}
         |""".stripMargin,
      """<a href="psi_element://X">X</a> <default><br>protected def f1: <a href="psi_element://scala.Int">Int</a>"""
    )

  def testMethodWithAccessModifierWithThisQualifier(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""class X {
         |  protected[this] def ${|}f1 = 42
         |}
         |""".stripMargin,
      """<a href="psi_element://X">X</a> <default><br>protected def f1: <a href="psi_element://scala.Int">Int</a>"""
    )

  def testTypeWithColon(): Unit =
    doGenerateQuickNavigateInfoBodyTest(
      s"""trait MyTrait[T]
         |class :::[T1, T2]
         |class ${|}ClassWithGenericColons1[A <: MyTrait[:::[Int, String]]]
         |  extends MyTrait[Int ::: String]
         |""".stripMargin,
      """[light_idea_test_case] default<br>class ClassWithGenericColons1[A &lt;: <a href="psi_element://MyTrait">MyTrait</a>[<a href="psi_element://scala.Int">Int</a> <a href="psi_element://:::">:::</a> <a href="psi_element://java.lang.String">String</a>]] extends <a href="psi_element://MyTrait">MyTrait</a>[<a href="psi_element://scala.Int">Int</a> <a href="psi_element://:::">:::</a> <a href="psi_element://java.lang.String">String</a>]"""
    )
}
