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
      """
        |<span style="color:#000080;font-weight:bold;">inline</span><span style="color:#000080;font-weight:bold;">def</span>
        |f:<span style="color:#000000;"><a href="psi_element://scala.Int"><code>Int</code></a></span>
        |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testOpaque(): Unit = {
    val fileContent =
      s"""
         |opaque type ${|}Foo = String
         |""".stripMargin

    val expectedContent =
      """
        |<span style="color:#000080;font-weight:bold;">opaque</span><span style="color:#000080;font-weight:bold;">type</span>Foo =<span style="color:#000000;"><a href="psi_element://java.lang.String"><code>String</code></a></span>
        |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testTransparent(): Unit = {
    val fileContent =
      s"""
         |transparent trait ${|}Foo
         |""".stripMargin

    val expectedContent =
      """
        |<span style="color:#000080;font-weight:bold;">transparent</span><span style="color:#000080;font-weight:bold;">trait</span>Foo
        |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testOpen(): Unit = {
    val fileContent =
      s"""
         |open class ${|}Foo
         |""".stripMargin

    val expectedContent =
      """
        |<span style="color:#000080;font-weight:bold;">open</span><span style="color:#000080;font-weight:bold;">class</span>Foo
        |""".stripMargin

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
        |<span style="color:#000080;font-weight:bold;">def</span>
        |comp(str2:<span style="color:#000000;"><a href="psi_element://java.lang.String"><code>String</code></a></span>):
        |<span style="color:#000000;"><a href="psi_element://scala.Boolean"><code>Boolean</code></a></span>
        |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testUsing(): Unit = {
    val fileContent =
      s"""
         |import scala.concurrent.ExecutionContext
         |def ${|}f()(using ec: ExecutionContext): Int = ???
         |""".stripMargin

    val expectedContent =
      """
        |<span style="color:#000080;font-weight:bold;">def</span>
        |f()(<span style="color:#000080;font-weight:bold;">using</span>ec:<span style="color:#000000;"><a href="psi_element://scala.concurrent.ExecutionContext"><code>ExecutionContext</code></a></span>):
        |<span style="color:#000000;"><a href="psi_element://scala.Int"><code>Int</code></a></span>
        |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testGiven(): Unit = {
    val fileContent =
      s"""
         |import scala.concurrent.ExecutionContext
         |given ${|}ec: ExecutionContext = ???
         |""".stripMargin

    val expectedContent =
      """
        |<span style="color:#000080;font-weight:bold;">given</span>
        |ec:<span style="color:#000000;"><a href="psi_element://scala.concurrent.ExecutionContext"><code>ExecutionContext</code></a></span>
        |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }
}
