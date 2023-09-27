package org.jetbrains.plugins.scala.editor.documentationProvider

import org.jetbrains.plugins.scala.ScalaVersion

class Scala3DocumentationProviderTest extends ScalaDocumentationProviderTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  def testInline(): Unit = {
    val fileContent =
      s"""
         |inline def ${|}f: Int = 1
         |""".stripMargin

    val expectedContent =
      """<span style="color:#000080;font-weight:bold;">inline</span> <span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">f</span>: <span style="color:#000000;"><a href="psi_element://scala.Int"><code>Int</code></a></span>""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testOpaque(): Unit = {
    val fileContent =
      s"""
         |opaque type ${|}Foo = String
         |""".stripMargin

    val expectedContent =
      """<span style="color:#000080;font-weight:bold;">opaque</span> <span style="color:#000080;font-weight:bold;">type</span> <span style="color:#20999d;">Foo</span> = <span style="color:#000000;"><a href="psi_element://java.lang.String"><code>String</code></a></span>""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testTransparent(): Unit = {
    val fileContent =
      s"""
         |transparent trait ${|}Foo
         |""".stripMargin

    val expectedContent =
      """<span style="color:#000080;font-weight:bold;">transparent</span> <span style="color:#000080;font-weight:bold;">trait</span> <span style="color:#000000;">Foo</span>""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testOpen(): Unit = {
    val fileContent =
      s"""
         |open class ${|}Foo
         |""".stripMargin

    val expectedContent =
      """<span style="color:#000080;font-weight:bold;">open</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">Foo</span>""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testInfix(): Unit = {
    val fileContent =
      s"""
         |extension (str: String)
         |  infix def ${|}comp(str2: String): Boolean = str == str2
         |""".stripMargin

    val expectedContent =
      """
        |<span style="color:#000080;font-weight:bold;">infix</span>
        | <span style="color:#000080;font-weight:bold;">def</span>
        | <span style="color:#000000;">comp</span>
        |(str2:
        | <span style="color:#000000;"><a href="psi_element://java.lang.String"><code>String</code></a></span>
        |): <span style="color:#000000;"><a href="psi_element://scala.Boolean"><code>Boolean</code></a></span>
        |""".stripMargin.withoutNewLines

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testGiven(): Unit = {
    val fileContent =
      s"""
         |import scala.concurrent.ExecutionContext
         |given ${|}ec: ExecutionContext = ???
         |""".stripMargin

    val expectedContent =
      """<span style="color:#000080;font-weight:bold;">given</span> <span style="color:#000080;font-weight:bold;">ec</span>
        |: <span style="color:#000000;"><a href="psi_element://scala.concurrent.ExecutionContext"><code>ExecutionContext</code></a></span>
        |""".stripMargin.withoutNewLines

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testContextParameters(): Unit = {
    val fileContent =
      s"""
         |def ${|}foo(a: Int, b: Int)(using c: Int, d: Int): Unit = ???
         |""".stripMargin

    val expectedContent =
      """<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">foo</span>(a: <span style="color:#000000;"><a href="psi_element://scala.Int"><code>Int</code></a></span>, b: <span style="color:#000000;"><a href="psi_element://scala.Int"><code>Int</code></a></span>)(<span style="color:#000080;font-weight:bold;">using</span> c: <span style="color:#000000;"><a href="psi_element://scala.Int"><code>Int</code></a></span>, d: <span style="color:#000000;"><a href="psi_element://scala.Int"><code>Int</code></a></span>): <span style="color:#000000;"><a href="psi_element://scala.Unit"><code>Unit</code></a></span>""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testContextParameters_Anonimous(): Unit = {
    val fileContent =
      s"""
         |import scala.concurrent.ExecutionContext
         |def ${|}f()(using ExecutionContext): Int = ???
         |""".stripMargin

    val expectedContent =
      """
        |<span style="color:#000080;font-weight:bold;">def</span>
        | <span style="color:#000000;">f</span>
        |()(
        |<span style="color:#000080;font-weight:bold;">using</span>
        | <span style="color:#000000;"><a href="psi_element://scala.concurrent.ExecutionContext"><code>ExecutionContext</code></a></span>
        |): <span style="color:#000000;"><a href="psi_element://scala.Int"><code>Int</code></a></span>
        |""".stripMargin.withoutNewLines

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  private def ExpectedEnumRenderedContent_Common: String =
    s"""<html>
       |${DocHtmlHead(myFixture.getFile)}
       |$BodyStart
       |$DefinitionStart<icon src="AllIcons.Nodes.Package"/> <a href="psi_element://example"><code>example</code></a>
       |
       |<span style="color:#000080;font-weight:bold;">enum</span> <span style="color:#000000;">TestEnum</span>
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
       |$DefinitionStart<icon src="AllIcons.Nodes.Package"/> <a href="psi_element://example.MyEnum"><code>example.MyEnum</code></a>
       |
       |<span style="color:#000080;font-weight:bold;">case</span> <span style="color:#000000;">MyEnumCase1</span>$DefinitionEnd
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
       |$DefinitionStart<icon src="AllIcons.Nodes.Package"/> <a href="psi_element://example.MyEnum"><code>example.MyEnum</code></a>
       |
       |<span style="color:#000080;font-weight:bold;">case</span> <span style="color:#000000;">MyEnumCase2</span>$DefinitionEnd
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
      """<icon src="AllIcons.Nodes.Package"/> <a href="psi_element://example.MyEnum"><code>example.MyEnum</code></a>
        |
        |<span style="color:#000080;font-weight:bold;">case</span> <span style="color:#000000;">MyEnumCase1</span>
        |<span style="color:#000080;font-weight:bold;">extends</span> <span style="color:#000000;"><a href="psi_element://example.MyEnum"><code>MyEnum</code></a></span>
        |<span style="color:#000080;font-weight:bold;">with</span> <span style="color:#000000;"><a href="psi_element://example.MyTrait"><code>MyTrait</code></a></span>""".stripMargin
    )
}
