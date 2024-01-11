package org.jetbrains.plugins.scala.editor.documentationProvider

import org.jetbrains.plugins.scala.editor.documentationProvider.util.{ScalaDocumentationsBodySectionTesting, ScalaDocumentationsScalaDocContentTesting}
import org.jetbrains.plugins.scala.util.AliasExports._

final class ScalaDocumentationProviderTest_ScalaDocContent extends ScalaDocumentationProviderTestBase
  with ScalaDocumentationsBodySectionTesting
  with ScalaDocumentationsScalaDocContentTesting {

  def testMethod(): Unit =
    doGenerateDocBodyTest(
      s"""class A {
         |  /** description of foo */
         |  def ${|}foo: String = ???
         |}""".stripMargin,
      s"""
         |$DefinitionStart<a href="psi_element://A"><code>A</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">foo</span>: <span style="color:#000000;"><a href="psi_element://java.lang.String"><code>String</code></a></span>$DefinitionEnd
         |${ContentStart}description of foo$ContentEnd
         |""".stripMargin
    )

  def testMethod_Overriding(): Unit = {
    myFixture.addFileToProject("BaseScalaClass.scala",
      s"""class BaseScalaClass {
         |  /** description of base method from BaseScalaClass */
         |  def baseMethod: String = ???
         |}
         |""".stripMargin
    )
    doGenerateDocBodyTest(
      s"""class A extends BaseScalaClass {
         |  /** description of base method from A */
         |  def ${|}baseMethod: String = ???
         |}
         |""".stripMargin,
      s"""
         |$DefinitionStart<a href="psi_element://A"><code>A</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">baseMethod</span>: <span style="color:#000000;"><a href="psi_element://java.lang.String"><code>String</code></a></span>$DefinitionEnd
         |${ContentStart}description of base method from A$ContentEnd
         |""".stripMargin
    )
  }

  def testMethod_WithEmptyDoc_Overriding(): Unit = {
    myFixture.addFileToProject("BaseScalaClass.scala",
      s"""class BaseScalaClass {
         |  /** description of base method from BaseScalaClass */
         |  def baseMethod: String = ???
         |}
         |""".stripMargin
    )

    // TODO: do we need override keyword as text in <pre> section?
    //  Java uses `Overrides` section for that (e.g. Overrides: foo in class BaseClass)
    //  Maybe we leave the keyword but also add a "Overrides" section?
    doGenerateDocBodyTest(
      s"""class A extends BaseScalaClass {
         |  override def ${|}baseMethod: String = ???
         |}""".stripMargin,
      s"""
         |$DefinitionStart<a href="psi_element://A"><code>A</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">override</span> <span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">baseMethod</span>: <span style="color:#000000;"><a href="psi_element://java.lang.String"><code>String</code></a></span>$DefinitionEnd
         |$ContentStart
         |<b>Description copied from class: </b>
         |<a href="psi_element://BaseScalaClass"><code>BaseScalaClass</code></a>
         |$ContentEnd
         |$ContentStart
         |description of base method from BaseScalaClass
         |$ContentEnd
         |""".stripMargin
    )
  }

  def testMethod_WithEmptyDoc_OverridingJavaMethod(): Unit = {
    myFixture.addFileToProject("BaseJavaClass.java",
      s"""public class BaseJavaClass {
         |  /** description of base method from BaseJavaClass */
         |  String baseMethod() { return null; }
         |}
         |""".stripMargin
    )
    doGenerateDocBodyTest(
      s"""class A extends BaseJavaClass {
         |  override def ${|}baseMethod: String = ???
         |}
         |""".stripMargin,
      s"""$DefinitionStart<a href="psi_element://A"><code>A</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">override</span> <span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">baseMethod</span>: <span style="color:#000000;"><a href="psi_element://java.lang.String"><code>String</code></a></span>$DefinitionEnd
         |$ContentStart
         |<b>Description copied from class: </b>
         |<a href="psi_element://BaseJavaClass"><code>BaseJavaClass</code></a>
         |$ContentEnd
         |$ContentStart description of base method from BaseJavaClass $ContentEnd
         |$SectionsStart$SectionsEnd
         |""".stripMargin
    )
  }

  def testMethod_WithEmptyDoc_OverridingJavaMethod_TagsInJavadoc(): Unit = {
    myFixture.addFileToProject("BaseJavaClass.java",
      s"""public class BaseJavaClass {
         |  /** @return modules to compile before run. Empty list to build project */
         |  String[] getModules() {  return null;  }
         |}""".stripMargin
    )
    doGenerateDocBodyTest(
      s"""class A extends BaseJavaClass {
         |  override def ${|}getModules: String = ???
         |}
         |""".stripMargin,
      s"""
         |$DefinitionStart<a href="psi_element://A"><code>A</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">override</span> <span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">getModules</span>: <span style="color:#000000;"><a href="psi_element://java.lang.String"><code>String</code></a></span>$DefinitionEnd
         |$ContentStart
         |<b>Description copied from class: </b>
         |<a href="psi_element://BaseJavaClass"><code>BaseJavaClass</code></a>
         |$ContentEnd
         |$SectionsStart
         |<p><tr>
         |<td valign='top' class='section'><p>Returns:</td>
         |<td valign='top'><p>modules to compile before run. Empty list to build project </td>
         |$SectionsEnd""".stripMargin
    )
  }

  def testMethod_HigherKindedTypeParameters_ReferToParameterInExtendsList(): Unit = {
    val input1  =
      s"""trait Trait1[A]
         |trait Trait2[A, CC[X] <: Seq[X]]
         |extends Trait1[CC[A]]
         |val ${|}x: Trait2[_, _] = ???""".stripMargin
    val expectedDoc = {
      s"""$DefinitionStart<span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-style:italic;">x</span>: <span style="color:#000000;"><a href="psi_element://Trait2"><code>Trait2</code></a></span>[_, _]$DefinitionEnd
         |""".stripMargin
    }
    doGenerateDocBodyTest(input1, expectedDoc)
  }

  private def sharedValVarDefinition(definitionBody: String): String = {
    s"""class X {
       |  /**
       |   * some description
       |   *
       |   * @note some note
       |   */
       |  $definitionBody
       |}""".stripMargin
  }

  private def doValVarTest(definitionBody: String, expectedDocDefinitionSection: String): Unit =
    doGenerateDocBodyTest(
      sharedValVarDefinition(definitionBody),
      s"""$DefinitionStart$expectedDocDefinitionSection$DefinitionEnd
         |${ContentStart}some description$ContentEnd
         |$SectionsStart
         |<tr><td valign='top' class='section'><p>Note:</td><td valign='top'>some note</td>
         |$SectionsEnd""".stripMargin
    )

  def testMemberValue(): Unit =
    doValVarTest(
      s"""val ${|}v = 1""",
      s"""<a href="psi_element://X"><code>X</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-style:italic;">v</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>
         |""".stripMargin.trim
    )

  def testMemberVariable(): Unit =
    doValVarTest(
      s"""var ${|}v = 1""",
      s"""<a href="psi_element://X"><code>X</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">var</span> <span style="color:#660e7a;font-style:italic;">v</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>
         |""".stripMargin.trim
    )

  def testMemberValuePattern(): Unit =
    doValVarTest(
      s"""val (v1, ${|}v2) = (1, "str")""",
      s"""<a href="psi_element://X"><code>X</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-style:italic;">v2</span>: <span style="color:#000000;"><a href="psi_element://java.lang.String"><code>String</code></a></span>
         |""".stripMargin.trim
    )

  def testMemberValuePattern_1(): Unit =
    doValVarTest(
      s"""val Tuple2(v1, ${|}v2) = (1, "str")""",
      // java.lang.String cause it's an inferred type
      s"""<a href="psi_element://X"><code>X</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-style:italic;">v2</span>: <span style="color:#000000;"><a href="psi_element://java.lang.String"><code>String</code></a></span>
         |""".stripMargin.trim
    )

  def testMemberVariablePattern(): Unit =
    doValVarTest(
      s"""var (v1, ${|}v2) = (1, "str")""",
      s"""<a href="psi_element://X"><code>X</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">var</span> <span style="color:#660e7a;font-style:italic;">v2</span>: <span style="color:#000000;"><a href="psi_element://java.lang.String"><code>String</code></a></span>
         |""".stripMargin.trim
    )

  def testMemberVariablePattern_1(): Unit =
    doValVarTest(
      s"""var Tuple2(v1, ${|}v2) = (1, "str")""",
      s"""<a href="psi_element://X"><code>X</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">var</span> <span style="color:#660e7a;font-style:italic;">v2</span>: <span style="color:#000000;"><a href="psi_element://java.lang.String"><code>String</code></a></span>
         |""".stripMargin.trim
    )

  def testMemberValue_Abstract(): Unit =
    doValVarTest(
      s"""val ${|}v: Int""",
      s"""<a href="psi_element://X"><code>X</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-style:italic;">v</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>
         |""".stripMargin.trim
    )

  def testMemberVariable_Abstract(): Unit =
    doValVarTest(
      s"""var ${|}v: Int""",
      s"""<a href="psi_element://X"><code>X</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">var</span> <span style="color:#660e7a;font-style:italic;">v</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>
         |""".stripMargin.trim
    )

  def testAllTags(): Unit = {
    // Should only add to sections these tags in the same order:
    // @deprecated @param, @tparam, @return, @throws
    // @example @note @see @since @to-do
    val fileText    =
    s"""class AllTags {
       |  /**
       |   * @see some text
       |   * @author some text
       |   * @note some text
       |   * @since some text
       |   * @define some text
       |   * @version some text
       |   * @todo some text
       |   * @usecase some text
       |   * @example some text
       |   * @deprecated some text
       |   * @migration some text
       |   * @group some text
       |   * @groupname some text
       |   * @groupdesc some text
       |   * @groupprio some text
       |   * @constructor some text
       |   * @return some text
       |   * @tparam T some text
       |   * @throws Exception some text
       |   * @param p some text
       |   */
       |  def ${|}foo[T](p: String) = 42
       |}
       |""".stripMargin

    val expectedDoc =
      s"""<tr><td valign='top' class='section'><p>Deprecated</td>
         |<td valign='top'>some text</td>
         |<tr><td valign='top' class='section'><p>Params:</td>
         |<td valign='top'>p &ndash; some text</td>
         |<tr><td valign='top' class='section'><p>Type parameters:</td>
         |<td valign='top'>T &ndash; some text</td>
         |<tr><td valign='top' class='section'><p>Returns:</td>
         |<td valign='top'>some text</td>
         |<tr><td valign='top' class='section'><p>Throws:</td>
         |<td valign='top'><a href="psi_element://$exceptionClass"><code>Exception</code></a> &ndash; some text</td>
         |<tr><td valign='top' class='section'><p>Note:</td>
         |<td valign='top'>some text</td>
         |<tr><td valign='top' class='section'><p>Example:</td>
         |<td valign='top'>some text</td>
         |<tr><td valign='top' class='section'><p>See also:</td>
         |<td valign='top'>some text</td>
         |<tr><td valign='top' class='section'><p>Since:</td>
         |<td valign='top'>some text</td>
         |<tr><td valign='top' class='section'><p>Todo:</td>
         |<td valign='top'>some text</td>
         |""".stripMargin
    doGenerateDocSectionsTest(fileText, expectedDoc)
  }

  //note strong requirement, just fixating current behaviour
  def testTags_AuthorTagShouldBeIgnored(): Unit = {
    val fileText =
      s"""/**
         |  * Description
         |  * @author Some Name 1
         |  * @author Some Name 2
         |  */
         |class ${|}A {}
         |""".stripMargin
    val expectedDoc =
      s"""
         |$DefinitionStart<span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">A</span>$DefinitionEnd
         |${ContentStart}Description$ContentEnd
         |""".stripMargin

    doGenerateDocBodyTest(
      fileText,
      expectedDoc
    )
  }

  def testTags_ParamsForMethod(): Unit = {
    val fileText =
      s"""/**
         | * @param i aaa
         | * @param j bbb
         | * @param k ccc
         | * @tparam T ddd
         | * @tparam E eee
         | *           ggg
         | */
         |def ${|}f[T, E](i: Int, j: Int, k: String) {}
         |""".stripMargin
    val expectedDoc =
      s"""<tr><td valign='top' class='section'><p>Params:</td>
         |<td valign='top'>i &ndash; aaa<p>j &ndash; bbb<p>k &ndash; ccc</td>
         |<tr><td valign='top' class='section'><p>Type parameters:</td>
         |<td valign='top'>
         |T &ndash; ddd<p>
         |E &ndash; eee ggg</td>
         |""".stripMargin
    doGenerateDocSectionsTest(fileText, expectedDoc)
  }

  def testTags_Throws(): Unit = {
    val fileText =
      s"""/**
         |  * @throws Exception some condition 1
         |  * @throws java.lang.IllegalAccessException some condition 2
         |  * @throws java.util.ConcurrentModificationException some condition 3
         |  */
         |def ${|}g() {}
         |""".stripMargin
    val expectedDoc =
      s"""<tr><td valign='top' class='section'><p>Throws:</td>
         |<td valign='top'>
         |<a href="psi_element://$exceptionClass"><code>Exception</code></a>
         | &ndash; some condition 1
         |<p><a href="psi_element://java.lang.IllegalAccessException"><code>IllegalAccessException</code></a>
         | &ndash; some condition 2
         |<p><a href="psi_element://java.util.ConcurrentModificationException"><code>java.util.ConcurrentModificationException</code></a>
         | &ndash; some condition 3</td>
         |""".stripMargin
    doGenerateDocSectionsTest(fileText, expectedDoc)
  }

  def testTags_Throws_ShouldUseShortestExceptionName(): Unit = {
    val fileText =
      s"""import java.util._
         |/** @throws java.util.ConcurrentModificationException some condition */
         |def ${|}g() {}
         |""".stripMargin
    val expectedDoc =
      s"""<tr><td valign='top' class='section'><p>Throws:</td>
         |<td valign='top'>
         |<a href="psi_element://java.util.ConcurrentModificationException"><code>ConcurrentModificationException</code></a>
         | &ndash; some condition</td>
         |""".stripMargin
    doGenerateDocSectionsTest(fileText, expectedDoc)
  }

  def testTags_Throws_ShouldEscapeExceptionName(): Unit = {
    val fileText =
      s"""import java.util._
         |class <:::< extends Exception
         |/** @throws <:::< some condition */
         |def ${|}g() {}
         |""".stripMargin
    val expectedDoc =
      s"""<tr><td valign='top' class='section'><p>Throws:</td>
         |<td valign='top'>
         |<a href="psi_element://<:::<"><code>&lt;:::&lt;</code></a>
         | &ndash; some condition</td>
         |""".stripMargin
    doGenerateDocSectionsTest(fileText, expectedDoc)
  }

  def testTags_ParamsForClass(): Unit = {
    val fileText =
      s"""/**
         |  * @param o
         |  * @tparam E
         |  * @param f description for f
         |  * @tparam K description for K
         |  */
         |class ${|}A[E, K](o: Any, f: AnyVal) {}
         |""".stripMargin
    val expectedDoc  =
      s"""<tr><td valign='top' class='section'><p>Params:</td>
         |<td valign='top'>o &ndash; <p>f &ndash; description for f</td>
         |<tr><td valign='top' class='section'><p>Type parameters:</td>
         |<td valign='top'>E &ndash; <p>K &ndash; description for K</td>
         |""".stripMargin

    doGenerateDocSectionsTest(fileText, expectedDoc)
  }

  def testTags_ParamsForTypeAlias(): Unit = {
    val fileText =
      s"""/**
         |  * @tparam A
         |  * @tparam B    description for B
         |  * @tparam C
         |  */
         |type ${|}myType[A, B, C] = java.util.HashMap[A, java.util.HashMap[B, C]]
         |""".stripMargin
    val expectedDoc  =
      s"""<tr><td valign='top' class='section'><p>Type parameters:</td>
         |<td valign='top'>A &ndash;  <p>B &ndash; description for B<p>C &ndash; </td>""".stripMargin
    doGenerateDocSectionsTest(fileText, expectedDoc)
  }

  def testTags_ParamInUse(): Unit = {
    val fileText =
      s"""
         |/**
         |  * Method description
         |  *
         |  * @param initB initial boolean
         |  * @return a sequence of ints
         |  */
         |def foo(initB: Boolean): Seq[Int] = {
         |  var b = ${|}initB
         |  if (b) Vector(1) else List(1)
         |}
         |""".stripMargin
    val expectedDoc = "initial boolean"
    doGenerateDocSectionsTest(fileText, expectedDoc)
  }

  def testTags_ValueInUse(): Unit = {
    val fileText =
      s"""
         |def foo(initB: Boolean): Seq[Int] = {
         |  /**
         |    * this is a boolean
         |    */
         |  val b = initB
         |  if (${|}b) Vector(1) else List(1)
         |}
         |""".stripMargin
    val expectedDoc =
      s"""
         |$DefinitionStart<span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-style:italic;">b</span>: <span style=""><a href="psi_element://scala.Boolean"><code>Boolean</code></a></span>$DefinitionEnd
         |$ContentStart
         |this is a boolean
         |$ContentEnd
         |""".stripMargin
    doGenerateDocBodyTest(fileText, expectedDoc)
  }

  def testTags_NestedMethodInUse(): Unit = {
    val fileText =
      s"""
         |def foo(): Int = {
         |  /**
         |    * Increments the number
         |    * @param n the number to be incremented
         |    * @return the incremented number
         |    */
         |  def inc(n: Int): Int = n + 1
         |
         |  ${|}inc(2)
         |}
         |""".stripMargin
    val expectedDoc =
      s"""
         |<tr><td valign='top' class='section'><p>Params:</td>
         |<td valign='top'>n &ndash; the number to be incremented</td>
         |<tr><td valign='top' class='section'><p>Returns:</td><td valign='top'>the incremented number</td>
         |""".stripMargin
    doGenerateDocSectionsTest(fileText, expectedDoc)
  }

  def testTags_VariableInUse(): Unit = {
    val fileText =
      s"""
         |def foo(initB: Boolean): Seq[Int] = {
         |  /**
         |    * this is a boolean
         |    */
         |  var b = initB
         |  if (${|}b) Vector(1) else List(1)
         |}
         |""".stripMargin
    val expectedDoc =
      s"""
         |$DefinitionStart<span style="color:#000080;font-weight:bold;">var</span> <span style="color:#660e7a;font-style:italic;">b</span>: <span style=""><a href="psi_element://scala.Boolean"><code>Boolean</code></a></span>$DefinitionEnd
         |$ContentStart
         |this is a boolean
         |$ContentEnd
         |""".stripMargin
    doGenerateDocBodyTest(fileText, expectedDoc)
  }

  def testTags_TupleInUse(): Unit = {
    val fileText =
      s"""
         |/**
         |  * some description
         |  *
         |  * @note some note
         |  */
         |val (v1, v2) = (1, "str")
         |
         |val v = Vector(${|}v1)
         |""".stripMargin
    val expectedDoc =
      s"""
         |$DefinitionStart<span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-style:italic;">v1</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>$DefinitionEnd
         |$ContentStart
         |some description
         |$ContentEnd
         |$SectionsStart
         |<tr><td valign='top' class='section'><p>Note:</td><td valign='top'>some note</td>
         |$SectionsEnd
         |""".stripMargin
    doGenerateDocBodyTest(fileText, expectedDoc)
  }


  def testFontStyles_Nested_Underscore_Power_Italic(): Unit =
    doGenerateDocBodyTest(
      s"""/**
         | * __xxx^yyy''zzz''yyy^xxx__
         | */
         |val ${|}a = 1
         |""".stripMargin,
      s"""$DefinitionStart<span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-style:italic;">a</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>$DefinitionEnd
         |$ContentStart<u>xxx<sup>yyy<i>zzz</i>yyy</sup>xxx</u>$ContentEnd
         |""".stripMargin
    )

  def testFontStyles_InTags(): Unit = {
    val fileText =
      s"""/**
         | * @note '''__bold with underscore text__'''
         | * @see abc,,index text __index with underscore text__ index text 2,,def
         | */
         |def ${|}f() {}
         |""".stripMargin

    val expectedDoc  =
      s"""<tr><td valign='top' class='section'><p>Note:</td>
         |<td valign='top'><b><u>bold with underscore text</u></b></td>
         |<tr><td valign='top' class='section'><p>See also:</td>
         |<td valign='top'>abc<sub>index text <u>index with underscore text</u> index text 2</sub>def</td>""".stripMargin

    doGenerateDocSectionsTest(fileText, expectedDoc)
  }

  def testHttpLinks(): Unit = {
    val fileText =
      s"""/**
         | * [[http://example.org]]<br>
         | * [[http://example.org    ]]<br>
         | * [[  http://example.org]]<br>
         | * [[  http://example.org    ]]<br>
         | */
         |val ${|}a = 1
         |""".stripMargin
    val expectedDoc =
      s"""
         |$DefinitionStart<span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-style:italic;">a</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>$DefinitionEnd
         |$ContentStart
         |<a href="http://example.org">http://example.org</a><br>
         | <a href="http://example.org">http://example.org</a><br>
         | <a href="http://example.org">http://example.org</a><br>
         | <a href="http://example.org">http://example.org</a><br>
         |$ContentEnd""".stripMargin
    doGenerateDocBodyTest(fileText, expectedDoc)
  }

  def testHttpLinks_WithDescription_Simple(): Unit = {
    val fileText =
      s"""/**
         | * [[http://example.org   label]]<br>
         | * [[http://example.org   label  ]]<br>
         | * [[http://example.org   label with   spaces    ]]<br>
         | * [[  http://example.org   label with   spaces    ]]<br>
         | */
         |val ${|}a = 1
         |""".stripMargin
    val expectedDoc =
      s"""$DefinitionStart<span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-style:italic;">a</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>$DefinitionEnd
         |$ContentStart<a href="http://example.org">label</a><br>
         | <a href="http://example.org">label </a><br>
         | <a href="http://example.org">label with spaces </a><br>
         | <a href="http://example.org">label with spaces </a><br>
         |$ContentEnd""".stripMargin
    doGenerateDocBodyTest(fileText, expectedDoc)
  }

  def testHttpLinks_WithDescription_WithMarkupSyntax(): Unit = {
    val fileText =
      s"""/**
         | * [[http://example.org   '''label with markdown text 1''']]<br>
         | * [[http://example.org   label '''with markdown text 2'''  ]]<br>
         | * [[http://example.org   '''label with markdown''' text 3  ]]<br>
         | * [[http://example.org   label '''with markdown''' text 4  ]]<br>
         | * [[http://example.org   label '''__with nested__ markdown ''' text 5]]<br>
         | * [[  http://example.org   label '''__with nested__ markdown''' text 6 ]]<br>
         | */
         |val ${|}a = 1
         |""".stripMargin
    val expectedDoc = {
      s"""
         |$DefinitionStart<span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-style:italic;">a</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>$DefinitionEnd
         |$ContentStart
         |<a href="http://example.org"><b>label with markdown text 1</b></a><br>
         | <a href="http://example.org">label <b>with markdown text 2</b> </a><br>
         | <a href="http://example.org"><b>label with markdown</b> text 3 </a><br>
         | <a href="http://example.org">label <b>with markdown</b> text 4 </a><br>
         | <a href="http://example.org">label <b><u>with nested</u> markdown </b> text 5</a><br>
         | <a href="http://example.org">label <b><u>with nested</u> markdown</b> text 6 </a><br>
         |$ContentEnd""".stripMargin
    }
    doGenerateDocBodyTest(fileText, expectedDoc)
  }

  def testHttpLinks_WithMarkupSyntaxOutside(): Unit = {
    val fileText =
      s"""/**
         | * ,,__[[http://example.org]]__,,<br>
         | * ,,__[[http://example.org   label text]]__,,<br>
         | * [[http://example.org]] ^[[http://example.org]]^<br>
         | * [[http://example.org  label  1 ]] ^[[http://example.org label 2]]^<br>
         | */
         |val ${|}a = 1
         |""".stripMargin
    val expectedDoc =
      s"""$DefinitionStart<span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-style:italic;">a</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>$DefinitionEnd
         |$ContentStart
         |<sub><u><a href="http://example.org">http://example.org</a></u></sub><br>
         | <sub><u><a href="http://example.org">label text</a></u></sub><br>
         | <a href="http://example.org">http://example.org</a> <sup><a href="http://example.org">http://example.org</a></sup><br>
         | <a href="http://example.org">label  1 </a> <sup><a href="http://example.org">label 2</a></sup><br>
         |$ContentEnd""".stripMargin
    doGenerateDocBodyTest(fileText, expectedDoc)
  }

  def testHttpLink_WithValidEqualSignInside(): Unit =
    doGenerateDocContentTest(
      s"""/** [[https://example.org?at=location]] */
         |val ${|}a = 1
         |""".stripMargin,
      s"""<a href="https://example.org?at=location">https://example.org?at=location</a>""".stripMargin
    )

  def testEmptyLink(): Unit = {
    val fileText =
      s"""/**
         | * [[]]<br>
         | * [[    ]]<br>
         | */
         |val ${|}a = 1
         |""".stripMargin
    val expectedDoc =
      s"""<br> <br>""".stripMargin
    doGenerateDocContentTest(fileText, expectedDoc)
  }

  def testCodeLinks_ToClass_WithoutCompanionObject(): Unit = {
    val fileText =
      s"""/**
         | * [[scala.util.DynamicVariable]]<br>
         | * [[scala.util.DynamicVariable    ]]<br>
         | * [[  scala.util.DynamicVariable]]<br>
         | * [[  scala.util.DynamicVariable    ]]<br>
         | */
         |val ${|}a = 1
         |""".stripMargin
    val expectedDoc =
      s"""<a href="psi_element://scala.util.DynamicVariable"><code>scala.util.DynamicVariable</code></a><br>
         | <a href="psi_element://scala.util.DynamicVariable"><code>scala.util.DynamicVariable</code></a><br>
         | <a href="psi_element://scala.util.DynamicVariable"><code>scala.util.DynamicVariable</code></a><br>
         | <a href="psi_element://scala.util.DynamicVariable"><code>scala.util.DynamicVariable</code></a><br>
         |""".stripMargin
    doGenerateDocContentTest(fileText, expectedDoc)
  }

  def testCodeLinks_ToClass_WithCompanionObject(): Unit = {
    val fileText =
      s"""/**
         | * [[scala.util.Try]]<br>
         | */
         |val ${|}a = 1
         |""".stripMargin
    val expectedDoc =
      """<a href="psi_element://scala.util.Try"><code>scala.util.Try</code></a><br>
        |""".stripMargin
    doGenerateDocContentTest(fileText, expectedDoc)
  }

  def testCodeLinks_ToObject_WithCompanionCLass(): Unit = {
    return
    val fileText =
      s"""/**
         | * [[scala.util.Try$$]]<br>
         | */
         |val ${|}a = 1
         |""".stripMargin
    val expectedDoc =
      """<a href="psi_element://scala.util.Try$"><code>scala.util.DynamicVariable$</code></a><br>
        |""".stripMargin
    doGenerateDocContentTest(fileText, expectedDoc)
  }

  def testCodeLinks_ToObject_WithoutCompanionClass(): Unit = {
    val fileText =
      s"""/**
         | * [[scala.util.Properties]]<br>
         | */
         |val ${|}a = 1
         |""".stripMargin
    val expectedDoc =
      """<a href="psi_element://scala.util.Properties"><code>scala.util.Properties</code></a><br>
        |""".stripMargin
    doGenerateDocContentTest(fileText, expectedDoc)
  }

  def testCodeLinks_ToObject_WithoutCompanionClass_1(): Unit = {
    // TODO: implement support of ref to companion objects
    return
    val fileText =
      s"""/**
         | * [[scala.util.Properties$$]]<br>
         | */
         |val ${|}a = 1
         |""".stripMargin
    val expectedDoc =
      """<a href="psi_element://scala.util.Properties$"><code>scala.util.Properties$</code></a><br>
        |""".stripMargin
    doGenerateDocContentTest(fileText, expectedDoc)
  }

  def testCodeLinks_WithDescription_Simple(): Unit = {
    val fileText =
      s"""/**
         | * [[scala.util.DynamicVariable   label]]<br>
         | * [[scala.util.DynamicVariable   label  ]]<br>
         | * [[scala.util.DynamicVariable   label with   spaces    ]]<br>
         | * [[  scala.util.DynamicVariable   label with   spaces    ]]<br>
         | */
         |val ${|}a = 1
         |""".stripMargin
    val expectedDoc =
      s"""<a href="psi_element://scala.util.DynamicVariable"><code>label</code></a><br>
         | <a href="psi_element://scala.util.DynamicVariable"><code>label </code></a><br>
         | <a href="psi_element://scala.util.DynamicVariable"><code>label with   spaces </code></a><br>
         | <a href="psi_element://scala.util.DynamicVariable"><code>label with   spaces </code></a><br>
         |""".stripMargin
    doGenerateDocContentTest(fileText, expectedDoc)
  }

  def testCodeLinks_WithDescription_WithMarkupSyntax(): Unit = {
    val fileText =
      s"""/**
         | * [[scala.util.DynamicVariable   '''label with markdown text''']]<br>
         | * [[scala.util.DynamicVariable   label '''with markdown text''']]<br>
         | * [[scala.util.DynamicVariable   '''label with markdown''' text]]<br>
         | * [[scala.util.DynamicVariable   label '''with markdown''' text]]<br>
         | * [[scala.util.DynamicVariable   label '''__with nested__ markdown''' text]]<br>
         | * [[  scala.util.DynamicVariable   label '''__with nested__ markdown''' text  ]]<br>
         | * [[  scala.util.DynamicVariable   label '''__with nested__ markdown''' text and special chars >>> <<< ]]<br>
         | */
         |val ${|}a = 1
         |""".stripMargin
    val expectedDoc =
      s"""<a href="psi_element://scala.util.DynamicVariable"><code><b>label with markdown text</b></code></a><br>
         | <a href="psi_element://scala.util.DynamicVariable"><code>label <b>with markdown text</b></code></a><br>
         | <a href="psi_element://scala.util.DynamicVariable"><code><b>label with markdown</b> text</code></a><br>
         | <a href="psi_element://scala.util.DynamicVariable"><code>label <b>with markdown</b> text</code></a><br>
         | <a href="psi_element://scala.util.DynamicVariable"><code>label <b><u>with nested</u> markdown</b> text</code></a><br>
         | <a href="psi_element://scala.util.DynamicVariable"><code>label <b><u>with nested</u> markdown</b> text  </code></a><br>
         | <a href="psi_element://scala.util.DynamicVariable"><code>label <b><u>with nested</u> markdown</b> text and special chars >>> <<< </code></a><br>
         |""".stripMargin
    doGenerateDocContentTest(fileText, expectedDoc)
  }

  def testCodeLinks_WithMarkupSyntaxOutside(): Unit = {
    val fileText =
      s"""/**
         | * ,,__[[scala.util.DynamicVariable]]__,,<br>
         | * ,,__[[scala.util.DynamicVariable   label text]]__,,<br>
         | * [[scala.util.DynamicVariable]] ^[[scala.util.DynamicVariable]]^<br>
         | * [[scala.util.DynamicVariable  label  1 ]] ^[[scala.util.DynamicVariable label 2]]^<br>
         | */
         |val ${|}a = 1
         |""".stripMargin
    val expectedDoc =
      s"""<sub><u><a href="psi_element://scala.util.DynamicVariable"><code>scala.util.DynamicVariable</code></a></u></sub><br>
         | <sub><u><a href="psi_element://scala.util.DynamicVariable"><code>label text</code></a></u></sub><br>
         | <a href="psi_element://scala.util.DynamicVariable"><code>scala.util.DynamicVariable</code></a>
         | <sup><a href="psi_element://scala.util.DynamicVariable"><code>scala.util.DynamicVariable</code></a></sup><br>
         | <a href="psi_element://scala.util.DynamicVariable"><code>label  1 </code></a> <sup><a href="psi_element://scala.util.DynamicVariable"><code>label 2</code></a></sup><br>
         |""".stripMargin
    doGenerateDocContentTest(fileText, expectedDoc)
  }

  def testCodeLinks_Unresolved(): Unit = {
    val fileText =
      s"""/**
         | * [[org.Unresolved]]<br>
         | * [[org.Unresolved description __with markup__]]<br>
         | */
         |val ${|}a = 1
         |""".stripMargin
    val expectedDoc =
      s"""<code>org.Unresolved</code><br>
         | <code>description <u>with markup</u></code><br>
         |""".stripMargin
    doGenerateDocContentTest(fileText, expectedDoc)
  }

  def testCodeLinks_Mixed_UseShortestNameInTheContext(): Unit = {
    val fileText =
      s"""import scala.util.DynamicVariable
         |
         |/**
         | * [[DynamicVariable]]<br>
         | * [[scala.util.DynamicVariable]]<br>
         | * {@link DynamicVariable}<br>
         | * {@link scala.util.DynamicVariable}<br>
         | */
         |class ${|}A
         |""".stripMargin
    val expectedDoc =
      s"""<a href="psi_element://scala.util.DynamicVariable"><code>DynamicVariable</code></a><br>
         | <a href="psi_element://scala.util.DynamicVariable"><code>DynamicVariable</code></a><br>
         | <a href="psi_element://scala.util.DynamicVariable"><code>DynamicVariable</code></a><br>
         | <a href="psi_element://scala.util.DynamicVariable"><code>DynamicVariable</code></a><br>
         |""".stripMargin
    doGenerateDocContentTest(fileText, expectedDoc)
  }

  def testCodeLinks_Mixed_UseShortestNamesFromScalaPredef(): Unit = {
    val fileText =
      s"""/**
         | * [[java.lang.Exception]]<br>
         | * [[Exception]]<br>
         | * [[scala.Exception]]<br>
         | */
         |class ${|}A
         |""".stripMargin
    val expectedDoc =
      s"""<a href="psi_element://java.lang.Exception"><code>${if (aliasExportsEnabled) "Exception" else "java.lang.Exception"}</code></a><br>
         | <a href="psi_element://$exceptionClass"><code>Exception</code></a><br>
         | <a href="psi_element://scala.Exception"><code>${if (aliasExportsEnabled) "scala.Exception" else "Exception"}</code></a><br>
         |""".stripMargin
    doGenerateDocContentTest(fileText, expectedDoc)
  }

  def testCodeLinks_Mixed_EscapeClassNames(): Unit = {
    val fileText =
      s"""class <:::<
         |
         |/**
         | * [[<:::<]]<br>
         | * {@link <:::<}<br>
         | */
         |class ${|}A
         |""".stripMargin
    val expectedDoc =
      s"""<a href="psi_element://<:::<"><code>&lt;:::&lt;</code></a><br>
         | <a href="psi_element://<:::<"><code>&lt;:::&lt;</code></a><br>
         |""".stripMargin
    doGenerateDocContentTest(fileText, expectedDoc)
  }

  def testCodeLinks_UsingNonFullyQualifiedNames(): Unit = {
    myFixture.addFileToProject(
      "MyClass3.scala",
      """package com.example
        |class MyClass3""".stripMargin
    )
    // NOTE: the file has non default package but still is added to the root src dir
    // but looks like it's ok, resolve works as expected
    val fileContent =
    s"""package com
       |package example
       |
       |/**
       | * [[MyClass1]] <br>
       | * [[example.MyClass1]] <br>
       | * [[com.example.MyClass1]] <br>
       | * [[MyClass2]] <br>
       | * [[example.MyClass2]] <br>
       | * [[com.example.MyClass2]] <br>
       | * [[MyClass3]] <br>
       | * [[example.MyClass3]] <br>
       | * [[com.example.MyClass3]] <br>
       | */
       |class ${|}MyClass1
       |class MyClass2
       |""".stripMargin

    val expectedContent =
      """<a href="psi_element://com.example.MyClass1"><code>MyClass1</code></a> <br>
        | <a href="psi_element://com.example.MyClass1"><code>MyClass1</code></a> <br>
        | <a href="psi_element://com.example.MyClass1"><code>MyClass1</code></a> <br>
        | <a href="psi_element://com.example.MyClass2"><code>MyClass2</code></a> <br>
        | <a href="psi_element://com.example.MyClass2"><code>MyClass2</code></a> <br>
        | <a href="psi_element://com.example.MyClass2"><code>MyClass2</code></a> <br>
        | <a href="psi_element://com.example.MyClass3"><code>MyClass3</code></a> <br>
        | <a href="psi_element://com.example.MyClass3"><code>MyClass3</code></a> <br>
        | <a href="psi_element://com.example.MyClass3"><code>MyClass3</code></a> <br>
        |""".stripMargin

    doGenerateDocContentTest(
      fileContent,
      expectedContent
    )
  }

  def testMalformedFontStyles(): Unit =
    doGenerateDocBodyTest(
      s"""/**
         | * ^blah-blah
         | *
         | * __aaaaaaa,,bbbbbbb
         | */
         |val ${|}a = 1
         |""".stripMargin,
      s"""$DefinitionStart<span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-style:italic;">a</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>$DefinitionEnd
         |$ContentStart
         |<sup>blah-blah</sup>
         |<p>
         |<u>aaaaaaa<sub>bbbbbbb</sub></u>
         |</p>
         |$ContentEnd""".stripMargin
    )

  def testMalformedTags(): Unit =
    doGenerateDocBodyTest(
      s"""/**
         |  * @gmm
         |  * @
         |  @see
         |  * @param
         |  * @note aaaaa
         |  */
         |val ${|}a = 1
         |""".stripMargin,
      s"""
         |$DefinitionStart<span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-style:italic;">a</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>$DefinitionEnd
         |$SectionsStart
         |<tr><td valign='top' class='section'><p>Note:</td>
         |<td valign='top'>aaaaa</td>
         |<tr><td valign='top' class='section'><p>See also:</td>
         |<td valign='top'></td>
         |$SectionsEnd""".stripMargin
    )

  def testTags_InheritDoc_SimpleContent(): Unit = {
    val fileText =
      s"""class A {
         |  /**
         |   * Parent description
         |   */
         |  def f = 42
         |}
         |
         |class B extends A {
         |  /**
         |   * Child description
         |   * @inheritdoc
         |   * Extra child description
         |   */
         |  override def ${|}f = 23
         |}
         |""".stripMargin

    val expectedDoc = {
      s"""
         |$DefinitionStart<a href="psi_element://B"><code>B</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">override</span> <span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">f</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>$DefinitionEnd
         |$ContentStart
         |Child description
         |<p>Parent description
         |<p>Extra child description
         |$ContentEnd""".stripMargin
    }
    doGenerateDocBodyTest(fileText, expectedDoc)
  }

  def testTags_InheritDoc_EmptyBaseContent(): Unit = {
    val fileText =
      s"""class A {
         |  def f = 42
         |}
         |
         |class B extends A {
         |  /**
         |   * Child description
         |   * @inheritdoc
         |   * Extra child description
         |   */
         |  override def ${|}f = 23
         |}
         |""".stripMargin

    val expectedDoc =
      s"""$DefinitionStart<a href="psi_element://B"><code>B</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">override</span> <span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">f</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>$DefinitionEnd
         |$ContentStart
         |Child description
         |<p>Extra child description
         |$ContentEnd""".stripMargin
    doGenerateDocBodyTest(fileText, expectedDoc)
  }

  def testTags_InheritDoc_EmptyBaseContent_1(): Unit = {
    val fileText =
      s"""class A {
         |  def f = 42
         |}
         |
         |class B extends A {
         |  /**
         |   * @inheritdoc
         |   * Extra child description
         |   */
         |  override def ${|}f = 23
         |}
         |""".stripMargin

    val expectedDoc =
      s"""
         |$DefinitionStart<a href="psi_element://B"><code>B</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">override</span> <span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">f</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>$DefinitionEnd
         |$ContentStart
         |Extra child description
         |$ContentEnd
         |""".stripMargin
    doGenerateDocBodyTest(fileText, expectedDoc)
  }

  def testTags_InheritDoc_EmptyOwnDescription(): Unit = {
    val fileText =
      s"""class A {
         |  /** Parent description */
         |  def f = 42
         |}
         |
         |class B extends A {
         |  /**
         |   * @inheritdoc
         |   * Extra child description
         |   */
         |  override def ${|}f = 23
         |}
         |""".stripMargin

    val expectedDoc = {
      s"""
         |$DefinitionStart<a href="psi_element://B"><code>B</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">override</span> <span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">f</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>$DefinitionEnd
         |$ContentStart
         |Parent description
         |<p>Extra child description
         |$ContentEnd""".stripMargin
    }
    doGenerateDocBodyTest(fileText, expectedDoc)
  }

  def testTags_InheritDoc_WithMacro(): Unit = {
    val fileText =
      s"""/**
         | *
         | * @define THIS A
         | */
         |class A {
         | /**
         |  * The function f defined in $$THIS returns some integer without no special property. (previously defined in $$PARENT)
         |  * @param i An ignored parameter.
         |  * @return The value $$RESULT.
         |  */
         | def f(i: Int) = 3
         |}
         |
         |/**
         | * @define THIS B
         | * @define PARENT A
         | */
         |class B extends A {
         |  /**
         |   * @inheritdoc
         |   * Some notes on implementation performance, the function runs in O(1).
         |   * @param i An important parameter
         |   */
         |  override def ${|}f(i: Int) = i + 3
         |}
         |""".stripMargin

    val expectedDoc =
      s"""$DefinitionStart<a href="psi_element://B"><code>B</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">override</span> <span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">f</span>(i: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>): <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>$DefinitionEnd
         |$ContentStart
         |The function f defined in B returns some integer without no special property. (previously defined in A)
         |<p>Some notes on implementation performance, the function runs in O(1).
         |$ContentEnd
         |$SectionsStart
         |<tr><td valign='top' class='section'><p>Params:</td>
         |<td valign='top'>i &ndash; An important parameter</td>
         |$SectionsEnd""".stripMargin
    doGenerateDocBodyTest(fileText, expectedDoc)
  }

  def testTags_InheritDoc_FromJavaClass(): Unit = {
    myFixture.addFileToProject("JavaClass.java",
      """/**
        | * text from <b>java</b><br>
        | * {@code code tag}<br>
        | * {@link JavaClass}<br>
        | */
        |public class JavaClass {}
        |""".stripMargin
    )
    val fileText =
      s"""/**
         | * text from __scala__
         | * @inheritdoc
         | * extra text from __scala__
         | */
         |class ${|}ScalaClass extends JavaClass
         |""".stripMargin

    val expectedDoc = {
      s"""
         |$DefinitionStart<span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">ScalaClass</span>
         |<span style="color:#000080;font-weight:bold;">extends</span> <span style="color:#000000;"><a href="psi_element://JavaClass"><code>JavaClass</code></a></span>$DefinitionEnd
         |$ContentStart
         |text from <u>scala</u><p> text from <b>java</b><br>
         | <code style='font-size:100%;'>
         |<span style="">code tag</span>
         |</code><br> <a href="psi_element://JavaClass"><code>JavaClass</code></a>
         |<br> <p>extra text from <u>scala</u>
         |$ContentEnd
         |""".stripMargin
    }
    doGenerateDocBodyTest(fileText, expectedDoc)
  }

  def testMacro_Simple(): Unit =
    doGenerateDocBodyTest(
      s"""/**
         | * @define THIS A
         | */
         |trait A {
         |  /**
         |   * Function defined in $$THIS
         |   */
         |  def ${|}boo()
         |}
         |""".stripMargin,
      s"""
         |$DefinitionStart<a href="psi_element://A"><code>A</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">boo</span>(): <span style=""><a href="psi_element://scala.Unit"><code>Unit</code></a></span>$DefinitionEnd
         |${ContentStart}Function defined in A$ContentEnd
         |""".stripMargin
    )

  def testMacro_ResolveFromContainingClass_ForMethod(): Unit =
    doGenerateDocBodyTest(
      s"""/**
         | * @define KEY1 VALUE1
         | */
         |trait A {
         |  /**
         |   * @define KEY_UNREACHED VALUE_UNREACHED
         |   */
         |  def boo() = 1
         |}
         |
         |/**
         | * @define KEY2 VALUE2
         | */
         |trait B {
         |}
         |
         |class C extends A with B {
         |  /**
         |   * a $$KEY1 b $$KEY2 c $$KEY_UNREACHED
         |   */
         |  override def ${|}boo() = 2
         |}
         |""".stripMargin,
      s"""
         |$DefinitionStart<a href="psi_element://C"><code>C</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">override</span> <span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">boo</span>(): <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>$DefinitionEnd
         |$ContentStart
         |a VALUE1 b VALUE2 c $$KEY_UNREACHED
         |$ContentEnd""".stripMargin
    )

  def testMacro_FromContainingClass_ForMethod_Deep(): Unit =
    doGenerateDocContentTest(
      s"""/**
         | * @define macroOuter outer value
         | */
         |object Outer1 {
         |  class Outer2 {
         |    trait Outer3 {
         |      /**
         |       * $$macroInner
         |       * $$macroOuter
         |       * @define macroInner inner value
         |       */
         |      def ${|}innerMethod: Unit = ???
         |    }
         |  }
         |}""".stripMargin,
      """inner value
        | outer value""".stripMargin
    )

  def testMacro_ResolveFromContainingClass_ForClass(): Unit =
    doGenerateDocContentTest(
      s"""/**
         | * @define macroOuter outer value
         | */
         |class Outer {
         |  /**
         |   * $$macroInner
         |   * $$macroOuter
         |   * @define macroInner inner value
         |   */
         |  class ${|}Inner {
         |  }
         |}""".stripMargin,
      """inner value
        | outer value""".stripMargin
    )

  def testMacro_FromContainingClass_ForClass_Deep(): Unit =
    doGenerateDocContentTest(
      s"""/**
         | * @define macroOuter outer value
         | */
         |object Outer1 {
         |  class Outer2 {
         |    trait Outer3 {
         |      /**
         |       * $$macroInner
         |       * $$macroOuter
         |       * @define macroInner inner value
         |       */
         |      class ${|}Inner {
         |      }
         |    }
         |  }
         |}""".stripMargin,
      """inner value
        | outer value""".stripMargin
    )


  def testMacro_WithBraces(): Unit =
    doGenerateDocContentTest(
      s"""/**
         | * $$myKey
         | * $${myKey}
         | * $${  myKey  }
         | * $${{myKey}}
         | * $${{ myKey }}
         | *
         | * @define myKey my value
         | */
         |class ${|}A""".stripMargin,
      """my value
        | my value
        | ${  myKey  }
        | ${{myKey}}
        | ${{ myKey }}
        |""".stripMargin
    )

  def testMacro_Adjacent(): Unit =
    doGenerateDocContentTest(
      s"""/**
         | * $$myKey$$
         | * $$myKey$$myKey
         | * $$myKey $$myKey
         | *
         | * @define myKey my value
         | */
         |class ${|}A""".stripMargin,
      """my value$
        | my valuemy value
        | my value my value
        |""".stripMargin
    )

  def testNotMacro_EscapedDollar(): Unit =
    doGenerateDocContentTest(
      s"""/**
         | * \\$$myKey
         | * \\$$myKey$$
         | * \\$$myKey\\$$myKey
         | * \\$${myKey}
         | * \\$${myKey}$$
         | * \\$${myKey}\\$$myKey
         | *
         | * @define myKey my value
         | */
         |class ${|}A""".stripMargin,
      """$myKey
        | $myKey$
        | $myKey$myKey
        | ${myKey}
        | ${myKey}$
        | ${myKey}$myKey
        |""".stripMargin
    )

  def testMacro_Wiki(): Unit =
    doGenerateDocBodyTest(
      s"""/**
         | * @define none `None`
         | */
         |class A {
         |  /**
         |   * $$none
         |   */
         |   def ${|}foo() = {}
         |}
         |""".stripMargin,
      s"""$DefinitionStart<a href="psi_element://A"><code>A</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">foo</span>(): <span style=""><a href="psi_element://scala.Unit"><code>Unit</code></a></span>$DefinitionEnd
         |$ContentStart<tt>None</tt>$ContentEnd
         |""".stripMargin
    )

  def testMacro_Undefined(): Unit =
    doGenerateDocContentTest(
      s"""/**
         | * $$myKey
         | * $${myKey}
         | * $${  myKey  }
         | * $${{myKey}}
         | * $${{ myKey }}
         | * $$myKey$$
         | * $$myKey$$myKey
         | */
         |class ${|}A""".stripMargin,
      """$myKey
        | ${myKey}
        | ${  myKey  }
        | ${{myKey}}
        | ${{ myKey }}
        | $myKey$
        | $myKey$myKey
        |""".stripMargin
    )

  def testMacro_Recursive_ShouldNotFail_1(): Unit =
    doGenerateDocContentTest(
      s"""/**
         | * test $$myTag
         | * @define myTag myTag description $$myTag
         | */
         |class ${|}RecursiveDefine
         |""".stripMargin,
      """test myTag description""".stripMargin
    )

  def testMacro_Recursive_ShouldNotFail_2(): Unit =
    doGenerateDocContentTest(
      s"""/**
         | * test $$myTag1 $$myTag2
         | * @define myTag1 myTag1 description $$myTag2
         | * @define myTag2 myTag2 description $$myTag1
         | */
         |class RecursiveDefine2
         |""".stripMargin,
      """test myTag1 description myTag2 description""".stripMargin
    )

  /**
   * NOTE: this test supposed to work with scala 2.12 with a class containing macro from parent {{{
   * /** A base trait for sequences.
   * *  $seqInfo
   * */
   * trait Seq
   * }}}
   */
  def testMacro_FromInheritedLibrarySources_Scala_2_12(): Unit =
    doGenerateDocContentTest(
      s"""abstract class SeqUsageExample2 extends scala.collection.S${|}eq[String]""".stripMargin,
      """A base trait for sequences. Sequences are special cases of iterable collections of class <tt>Iterable</tt>.
        | Unlike iterables, sequences always have a defined order of elements. Sequences provide a method <tt>apply</tt> for indexing.
        | Indices range from <tt>0</tt> up to the <tt>length</tt> of a sequence.
        | Sequences support a number of methods to find occurrences of elements or subsequences, including
        |  <tt>segmentLength</tt>, <tt>prefixLength</tt>, <tt>indexWhere</tt>, <tt>indexOf</tt>,
        |  <tt>lastIndexWhere</tt>, <tt>lastIndexOf</tt>, <tt>startsWith</tt>, <tt>endsWith</tt>, <tt>indexOfSlice</tt>.
        |<p>
        |Another way to see a sequence is as a <tt>PartialFunction</tt> from <tt>Int</tt> values
        | to the element type of the sequence. The <tt>isDefinedAt</tt> method of a sequence returns <tt>true</tt> for the
        | interval from <tt>0</tt> until <tt>length</tt>.
        |</p>
        |<p>
        |Sequences can be accessed in reverse order of their elements,
        | using methods <tt>reverse</tt> and <tt>reverseIterator</tt>.
        |</p>
        |<p>
        |Sequences have two principal subtraits, <tt>IndexedSeq</tt> and <tt>LinearSeq</tt>, which give different
        | guarantees for performance. An <tt>IndexedSeq</tt> provides fast random-access of elements
        | and a fast <tt>length</tt> operation. A <tt>LinearSeq</tt> provides fast access only to the first element
        | via <tt>head</tt>, but also has a fast <tt>tail</tt> operation.
        |</p>
        |""".stripMargin
    )

  def testMacro_WithParagraphs_InjectedInVariousPlaces(): Unit = {
    val fileContent =
      s"""/**
         | * @define macro1 short macro value
         | * @define macro2 long macro value line 1
         | *                long macro value line 2
         | *
         | *                long macro value line 3
         | */
         |class Parent
         |
         |/**
         | * $$macro1
         | * $$macro2
         | *
         | * $$macro1
         | *
         | * $$macro2
         | *
         | * Some prefix $$macro1
         | * Some prefix $$macro2
         | *
         | * Some prefix $$macro1
         | *
         | * Some prefix $$macro2
         | */
         |class ${|}Child extends Parent
         |""".stripMargin

    val expectedContent =
      """short macro value long macro value line 1 long macro value line 2
        |<p>long macro value line 3</p>
        |<p>short macro value</p>
        |<p>long macro value line 1 long macro value line 2
        |<p>long macro value line 3</p></p>
        |<p>Some prefix short macro value Some prefix long macro value line 1 long macro value line 2
        |<p>long macro value line 3</p></p>
        |<p>Some prefix short macro value</p>
        |<p>Some prefix long macro value line 1 long macro value line 2
        |<p>long macro value line 3</p></p>
        |""".stripMargin

    doGenerateDocContentTest(fileContent, expectedContent)
  }

  def testMacro_WithLists(): Unit = {
    val fileContent =
      s"""/**
         | * @define macro1 some text
         | *                - item
         | *                - item
         | *                  i. item
         | *                  i. item
         | *
         | *                another text
         | */
         |class Parent
         |
         |/**
         | * Some prefix $$macro1 some postfix
         | *
         | * $$macro1
         | */
         |class ${|}Child extends Parent""".stripMargin

    val expectedContent =
      """Some prefix some text
        |<ul>
        |<li>item</li>
        |<li>item
        |<ol class="lowerRoman">
        |<li>item</li>
        |<li>item</li>
        |</ol>
        |</li>
        |</ul>
        |<p>another text</p> some postfix
        |<p>some text
        |<ul>
        |<li>item</li>
        |<li>item
        |<ol class="lowerRoman">
        |<li>item</li>
        |<li>item</li>
        |</ol>
        |</li>
        |</ul>
        |<p>another text</p></p>""".stripMargin

    doGenerateDocContentTest(fileContent, expectedContent)
  }

  def testNestedScaladocShouldBeTreatedAsCommentData(): Unit ={
    val fileContent =
      s"""/**
         | * text 1
         | * /**
         | * * text inner 1
         | * */
         | * text 2
         | *
         | * {{{/**   text inner   3   */}}}
         | */
         |class ${|}NestedDoc""".stripMargin

    val expectedContent =
      """text 1
        | /**
        | * text inner 1
        | */
        | text 2
        |<p><pre><code>/**   text inner   3   */</code></pre></p> """.stripMargin

    doGenerateDocContentTest(fileContent, expectedContent, HtmlSpacesComparisonMode.DontIgnoreNewLinesCollapseSpaces)
  }

  def testJavadocInlinedTag_CodeI(): Unit =
    doGenerateDocContentTest(
      s"""/**
         | * {@code hello world}<br>
         | * {@code}<br>
         | * {@code  }<br>
         | * {@code [[this.is.not.an.actual.Link]]}<br>
         | */
         |class ${|}X
         |""".stripMargin,
      """<code>hello world</code><br>
        | <code></code><br>
        | <code></code><br>
        | <code>[[this.is.not.an.actual.Link]]</code><br>
        |""".stripMargin
    )

  def testJavadocInlinedTag_DocRoot(): Unit =
    doGenerateDocContentTest(
      s"""/**
         | * {@docRoot}
         | * {@docRoot some text}
         | */
         |class ${|}X
         |""".stripMargin,
      """"""
    )

  def testJavadocInlinedTag_Literal_Value(): Unit =
    doGenerateDocContentTest(
      s"""/**
         | * {@literal some text}
         | * {@value some text}
         | */
         |class ${|}X
         |""".stripMargin,
      """<tt>some text</tt>
        | <tt>some text</tt>
        |""".stripMargin
    )

  def testJavadocInlinedTag_Link_LinkPain(): Unit =
    doGenerateDocContentTest(
      s"""/**
         | * {@link scala.util.DynamicVariable  }<br>
         | * {@link scala.util.DynamicVariable label}<br>
         | * {@linkplain   scala.util.DynamicVariable   label text }<br>
         | */
         |class ${|}X
         |""".stripMargin,
      """<a href="psi_element://scala.util.DynamicVariable"><code>scala.util.DynamicVariable</code></a><br>
        | <a href="psi_element://scala.util.DynamicVariable"><code>label</code></a><br>
        | <a href="psi_element://scala.util.DynamicVariable">label text </a><br>
        |""".stripMargin
    )

  def testJavadocInlinedTag_Link_LinkPlain_Empty(): Unit =
    doGenerateDocContentTest(
      s"""/**
         | * {@link}<br>
         | * {@link  }<br>
         | */
         |class ${|}X
         |""".stripMargin,
      """<br>
        | <br>
        |""".stripMargin
    )

  def testJavadocInlinedTag_Link_LinkPLain_Unresolved(): Unit =
    doGenerateDocContentTest(
      s"""/**
         | * {@link org.Unresolved  }<br>
         | * {@linkplain   org.Unresolved   label text  }<br>
         | */
         |class ${|}X
         |""".stripMargin,
      """<code>org.Unresolved</code><br>
        | <code>label text </code><br>
        |""".stripMargin
    )

  def testJavadocInlinedTag_Unknown(): Unit =
    doGenerateDocContentTest(
      s"""/**
         | * {@unknown javadoc tag}
         | */
         |class ${|}X
         |""".stripMargin,
      """{@unknown javadoc tag}""".stripMargin
    )

  def testTreatLineBreaksAsParagraphs(): Unit = {
    val input =
      """/**
        | * line 1
        | * line 2
        | *
        | * line 3
        | * line 4
        | *
        | *line 5
        | *line 6
        | *
        | *
        | *
        | * line 7
        | * line 8
        | *
        | * __line 9__
        | * __line__ 10
        | * line __11__
        | * line __12__ text
        | *
        | */""".stripMargin

    val expectedContent =
      """line 1
        | line 2
        |<p>line 3
        | line 4</p>
        |<p>line 5
        |line 6</p>
        |<p>line 7
        | line 8</p>
        |<p><u>line 9</u>
        | <u>line</u> 10
        | line <u>11</u>
        | line <u>12</u> text</p>""".stripMargin

    doGenerateDocContentDanglingTest(input, expectedContent, HtmlSpacesComparisonMode.DontIgnoreNewLinesCollapseSpaces)
  }

  def testTreatLineBreaksAsParagraphs_IgnoreLeadingAndTrailingSpaces(): Unit = {
    val input1 =
      """/** line 1 */""".stripMargin

    val input2 =
      """/**
        | * line 1
        | */""".stripMargin

    val input3 =
      """/**
        | *
        | * line 1
        | *
        | */""".stripMargin

    val expectedContent = """line 1"""

    doGenerateDocContentDanglingTest(input1, expectedContent)
    doGenerateDocContentDanglingTest(input2, expectedContent)
    doGenerateDocContentDanglingTest(input3, expectedContent)
  }

  def testTreatLineBreaksAsParagraphs_TrailingSpaceBeforeTag(): Unit = {
    val input1 =
      """/** line 1
        | *
        | *
        | * @since 42
        | */""".stripMargin

    val expectedContent = """line 1"""
    doGenerateDocContentDanglingTest(input1, expectedContent)
  }

  def testTreatLineBreaksAsParagraphs_TrailingSpaceBeforeTag_WithoutSpaceBeforeTag(): Unit = {
    val input1 =
      """/** line 1
        | *
        | *
        | *@since 42
        | */""".stripMargin

    val expectedContent = """line 1"""
    doGenerateDocContentDanglingTest(input1, expectedContent)
  }

  def testTreatLineBreaksAsParagraphs_DoNotInsertParagraphsInsideCodeExample(): Unit = {
    val input =
      """/**
        | * {{{
        | * code line 1
        | *
        | * code line 2
        | *
        | *
        | * code line 3
        | * code line 4
        | * }}}
        | */
        | """.stripMargin

    val expectedContent =
      s"""<pre><code>
         |code line 1
         |
         |code line 2
         |
         |
         |code line 3
         |code line 4
         |</code></pre>
         |""".stripMargin

    doGenerateDocContentDanglingTest(input, expectedContent, HtmlSpacesComparisonMode.DontIgnoreNewLinesCollapseSpaces)
  }

  def testTreatLineBreaksAsParagraphs_DoNotInsertParagraphsHtmlTags(): Unit = {
    val input =
      """/**
        | * <pre>
        | * line 1
        | *
        | * line 2
        | * </pre>
        | *
        | * before <pre>
        | * line 1
        | *
        | * line 2
        | * </pre> after
        | *
        | * <u>
        | * line 1
        | *
        | * line 2
        | * </u>
        | *
        | * before <b>
        | * line 1
        | *
        | * line 2
        | * </b> after
        | */
        |""".stripMargin

    val expectedContent =
      s"""<pre>
         | line 1
         |
         | line 2
         | </pre>
         |<p>before <pre>
         | line 1
         |
         | line 2
         | </pre> after</p>
         |<p><u>
         | line 1
         |
         | line 2
         | </u></p>
         |<p>before <b>
         | line 1
         |
         | line 2
         | </b> after</p>
         |""".stripMargin

    doGenerateDocContentDanglingTest(input, expectedContent, HtmlSpacesComparisonMode.DontIgnoreNewLinesCollapseSpaces)
  }

  def testEmptyLikeFollowedByNewParagraph_DoNotInsertParagraph(): Unit = {
    val input =
      """/**
        | * Paragraph 1
        | *
        | * Paragraph 2 line 1
        | * Paragraph 2 line 2
        | * <p>Paragraph 4</p>
        | *
        | * <p>Paragraph 5</p>
        | */
        | class A
        |""".stripMargin

    val expectedContent =
      s"""Paragraph 1
         |<p>Paragraph 2 line 1
         | Paragraph 2 line 2
         | <p>Paragraph 4</p>
         |<p>Paragraph 5</p>
         |""".stripMargin.trim

    doGenerateDocContentDanglingTest(input, expectedContent)
  }

  /**
   * NOTE: most complex logic with lists is in Parser, that is comprehensively tested via
   * [[org.jetbrains.plugins.scala.lang.parser.ScalaParserTest]] under `data/doccomments`
   *
   * Rendering of parsed lists is quite straight-forward for now, so we just add a single test here.
   *
   * Another important thing to test is lists look-and-feel, that's defined via
   * [[org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocCss]].
   * This can only be done with manual, visual testing.
   */
  def testListAllInOne(): Unit = {
    val input =
      """/**
        | * description
        | *
        | *  1. item
        | *    I. item
        | *    I. item
        | *  1. item
        | *    A. item
        | *    A. item
        | *  1. item
        | *      i. item
        | *      i. item
        | *        a. item
        | *        a. item
        | *  1. item
        | *      - item
        | *      - item multiline line 1
        | *        item multiline line 2
        | *  1. item
        | */
        |class DocWithLists""".stripMargin

    //language=HTML
    val expectedContent =
      """description
        |<ol class="decimal">
        |<li>item
        |<ol class="upperRoman">
        |<li>item</li>
        |<li>item</li></ol></li>
        |<li>item
        |<ol class="upperAlpha">
        |<li>item</li>
        |<li>item</li></ol></li>
        |<li>item
        |<ol class="lowerRoman">
        |<li>item</li>
        |<li>item
        |<ol class="lowerAlpha">
        |<li>item</li>
        |<li>item</li></ol></li></ol></li>
        |<li>item<ul>
        |<li>item</li>
        |<li>item multiline line 1 item multiline line 2</li></ul></li>
        |<li>item</li></ol>""".stripMargin

    doGenerateDocContentTest(input, expectedContent)
  }


  def testEscapeHtmlInCodeExampleContent(): Unit =
    doGenerateDocContentTest(
      s"""/**
         | * text before {{{<head><style>...</style></head>}}} text after
         | */
         |class ${|}A""".stripMargin,
      """text before <pre><code>&lt;head&gt;&lt;style&gt;...&lt;/style&gt;&lt;/head&gt;</code></pre> text after""".stripMargin,
      HtmlSpacesComparisonMode.DontIgnoreNewLinesCollapseSpaces
    )

  def testEscapeHtmlInWikiSyntaxElements(): Unit = {
    doGenerateDocContentTest(
      s"""/**
         | * text before `<head><style>...</style></head>` text after
         | */
         |class ${|}A""".stripMargin,
      """text before <tt>&lt;head&gt;&lt;style&gt;...&lt;/style&gt;&lt;/head&gt;</tt> text after""".stripMargin
    )

    doGenerateDocContentTest(
      s"""/**
         | * text before '''<head><style>...</style></head>''' text after
         | */
         |class ${|}A""".stripMargin,
      """text before
        | <b>&lt;head&gt;&lt;style&gt;...&lt;/style&gt;&lt;/head&gt;</b>
        | text after""".stripMargin
    )
  }
}
