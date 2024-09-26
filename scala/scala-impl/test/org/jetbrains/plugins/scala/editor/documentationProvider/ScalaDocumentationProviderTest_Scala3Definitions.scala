package org.jetbrains.plugins.scala.editor.documentationProvider

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.editor.documentationProvider.util.ScalaDocumentationsDefinitionSectionTesting

final class ScalaDocumentationProviderTest_Scala3Definitions extends ScalaDocumentationProviderTestBase
  with ScalaDocumentationsDefinitionSectionTesting {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  def testInline(): Unit = {
    val fileContent =
      s"""inline def ${|}f: Int = 1
         |""".stripMargin

    val expectedContent =
      """<span style="color:#000080;font-weight:bold;">inline</span> <span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">f</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testOpaque(): Unit = {
    val fileContent =
      s"""opaque type ${|}Foo = String
         |""".stripMargin

    val expectedContent =
      """<span style="color:#000080;font-weight:bold;">opaque</span> <span style="color:#000080;font-weight:bold;">type</span> <span style="color:#20999d;">Foo</span> = <span style="color:#000000;"><a href="psi_element://java.lang.String"><code>String</code></a></span>""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testTransparent(): Unit = {
    val fileContent =
      s"""transparent trait ${|}Foo
         |""".stripMargin

    val expectedContent =
      """<span style="color:#000080;font-weight:bold;">transparent</span> <span style="color:#000080;font-weight:bold;">trait</span> <span style="color:#000000;">Foo</span>""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testOpen(): Unit = {
    val fileContent =
      s"""open class ${|}Foo
         |""".stripMargin

    val expectedContent =
      """<span style="color:#000080;font-weight:bold;">open</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">Foo</span>""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testInfix(): Unit = {
    val fileContent =
      s"""extension (str: String)
         |  infix def ${|}comp(str2: String): Boolean = str == str2
         |""".stripMargin

    val expectedContent =
      """
        |<span style="color:#000080;font-weight:bold;">infix</span>
        | <span style="color:#000080;font-weight:bold;">def</span>
        | <span style="color:#000000;">comp</span>
        |(str2:
        | <span style="color:#000000;"><a href="psi_element://java.lang.String"><code>String</code></a></span>
        |): <span style=""><a href="psi_element://scala.Boolean"><code>Boolean</code></a></span>
        |""".stripMargin.withoutNewLines

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testGiven(): Unit = {
    val fileContent =
      s"""import scala.concurrent.ExecutionContext
         |given ${|}ec: ExecutionContext = ???
         |""".stripMargin

    val expectedContent =
      """<span style="color:#000080;font-weight:bold;">given</span> <span style="color:#660e7a;font-style:italic;">ec</span>
        |: <span style="color:#000000;"><a href="psi_element://scala.concurrent.ExecutionContext"><code>ExecutionContext</code></a></span>
        |""".stripMargin.withoutNewLines

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testContextParameters(): Unit = {
    val fileContent =
      s"""def ${|}foo(a: Int, b: Int)(using c: Int, d: Int): Unit = ???
         |""".stripMargin

    val expectedContent =
      """<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">foo</span>(a: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>, b: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>)(<span style="color:#000080;font-weight:bold;">using</span> c: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>, d: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>): <span style=""><a href="psi_element://scala.Unit"><code>Unit</code></a></span>""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testContextParameters_Anonymous(): Unit = {
    val fileContent =
      s"""import scala.concurrent.ExecutionContext
         |def ${|}f()(using ExecutionContext): Int = ???
         |""".stripMargin

    val expectedContent =
      """
        |<span style="color:#000080;font-weight:bold;">def</span>
        | <span style="color:#000000;">f</span>
        |()(
        |<span style="color:#000080;font-weight:bold;">using</span>
        | <span style="color:#000000;"><a href="psi_element://scala.concurrent.ExecutionContext"><code>ExecutionContext</code></a></span>
        |): <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>
        |""".stripMargin.withoutNewLines

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  private def ExpectedEnumRenderedContent_Common: String =
    s"""<html>
       |${DocHtmlHead(myFixture.getFile)}
       |$BodyStart
       |$ContainerInfoStart<icon src="AllIcons.Nodes.Package"/>&nbsp;<a href="psi_element://example"><code>example</code></a>$ContainerInfoEnd
       |$DefinitionStart<span style="color:#000080;font-weight:bold;">enum</span> <span style="color:#000000;">TestEnum</span>
       |<span style="color:#000080;font-weight:bold;">extends</span> <span style="color:#000000;"><a href="psi_element://java.io.Serializable"><code>Serializable</code></a></span>$DefinitionEnd
       |$ContentStart
       |Description of TestEnum
       |$ContentEnd
       |$BodyEnd
       |</html>
       |""".stripMargin

  def testEnum_AtDefinitionPosition(): Unit =
    doGenerateDocTest(
      s"""package example
         |
         |/** Description of TestEnum */
         |enum ${CARET}TestEnum extends Serializable:
         |  case EnumMember extends TestEnum
         |""".stripMargin,
      ExpectedEnumRenderedContent_Common
    )

  def testEnum_AtUsagePosition_1(): Unit =
    doGenerateDocTest(
      s"""package example
         |
         |/** Description of TestEnum */
         |enum TestEnum extends Serializable:
         |  case EnumMember extends TestEnum
         |
         |object usage:
         |  ${CARET}TestEnum.EnumMember
         |""".stripMargin,
      ExpectedEnumRenderedContent_Common
    )

  def testEnum_AtUsagePosition_2(): Unit =
    doGenerateDocTest(
      s"""package example
         |
         |/** Description of TestEnum */
         |enum TestEnum extends Serializable:
         |  case EnumMember extends TestEnum
         |
         |object usage:
         |  val x: ${CARET}TestEnum = ???
         |""".stripMargin,
      ExpectedEnumRenderedContent_Common
    )

  private def ExpectedEnumCase1RenderedContent_Common: String =
    s"""<html>
       |${DocHtmlHead(myFixture.getFile)}
       |$BodyStart
       |$ContainerInfoStart<icon src="org.jetbrains.plugins.scala.icons.Icons.ENUM"/>&nbsp;<a href="psi_element://example.MyEnum"><code>example.MyEnum</code></a>$ContainerInfoEnd
       |$DefinitionStart<span style="color:#000080;font-weight:bold;">case</span> <span style="color:#660e7a;font-style:italic;">MyEnumCase1</span>$DefinitionEnd
       |$ContentStart
       |Description of MyEnumCase1
       |$ContentEnd
       |$BodyEnd
       |</html>
       |""".stripMargin

  def testEnumCase1_AtDefinitionPosition(): Unit =
    doGenerateDocTest(
      s"""package example
         |
         |enum MyEnum:
         |  /** Description of MyEnumCase1 */
         |  case ${CARET}MyEnumCase1
         |""".stripMargin,
      ExpectedEnumCase1RenderedContent_Common
    )

  def testEnumCase1_AtUsagePosition_1(): Unit =
    doGenerateDocTest(
      s"""package example
         |
         |enum MyEnum:
         |  /** Description of MyEnumCase1 */
         |  case MyEnumCase1
         |
         |object usage:
         |  MyEnum.${CARET}MyEnumCase1
         |""".stripMargin,
      ExpectedEnumCase1RenderedContent_Common
    )

  def testEnumCase1_AtUsagePosition_2(): Unit =
    doGenerateDocTest(
      s"""package example
         |
         |enum MyEnum:
         |  /** Description of MyEnumCase1 */
         |  case MyEnumCase1
         |
         |object usage:
         |  val x: MyEnum.${CARET}MyEnumCase1.type = ???
         |""".stripMargin,
      ExpectedEnumCase1RenderedContent_Common
    )

  private def ExpectedEnumCase2RenderedContent_Common: String =
    s"""<html>
       |${DocHtmlHead(myFixture.getFile)}
       |$BodyStart
       |$ContainerInfoStart<icon src="org.jetbrains.plugins.scala.icons.Icons.ENUM"/>&nbsp;<a href="psi_element://example.MyEnum"><code>example.MyEnum</code></a>$ContainerInfoEnd
       |$DefinitionStart<span style="color:#000080;font-weight:bold;">case</span> <span style="color:#660e7a;font-style:italic;">MyEnumCase2</span>$DefinitionEnd
       |$ContentStart
       |Description of MyEnumCase1, MyEnumCase2
       |$ContentEnd
       |$BodyEnd
       |</html>
       |""".stripMargin

  def testEnumCase2_AtDefinitionPosition(): Unit =
    doGenerateDocTest(
      s"""package example
         |
         |enum MyEnum:
         |  /** Description of MyEnumCase1, MyEnumCase2 */
         |  case MyEnumCase1, ${CARET}MyEnumCase2
         |""".stripMargin,
      ExpectedEnumCase2RenderedContent_Common
    )

  def testEnumCase2_AtUsagePosition_1(): Unit =
    doGenerateDocTest(
      s"""package example
         |
         |enum MyEnum:
         |  /** Description of MyEnumCase1, MyEnumCase2 */
         |  case MyEnumCase1, MyEnumCase2
         |
         |object usage:
         |  MyEnum.${CARET}MyEnumCase2
         |""".stripMargin,
      ExpectedEnumCase2RenderedContent_Common
    )

  def testEnumCase2_AtUsagePosition_2(): Unit =
    doGenerateDocTest(
      s"""package example
         |
         |enum MyEnum:
         |  /** Description of MyEnumCase1, MyEnumCase2 */
         |  case MyEnumCase1, MyEnumCase2
         |
         |object usage:
         |  val x: MyEnum.${CARET}MyEnumCase2.type = ???
         |""".stripMargin,
      ExpectedEnumCase2RenderedContent_Common
    )

  def testEnumCase_WithExtendsList(): Unit =
    doGenerateDocDefinitionTest(
      s"""package example
         |
         |trait MyTrait
         |
         |enum MyEnum:
         |  /** Description of MyEnumCase1 */
         |  case ${CARET}MyEnumCase1 extends MyEnum with MyTrait
         |""".stripMargin,
      """<span style="color:#000080;font-weight:bold;">case</span> <span style="color:#660e7a;font-style:italic;">MyEnumCase1</span>
        |<span style="color:#000080;font-weight:bold;">extends</span> <span style="color:#000000;"><a href="psi_element://example.MyEnum"><code>MyEnum</code></a></span>
        |<span style="color:#000080;font-weight:bold;">with</span> <span style="color:#000000;"><a href="psi_element://example.MyTrait"><code>MyTrait</code></a></span>""".stripMargin
    )

  def testCaseClass(): Unit =
    doGenerateDocDefinitionTest(
      s"""case class ${CARET}MyCaseClass(x: Int)
         |""".stripMargin,
      """<span style="color:#000080;font-weight:bold;">case</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">MyCaseClass</span>(x: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>)
        |""".stripMargin,
    )

  def testCaseClass_WithExtendsList(): Unit =
    doGenerateDocDefinitionTest(
      s"""abstract class MyClass
         |trait MyTrait
         |case class ${CARET}MyCaseClass(x: Int) extends MyClass with MyTrait
         |""".stripMargin,
      """<span style="color:#000080;font-weight:bold;">case</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">MyCaseClass</span>(x: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>)
        |<span style="color:#000080;font-weight:bold;">extends</span> <span style="color:#000000;"><a href="psi_element://MyClass"><code>MyClass</code></a></span>
        |<span style="color:#000080;font-weight:bold;">with</span> <span style="color:#000000;"><a href="psi_element://MyTrait"><code>MyTrait</code></a></span>""".stripMargin
    )

  def testClassExtendingAnotherJavaClassShouldNotInheritDoc(): Unit = {
    myFixture.addFileToProject("J.java",
      s"""/** description of base class J */
         |class J {}
         |""".stripMargin
    )
    doGenerateDocDefinitionTest(
      s"""class ${|}B extends J""".stripMargin,
      s"""<span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">B</span>
         |<span style="color:#000080;font-weight:bold;">extends</span> <span style="color:#000000;"><a href="psi_element://J"><code>J</code></a></span>
         |""".stripMargin
    )
  }
  
  def testAnnotation_InterpolatedString(): Unit = {
    val fileContent =
      """
        |@deprecated(s"test ${42}")
        |def """.stripMargin + | +
        """foo() = {}
          |""".stripMargin

    // For now interpolated strings are displayed in ScalaDoc popups as regular strings, with no additional highlighting
    val expectedDoc =
      """<span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://scala.deprecated"><code>deprecated</code></a></span>(<span style="color:#008000;font-weight:bold;">s&quot;test ${42}&quot;</span>)
        |<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">foo</span>(): <span style=""><a href="psi_element://scala.Unit"><code>Unit</code></a></span>
        |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedDoc)
  }

  def testAnnotation_Number(): Unit = {
    val fileContent =
      s"""@deprecated(42)
         |def ${|}foo() = {}
         |""".stripMargin

    val expectedDoc =
      """<span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://scala.deprecated"><code>deprecated</code></a></span>(<span style="color:#0000ff;">42</span>)
        |<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">foo</span>(): <span style=""><a href="psi_element://scala.Unit"><code>Unit</code></a></span>
        |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedDoc)
  }

  def testAnnotation_Boolean(): Unit = {
    val fileContent =
      s"""@deprecated(true)
         |def ${|}foo() = {}
         |""".stripMargin

    val expectedDoc =
      """<span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://scala.deprecated"><code>deprecated</code></a></span>(<span style="color:#000080;font-weight:bold;">true</span>)
        |<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">foo</span>(): <span style=""><a href="psi_element://scala.Unit"><code>Unit</code></a></span>
        |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedDoc)
  }

  def testAbstractClass(): Unit = {
    val fileContent =
      s"""abstract class ${|}AbstractClass
         |""".stripMargin

    val expectedContent =
      """<span style="color:#000080;font-weight:bold;">abstract</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">AbstractClass</span>
        |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testAnnotation(): Unit = {
    val fileContent =
      s"""@Source(url = "https://foo.com/")
         |trait ${|}Foo
         |""".stripMargin

    val expectedContent =
      """<span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://scala.Any"><code>Any</code></a></span>(url = <span style="color:#008000;font-weight:bold;">&quot;https://foo.com/&quot;</span>)
        |<span style="color:#000080;font-weight:bold;">trait</span> <span style="color:#000000;">Foo</span>
        |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testValWithFunctionType(): Unit = {
    val fileContent =
      s"""val ${|}f = (x: Int) => x
         |""".stripMargin

    val expectedContent =
      """
        |<span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-style:italic;">f</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span> => <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>
        |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testImplicit(): Unit = {
    val fileContent =
      s"""import scala.concurrent.ExecutionContext
         |def ${|}f()(implicit ec: ExecutionContext): Int = ???
         |""".stripMargin

    val expectedContent =
      """<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">f</span>()(<span style="color:#000080;font-weight:bold;">implicit</span> ec: <span style="color:#000000;"><a href="psi_element://scala.concurrent.ExecutionContext"><code>ExecutionContext</code></a></span>): <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>
        |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testTwoParameters(): Unit = {
    val fileContent =
      s"""def ${|}foo(a: Int, b: Int): Unit = ???
         |""".stripMargin

    val expectedContent =
      """<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">foo</span>(a: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>, b: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>): <span style=""><a href="psi_element://scala.Unit"><code>Unit</code></a></span>
        |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testThreeParameters(): Unit = {
    val fileContent =
      s"""def ${|}foo(a: Int, b: Int, c: Int): Unit = ???
         |""".stripMargin

    val expectedContent =
      """<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">foo</span>(a: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>, b: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>, c: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>): <span style=""><a href="psi_element://scala.Unit"><code>Unit</code></a></span>
        |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testImplicitParameters(): Unit = {
    val fileContent =
      s"""def ${|}foo(a: Int, b: Int)(implicit c: Int, d: Int): Unit = ???
         |""".stripMargin

    val expectedContent =
      """<span style="color:#000080;font-weight:bold;">def</span>
        | <span style="color:#000000;">foo</span>(a: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>, b: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>)
        |(<span style="color:#000080;font-weight:bold;">implicit</span> c: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>, d: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>)
        |: <span style=""><a href="psi_element://scala.Unit"><code>Unit</code></a></span>
        |""".stripMargin.withoutNewLines

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testTypeLambda(): Unit = {
    val fileContent =
      s"""trait R
         |type ${|}T = [X] =>> R
         |""".stripMargin

    val expectedContent =
      s"""<span style="color:#000080;font-weight:bold;">type</span> <span style="color:#20999d;">T</span> = [<span style="color:#20999d;">X</span>] =>> <span style="color:#000000;"><a href="psi_element://R"><code>R</code></a></span>
         |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testTypeLambda2(): Unit = {
    val fileContent =
      s"""type ${|}TL = [X] =>> [Y] =>> (X, Y)
         |""".stripMargin

    val expectedContent =
      s"""<span style="color:#000080;font-weight:bold;">type</span> <span style="color:#20999d;">TL</span> = [<span style="color:#20999d;">X</span>] =>> [<span style="color:#20999d;">Y</span>] =>> (<span style="color:#20999d;">X</span>, <span style="color:#20999d;">Y</span>)
         |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testUnionType(): Unit = {
    val fileContent =
      s"""type UserName = String
         |type Password = Array[Char]
         |
         |def ${|}nameOrPwd(whichOne: Boolean): UserName | Password = ???
         |""".stripMargin

    val expectedContent =
      s"""<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">nameOrPwd</span>(
         |whichOne: <span style=""><a href="psi_element://scala.Boolean"><code>Boolean</code></a></span>
         |): <span style="color:#20999d;"><a href="psi_element://.UserName"><code>UserName</code></a></span> <span style="color:#20999d;">|</span> <span style="color:#20999d;"><a href="psi_element://.Password"><code>Password</code></a></span>
         |""".stripMargin.withoutNewLines

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testProductType(): Unit = {
    val fileContent =
      s"""type UserName = String
         |type Password = Array[Char]
         |
         |type ${|}NameAndPwd = UserName & Password
         |""".stripMargin

    val expectedContent =
      s"""<span style="color:#000080;font-weight:bold;">type</span> <span style="color:#20999d;">NameAndPwd</span> =
         | <span style="color:#20999d;"><a href="psi_element://.UserName"><code>UserName</code></a></span>
         | <span style="color:#20999d;">&</span> <span style="color:#20999d;"><a href="psi_element://.Password"><code>Password</code></a></span>
         |""".stripMargin.withoutNewLines

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testDependentFunctionType(): Unit = {
    val fileContent =
      s"""trait Entry { type Key; val key: Key }
         |
         |def ${|}extractKey(e: Entry): e.Key = e.key
         |""".stripMargin

    val expectedContent =
      s"""<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">extractKey</span>(
         |e: <span style="color:#000000;"><a href="psi_element://Entry"><code>Entry</code></a></span>
         |): e.<span style="color:#20999d;"><a href="psi_element://Entry.Key"><code>Key</code></a></span>
         |""".stripMargin.withoutNewLines

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testJavaType(): Unit = {
    val fileContent =
      s"""import java.util.ArrayList
         |
         |val ${|}all = new ArrayList[String]
         |""".stripMargin

    val expectedContent =
      s"""<span style="color:#000080;font-weight:bold;">val</span>
         | <span style="color:#660e7a;font-style:italic;">all</span>:
         | <span style="color:#000000;"><a href="psi_element://java.util.ArrayList"><code>ArrayList</code></a></span>[
         |<span style="color:#000000;"><a href="psi_element://java.lang.String"><code>String</code></a></span>
         |]
         |""".stripMargin.withoutNewLines

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testModifiersOrder(): Unit = {
    val inlineTransparent =
      s"""inline transparent def ${|}foo(): Unit = {}
         |""".stripMargin

    val inlineTransparentExpected =
      s"""<span style="color:#000080;font-weight:bold;">inline</span> <span style="color:#000080;font-weight:bold;">transparent</span>
         | <span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">foo</span>():
         | <span style=""><a href="psi_element://scala.Unit"><code>Unit</code></a></span>
         |""".stripMargin.withoutNewLines

    doGenerateDocDefinitionTest(inlineTransparent, inlineTransparentExpected)

    val transparentInline =
      s"""transparent inline def ${|}foo(): Unit = {}
         |""".stripMargin

    val transparentInlineExpected =
      s"""<span style="color:#000080;font-weight:bold;">transparent</span> <span style="color:#000080;font-weight:bold;">inline</span>
         | <span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">foo</span>():
         | <span style=""><a href="psi_element://scala.Unit"><code>Unit</code></a></span>
         |""".stripMargin.withoutNewLines

    doGenerateDocDefinitionTest(transparentInline, transparentInlineExpected)
  }

  def testInnerClass(): Unit = {
    val fileContent =
      s"""object Bar:
         |  class Baz(n: Int):
         |    case class FooFoo(foo: Int):
         |      val t: Int = 1
         |
         |  val ${|}c = new Baz(0).FooFoo(1)
         |""".stripMargin

    val expectedContent =
      s"""<span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-style:italic;">c</span>: <span style="color:#000000;"><a href="psi_element://Bar.Baz"><code>Baz</code></a></span>#<span style="color:#000000;"><a href="psi_element://Bar.Baz.FooFoo"><code>FooFoo</code></a></span>
         |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testClassInObject(): Unit = {
    val fileContent =
      s"""object Bar:
         |  class Baz(n: Int)
         |
         |val ${|}fff = Bar.Baz(1)
         |""".stripMargin

    val expectedContent =
      s"""<span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-style:italic;">fff</span>:
         | <span style="color:#000000;"><a href="psi_element://Bar"><code>Bar</code></a></span>.<span style="color:#000000;"><a href="psi_element://Bar.Baz"><code>Baz</code></a></span>
         |""".stripMargin.withoutNewLines

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testInnerClassOfAnInstance(): Unit = {
    val fileContent =
      s"""object Bar:
         |  class Baz(n: Int):
         |    case class FooFoo(foo: Int):
         |      val t: Int = 1
         |
         |val fff = Bar.Baz(1)
         |val ${|}ggg = fff.FooFoo(2)
         |val hhh = new Bar.Baz(1).FooFoo(2)
         |""".stripMargin

    val expectedContent =
      s"""<span style="color:#000080;font-weight:bold;">val</span>
         | <span style="color:#660e7a;font-style:italic;">ggg</span>:
         | fff.<span style="color:#000000;"><a href="psi_element://Bar.Baz.FooFoo"><code>FooFoo</code></a></span>
         |""".stripMargin.withoutNewLines

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testInnerClassOfAnAnonymousInstance(): Unit = {
    val fileContent =
      s"""object Bar:
         |  class Baz(n: Int):
         |    case class FooFoo(foo: Int)
         |
         |val ${|}hhh = new Bar.Baz(1).FooFoo(2)
         |""".stripMargin

    val expectedContent =
      s"""<span style="color:#000080;font-weight:bold;">val</span>
         | <span style="color:#660e7a;font-style:italic;">hhh</span>:
         | <span style="color:#000000;"><a href="psi_element://Bar"><code>Bar</code></a></span>.<span style="color:#000000;"><a href="psi_element://Bar.Baz"><code>Baz</code></a></span>#<span style="color:#000000;"><a href="psi_element://Bar.Baz.FooFoo"><code>FooFoo</code></a></span>
         |""".stripMargin.withoutNewLines

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }
}
