package org.jetbrains.plugins.scala.editor.documentationProvider

import org.jetbrains.plugins.scala.editor.documentationProvider.util.{ScalaDocumentationsBodySectionTesting, ScalaDocumentationsScalaDocContentTesting}

final class ScalaDocumentationProviderTest_ScalaDocParamLinks extends ScalaDocumentationProviderTestBase
  with ScalaDocumentationsBodySectionTesting
  with ScalaDocumentationsScalaDocContentTesting {

  def testParamWithLink(): Unit = {
    val fileText =
      s"""/**
         | * @param x see [[B]]
         | */
         |case class C(${|}x: String)
         |class B {
         |  def b(s: Int): String = {
         |    s.toString
         |  }
         |}
         |""".stripMargin
    val expectedDoc =
      s"""<tr><td valign='top' class='section'><p>Params:</td>
         |<td valign='top'>x &ndash; see <a href="psi_element://B"><code>B</code></a></td>
         |""".stripMargin
    doGenerateDocSectionsTest(fileText, expectedDoc)
  }

  def testParamWithLinkInMiddle(): Unit = {
    val fileText =
      s"""/**
         | * @param x text before [[B]] text after
         | */
         |case class C(${|}x: String)
         |class B {
         |  def b(s: Int): String = {
         |    s.toString
         |  }
         |}
         |""".stripMargin
    val expectedDoc =
      s"""<tr><td valign='top' class='section'><p>Params:</td>
         |<td valign='top'>x &ndash; text before <a href="psi_element://B"><code>B</code></a> text after</td>
         |""".stripMargin
    doGenerateDocSectionsTest(fileText, expectedDoc)
  }

  def testParamWithMultipleLinks(): Unit = {
    val fileText =
      s"""/**
         | * @param x text before [[B]] middle text [[A]] text after
         | */
         |case class C(${|}x: String)
         |class A {}
         |class B {
         |  def b(s: Int): String = {
         |    s.toString
         |  }
         |}
         |""".stripMargin
    val expectedDoc =
      s"""<tr><td valign='top' class='section'><p>Params:</td>
         |<td valign='top'>x &ndash; text before <a href="psi_element://B"><code>B</code></a> middle text <a href="psi_element://A"><code>A</code></a> text after</td>
         |""".stripMargin
    doGenerateDocSectionsTest(fileText, expectedDoc)
  }
}