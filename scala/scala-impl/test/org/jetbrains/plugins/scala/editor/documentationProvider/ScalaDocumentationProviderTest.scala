package org.jetbrains.plugins.scala.editor.documentationProvider

// TODO: in-editor doc: code example in the end of the doc produces new line
class ScalaDocumentationProviderTest extends ScalaDocumentationProviderTestBase {

  // helper symbol used to prevent empty lines in multiline strings from trimming by IDEA
  private def blank = ""

  def testClass(): Unit =
    doGenerateDocDefinitionTest(
      s"""package a.b.c
         |
         |class ${|}A
         |""".stripMargin,
      s"""a.b.c
         |class <b>A</b>""".stripMargin
    )

  def testClass_TopLevel(): Unit =
    doGenerateDocDefinitionTest(
      s"""class ${|}A""",
      s"""class <b>A</b>"""
    )

  def testClass_WithSuperClass(): Unit =
    doGenerateDocDefinitionTest(
      s"""package a.b.c
         |
         |class ${|}A extends Exception""".stripMargin,
      s"""a.b.c
         |class <b>A</b>
         |extends <a href="psi_element://scala.Exception"><code>Exception</code></a>""".stripMargin
    )

  def testTrait(): Unit =
    doGenerateDocDefinitionTest(
      s"""package a.b.c
         |
         |trait ${|}T
         |""".stripMargin,
      s"""a.b.c
         |trait <b>T</b>""".stripMargin
    )

  def testObject(): Unit =
    doGenerateDocDefinitionTest(
      s"""package a.b.c
         |
         |object ${|}O
         |""".stripMargin,
      s"""a.b.c
         |object <b>O</b>""".stripMargin
    )

  def testTypeAlias(): Unit =
    doGenerateDocDefinitionTest(
      s"""object O {
         |  type ${|}MyType = java.lang.Exception
         |}""".stripMargin,
      s"""<a href="psi_element://O"><code>O</code></a>
         |type <b>MyType</b> = <a href="psi_element://java.lang.Exception"><code>Exception</code></a>""".stripMargin
    )

  def testClass_WithSuperClassAndTraits(): Unit =
    doGenerateDocDefinitionTest(
      s"""package a.b.c
         |trait T1
         |trait T2
         |class ${|}A extends Exception with T1 with T2""".stripMargin,
      s"""a.b.c
         |class <b>A</b>
         |extends <a href="psi_element://scala.Exception"><code>Exception</code></a>
         |with <a href="psi_element://a.b.c.T1"><code>T1</code></a> with <a href="psi_element://a.b.c.T2"><code>T2</code></a>""".stripMargin
    )

  // for not it's not a business requirement just fixing implementation in tests
  def testClassExtendingAnotherClassShouldNotInheritDoc(): Unit =
    doGenerateDocDefinitionTest(
      s"""/** description of A */
         |class A
         |class ${|}B extends A
         |""".stripMargin,
      """class <b>B</b>
        |extends <a href="psi_element://A"><code>A</code></a>""".stripMargin
    )

  def testClassExtendingAnotherJavaClassShouldNotInheritDoc(): Unit = {
    getFixture.addFileToProject("J.java",
      s"""/** description of base class J */
         |class J {}
         |""".stripMargin
    )
    doGenerateDocDefinitionTest(
      s"""class ${|}B extends J""".stripMargin,
      s"""class <b>B</b>
         |extends <a href="psi_element://J"><code>J</code></a>""".stripMargin
    )
  }

  def testClass_WithVariousGenericsWithBounds(): Unit =
    doGenerateDocDefinitionTest(
      s"""trait Trait[A]
         |abstract class ${|}Class[T <: Trait[_ >: Object]]
         |  extends Comparable[_ <: Trait[_ >: String]]""".stripMargin,
      "abstract class <b>Class</b>[T &lt;: <a href=\"psi_element://Trait\"><code>Trait</code></a>" +
        "[_ &gt;: <a href=\"psi_element://java.lang.Object\"><code>Object</code></a>]" +
        "]\n" +
        "extends <a href=\"psi_element://java.lang.Comparable\"><code>Comparable</code></a>" +
        "[_ &lt;: <a href=\"psi_element://Trait\"><code>Trait</code></a>" +
        "[_ &gt;: <a href=\"psi_element://scala.Predef.String\"><code>String</code></a>]]"
    )

  def testMethod(): Unit =
    doGenerateDocBodyTest(
      s"""class A {
         |  /** description of foo */
         |  def ${|}foo: String = ???
         |}""".stripMargin,
      s"""$DefinitionStart<a href="psi_element://A"><code>A</code></a>
         |def <b>foo</b>: <a href="psi_element://scala.Predef.String"><code>String</code></a>$DefinitionEnd
         |$ContentStart<p>description of foo $ContentEnd
         |""".stripMargin
    )

  def testMethod_Overriding(): Unit = {
    getFixture.addFileToProject("BaseScalaClass.scala",
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
      s"""$DefinitionStart<a href="psi_element://A"><code>A</code></a>
         |def <b>baseMethod</b>: <a href="psi_element://scala.Predef.String"><code>String</code></a>$DefinitionEnd
         |$ContentStart<p>description of base method from A $ContentEnd
         |""".stripMargin
    )
  }

  def testMethod_WithEmptyDoc_Overriding(): Unit = {
    getFixture.addFileToProject("BaseScalaClass.scala",
      s"""class BaseScalaClass {
         |  /** description of base method from BaseScalaClass */
         |  def baseMethod: String = ???
         |}
         |""".stripMargin
    )
    // TODO: do we need override keyword as text in <pre> section?
    //  Java uses `Overrides` section for that (e.g. Overrides: foo in class BaseClass)
    doGenerateDocBodyTest(
      s"""class A extends BaseScalaClass {
         |  override def ${|}baseMethod: String = ???
         |}""".stripMargin,
      s"""$DefinitionStart<a href="psi_element://A"><code>A</code></a>
         |override def <b>baseMethod</b>: <a href="psi_element://scala.Predef.String"><code>String</code></a>$DefinitionEnd
         |$ContentStart
         |<b>Description copied from class: </b>
         |<a href="psi_element://BaseScalaClass"><code>BaseScalaClass</code></a>
         |$ContentEnd
         |$ContentStart
         |<p>description of base method from BaseScalaClass
         |$ContentEnd
         |""".stripMargin
    )
  }

  def testMethod_WithEmptyDoc_OverridingJavaMethod(): Unit = {
    getFixture.addFileToProject("BaseJavaClass.java",
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
         |override def <b>baseMethod</b>: <a href="psi_element://scala.Predef.String"><code>String</code></a>$DefinitionEnd
         |$ContentStart
         |<b>Description copied from class: </b>
         |<a href="psi_element://BaseJavaClass"><code>BaseJavaClass</code></a>
         |$ContentEnd
         |$ContentStart
         |description of base method from BaseJavaClass
         |<p>
         |$ContentEnd
         |$SectionsStart<p>$SectionsEnd
         |""".stripMargin
    )
  }

  def testMethod_WithEmptyDoc_OverridingJavaMethod_TagsInJavadoc(): Unit = {
    getFixture.addFileToProject("BaseJavaClass.java",
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
      s"""$DefinitionStart<a href="psi_element://A"><code>A</code></a>
         |override def <b>getModules</b>: <a href="psi_element://scala.Predef.String"><code>String</code></a>$DefinitionEnd
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

  def testMethod_WithAccessModifier(): Unit =
    doGenerateDocDefinitionTest(
      s"""class X {
         |  protected def ${|}f1 = 42
         |}
         |""".stripMargin,
      s"""<a href="psi_element://X"><code>X</code></a>
         |protected def <b>f1</b>: <a href="psi_element://scala.Int"><code>Int</code></a>""".stripMargin
    )

  def testMethod_WithAccessModifierWithThisQualifier(): Unit =
    doGenerateDocDefinitionTest(
      s"""class X {
         |  protected[this] def ${|}f1 = 42
         |}
         |""".stripMargin,
      s"""<a href="psi_element://X"><code>X</code></a>
         |protected[this] def <b>f1</b>: <a href="psi_element://scala.Int"><code>Int</code></a>""".stripMargin
    )

  def testMethod_HigherKindedTypeParameters(): Unit =
    doGenerateDocDefinitionTest(
      s"""object O {
         |  def ${|}f[A[_, B]] = 42
         |}""".stripMargin,
      s"""<a href="psi_element://O"><code>O</code></a>
         |def <b>f</b>[A[_, B]]: <a href="psi_element://scala.Int"><code>Int</code></a>""".stripMargin
    )

  def testMethod_HigherKindedTypeParameters_1(): Unit =
    doGenerateDocDefinitionTest(
      s"""trait ${|}T[X[_, Y[_, Z]]]
         |""".stripMargin,
      """trait <b>T</b>[X[_, Y[_, Z]]]"""
    )

  def testMethod_HigherKindedTypeParameters_ReferToParameterInExtendsList(): Unit = {
    val input1  =
      s"""trait Trait1[A]
         |trait Trait2[A, CC[X] <: Seq[X]]
         |extends Trait1[CC[A]]
         |val ${|}x: Trait2[_, _] = ???""".stripMargin
    val expectedDoc =
      s"""${DefinitionStart}val <b>x</b>: <a href="psi_element://Trait2"><code>Trait2</code></a>[_, _]$DefinitionEnd"""
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
         |$ContentStart<p>some description $ContentEnd
         |$SectionsStart
         |<tr><td valign='top' class='section'><p>Note:</td><td valign='top'>some note</td>
         |$SectionsEnd""".stripMargin
    )

  def testMemberValue(): Unit =
    doValVarTest(
      s"""val ${|}v = 1""",
      s"""<a href="psi_element://X"><code>X</code></a>\nval <b>v</b>: <a href="psi_element://scala.Int"><code>Int</code></a>"""
    )

  def testMemberVariable(): Unit =
    doValVarTest(
      s"""var ${|}v = 1""",
      s"""<a href="psi_element://X"><code>X</code></a>\nvar <b>v</b>: <a href="psi_element://scala.Int"><code>Int</code></a>"""
    )

  def testMemberValuePattern(): Unit =
    doValVarTest(
      s"""val (v1, ${|}v2) = (1, "str")""",
      s"""<a href="psi_element://X"><code>X</code></a>\nval <b>v2</b>: <a href="psi_element://java.lang.String"><code>String</code></a>"""
    )

  def testMemberValuePattern_1(): Unit =
    doValVarTest(
      s"""val Tuple2(v1, ${|}v2) = (1, "str")""",
      // java.lang.String cause it's an inferred type
      s"""<a href="psi_element://X"><code>X</code></a>\nval <b>v2</b>: <a href="psi_element://java.lang.String"><code>String</code></a>"""
    )

  def testMemberVariablePattern(): Unit =
    doValVarTest(
      s"""var (v1, ${|}v2) = (1, "str")""",
      s"""<a href="psi_element://X"><code>X</code></a>\nvar <b>v2</b>: <a href="psi_element://java.lang.String"><code>String</code></a>"""
    )

  def testMemberVariablePattern_1(): Unit =
    doValVarTest(
      s"""var Tuple2(v1, ${|}v2) = (1, "str")""",
      s"""<a href="psi_element://X"><code>X</code></a>\nvar <b>v2</b>: <a href="psi_element://java.lang.String"><code>String</code></a>"""
    )

  def testMemberValue_Abstract(): Unit =
    doValVarTest(
      s"""val ${|}v: Int""",
      s"""<a href="psi_element://X"><code>X</code></a>\nval <b>v</b>: <a href="psi_element://scala.Int"><code>Int</code></a>"""
    )

  def testMemberVariable_Abstract(): Unit =
    doValVarTest(
      s"""var ${|}v: Int""",
      s"""<a href="psi_element://X"><code>X</code></a>\nvar <b>v</b>: <a href="psi_element://scala.Int"><code>Int</code></a>"""
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
         |<td valign='top'> some text</td>
         |<tr><td valign='top' class='section'><p>Throws:</td>
         |<td valign='top'><a href="psi_element://scala.Exception"><code>Exception</code></a> &ndash; some text</td>
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
      s"""${DefinitionStart}class <b>A</b>$DefinitionEnd
         |$ContentStart<p>Description $ContentEnd
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
         |<td valign='top'>i &ndash; aaa  <p>j &ndash; bbb  <p>k &ndash; ccc  </td>
         |<tr><td valign='top' class='section'><p>Type parameters:</td>
         |<td valign='top'>
         |T &ndash; ddd<p>
         |E &ndash; eee\n            ggg</td>
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
         |<a href="psi_element://scala.Exception"><code>Exception</code></a>
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
         |<td valign='top'>o &ndash;<p>f &ndash; description for f  </td>
         |<tr><td valign='top' class='section'><p>Type parameters:</td>
         |<td valign='top'>E &ndash;<p>K &ndash; description for K</td>
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
         |<td valign='top'>A &ndash;  <p>B &ndash; description for B<p>C &ndash;</td>""".stripMargin
    doGenerateDocSectionsTest(fileText, expectedDoc)
  }

  def testFontStyles_Nested_Underscore_Power_Italic(): Unit =
    doGenerateDocBodyTest(
      s"""/**
         | * __xxx^yyy''zzz''yyy^xxx__
         | */
         |val ${|}a = 1
         |""".stripMargin,
      s"""${DefinitionStart}val <b>a</b>: <a href="psi_element://scala.Int"><code>Int</code></a>$DefinitionEnd
         |$ContentStart<p><u>xxx<sup>yyy<i>zzz</i>yyy</sup>xxx</u>$ContentEnd
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
      s"""${DefinitionStart}val <b>a</b>: <a href="psi_element://scala.Int"><code>Int</code></a>$DefinitionEnd
         |$ContentStart
         |<p>
         |<a href="http://example.org">http://example.org</a><br>
         |<a href="http://example.org">http://example.org</a><br>
         |<a href="http://example.org">http://example.org</a><br>
         |<a href="http://example.org">http://example.org</a><br>
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
      s"""${DefinitionStart}val <b>a</b>: <a href="psi_element://scala.Int"><code>Int</code></a>$DefinitionEnd
         |$ContentStart
         |<p>
         |<a href="http://example.org">label</a><br>
         |<a href="http://example.org">label</a><br>
         |<a href="http://example.org">label with   spaces</a><br>
         |<a href="http://example.org">label with   spaces</a><br>
         |$ContentEnd""".stripMargin
    doGenerateDocBodyTest(fileText, expectedDoc)
  }

  def testHttpLinks_WithDescription_WithMarkupSyntax(): Unit = {
    val fileText =
      s"""/**
         | * [[http://example.org   '''label with markdown text''']]<br>
         | * [[http://example.org   label '''with markdown text'''  ]]<br>
         | * [[http://example.org   '''label with markdown''' text  ]]<br>
         | * [[http://example.org   label '''with markdown''' text  ]]<br>
         | * [[http://example.org   label '''__with nested__ markdown''' text  ]]<br>
         | * [[  http://example.org   label '''__with nested__ markdown''' text  ]]<br>
         | */
         |val ${|}a = 1
         |""".stripMargin
    val expectedDoc =
      s"""${DefinitionStart}val <b>a</b>: <a href="psi_element://scala.Int"><code>Int</code></a>$DefinitionEnd
         |$ContentStart
         |<p>
         |<a href="http://example.org"><b>label with markdown text</b></a><br>
         |<a href="http://example.org">label <b>with markdown text</b></a><br>
         |<a href="http://example.org"><b>label with markdown</b> text</a><br>
         |<a href="http://example.org">label <b>with markdown</b> text</a><br>
         |<a href="http://example.org">label <b><u>with nested</u> markdown</b> text</a><br>
         |<a href="http://example.org">label <b><u>with nested</u> markdown</b> text</a><br>
         |$ContentEnd""".stripMargin
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
      s"""${DefinitionStart}val <b>a</b>: <a href="psi_element://scala.Int"><code>Int</code></a>$DefinitionEnd
         |$ContentStart
         |<p>
         |<sub><u><a href="http://example.org">http://example.org</a></u></sub><br>
         |<sub><u><a href="http://example.org">label text</a></u></sub><br>
         |<a href="http://example.org">http://example.org</a> <sup><a href="http://example.org">http://example.org</a></sup><br>
         |<a href="http://example.org">label  1</a> <sup><a href="http://example.org">label 2</a></sup><br>
         |$ContentEnd""".stripMargin
    doGenerateDocBodyTest(fileText, expectedDoc)
  }

  def testHttpLink_WithValidEqualSignInside(): Unit =
    doGenerateDocContentTest(
      s"""/** [[https://example.org?at=location]] */
         |val ${|}a = 1
         |""".stripMargin,
      s"""<p><a href="https://example.org?at=location">https://example.org?at=location</a>""".stripMargin
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
      s"""<p><font color=red></font><br><font color=red></font><br>""".stripMargin
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
      s"""<p>
         |<a href="psi_element://scala.util.DynamicVariable"><code>scala.util.DynamicVariable</code></a><br>
         |<a href="psi_element://scala.util.DynamicVariable"><code>scala.util.DynamicVariable</code></a><br>
         |<a href="psi_element://scala.util.DynamicVariable"><code>scala.util.DynamicVariable</code></a><br>
         |<a href="psi_element://scala.util.DynamicVariable"><code>scala.util.DynamicVariable</code></a><br>
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
      """<p><a href="psi_element://scala.util.Try"><code>scala.util.Try</code></a><br>
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
      """<p><a href="psi_element://scala.util.Properties"><code>scala.util.Properties</code></a><br>
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
      s"""<p>
         |<a href="psi_element://scala.util.DynamicVariable"><code>label</code></a><br>
         |<a href="psi_element://scala.util.DynamicVariable"><code>label</code></a><br>
         |<a href="psi_element://scala.util.DynamicVariable"><code>label with   spaces</code></a><br>
         |<a href="psi_element://scala.util.DynamicVariable"><code>label with   spaces</code></a><br>
         |""".stripMargin
    doGenerateDocContentTest(fileText, expectedDoc)
  }

  def testCodeLinks_WithDescription_WithMarkupSyntax(): Unit = {
    val fileText =
      s"""/**
         | * [[scala.util.DynamicVariable   '''label with markdown text''']]<br>
         | * [[scala.util.DynamicVariable   label '''with markdown text'''  ]]<br>
         | * [[scala.util.DynamicVariable   '''label with markdown''' text  ]]<br>
         | * [[scala.util.DynamicVariable   label '''with markdown''' text  ]]<br>
         | * [[scala.util.DynamicVariable   label '''__with nested__ markdown''' text  ]]<br>
         | * [[  scala.util.DynamicVariable   label '''__with nested__ markdown''' text  ]]<br>
         | * [[  scala.util.DynamicVariable   label '''__with nested__ markdown''' text and special chars >>> <<< ]]<br>
         | */
         |val ${|}a = 1
         |""".stripMargin
    val expectedDoc =
      s"""<p>
         |<a href="psi_element://scala.util.DynamicVariable"><code><b>label with markdown text</b></code></a><br>
         |<a href="psi_element://scala.util.DynamicVariable"><code>label <b>with markdown text</b></code></a><br>
         |<a href="psi_element://scala.util.DynamicVariable"><code><b>label with markdown</b> text</code></a><br>
         |<a href="psi_element://scala.util.DynamicVariable"><code>label <b>with markdown</b> text</code></a><br>
         |<a href="psi_element://scala.util.DynamicVariable"><code>label <b><u>with nested</u> markdown</b> text</code></a><br>
         |<a href="psi_element://scala.util.DynamicVariable"><code>label <b><u>with nested</u> markdown</b> text</code></a><br>
         |<a href="psi_element://scala.util.DynamicVariable"><code>label <b><u>with nested</u> markdown</b> text and special chars >>> <<<</code></a><br>
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
      s"""<p>
         |<sub><u><a href="psi_element://scala.util.DynamicVariable"><code>scala.util.DynamicVariable</code></a></u></sub><br>
         |<sub><u><a href="psi_element://scala.util.DynamicVariable"><code>label text</code></a></u></sub><br>
         |<a href="psi_element://scala.util.DynamicVariable"><code>scala.util.DynamicVariable</code></a>
         |<sup><a href="psi_element://scala.util.DynamicVariable"><code>scala.util.DynamicVariable</code></a></sup><br>
         |<a href="psi_element://scala.util.DynamicVariable"><code>label  1</code></a> <sup><a href="psi_element://scala.util.DynamicVariable"><code>label 2</code></a></sup><br>
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
      s"""<p>
         |<font color=red>org.Unresolved</font><br>
         |<font color=red>description <u>with markup</u></font><br>
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
      s"""<p>
         |<a href="psi_element://scala.util.DynamicVariable"><code>DynamicVariable</code></a><br>
         |<a href="psi_element://scala.util.DynamicVariable"><code>DynamicVariable</code></a><br>
         |<a href="psi_element://scala.util.DynamicVariable"><code>DynamicVariable</code></a><br>
         |<a href="psi_element://scala.util.DynamicVariable"><code>DynamicVariable</code></a><br>
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
      s"""<p>
         |<a href="psi_element://java.lang.Exception"><code>java.lang.Exception</code></a><br>
         |<a href="psi_element://scala.Exception"><code>Exception</code></a><br>
         |<a href="psi_element://scala.Exception"><code>Exception</code></a><br>
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
      s"""<p>
         |<a href="psi_element://<:::<"><code>&lt;:::&lt;</code></a><br>
         |<a href="psi_element://<:::<"><code>&lt;:::&lt;</code></a><br>
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
      """<p>
        |<a href="psi_element://com.example.MyClass1"><code>MyClass1</code></a><br>
        |<a href="psi_element://com.example.MyClass1"><code>MyClass1</code></a><br>
        |<a href="psi_element://com.example.MyClass1"><code>MyClass1</code></a><br>
        |<a href="psi_element://com.example.MyClass2"><code>MyClass2</code></a><br>
        |<a href="psi_element://com.example.MyClass2"><code>MyClass2</code></a><br>
        |<a href="psi_element://com.example.MyClass2"><code>MyClass2</code></a><br>
        |<a href="psi_element://com.example.MyClass3"><code>MyClass3</code></a><br>
        |<a href="psi_element://com.example.MyClass3"><code>MyClass3</code></a><br>
        |<a href="psi_element://com.example.MyClass3"><code>MyClass3</code></a><br>
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
      s"""${DefinitionStart}val <b>a</b>: <a href="psi_element://scala.Int"><code>Int</code></a>$DefinitionEnd
         |$ContentStart
         |<p><sup>blah-blah  </sup>
         |<p>
         |<u>aaaaaaa<sub>bbbbbbb </sub></u>
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
      s"""${DefinitionStart}val <b>a</b>: <a href="psi_element://scala.Int"><code>Int</code></a>$DefinitionEnd
         |$ContentStart$ContentEnd
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

    val expectedDoc =
      s"""$DefinitionStart<a href="psi_element://B"><code>B</code></a>
         |override def <b>f</b>: <a href="psi_element://scala.Int"><code>Int</code></a>$DefinitionEnd
         |$ContentStart
         |<p>Child description
         |<p>
         |<p>Parent description
         |<p>Extra child description
         |$ContentEnd""".stripMargin
    doGenerateDocBodyTest(fileText, expectedDoc)
  }

  def testTags_InheritDoc_WithMacro(): Unit = {
    val fileText =
      s"""
         |/**
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
         |override def <b>f</b>(i: <a href="psi_element://scala.Int"><code>Int</code></a>): <a href="psi_element://scala.Int"><code>Int</code></a>$DefinitionEnd
         |$ContentStart
         |<p>
         |<p>The function f defined in B returns some integer without no special property. (previously defined in A)
         |<p>
         |Some notes on implementation performance, the function runs in O(1).
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

    val expectedDoc =
      s"""${DefinitionStart}class <b>ScalaClass</b>
         |extends <a href="psi_element://JavaClass"><code>JavaClass</code></a>$DefinitionEnd
         |$ContentStart
         |<p>
         |text from<u>scala</u>
         |<p>
         |text from <b>java</b><br>
         |<code>code tag</code><br>
         |<a href="psi_element://JavaClass"><code>JavaClass</code></a><br>
         |<p>
         |extra text from<u>scala</u>
         |$ContentEnd
         |""".stripMargin
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
      s"""$DefinitionStart<a href="psi_element://A"><code>A</code></a>
         |def <b>boo</b>(): <a href="psi_element://scala.Unit"><code>Unit</code></a>$DefinitionEnd
         |$ContentStart<p>Function defined in A $ContentEnd
         |""".stripMargin
    )

  def testMacro_Complicated(): Unit =
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
      s"""$DefinitionStart<a href="psi_element://C"><code>C</code></a>
         |override def <b>boo</b>(): <a href="psi_element://scala.Int"><code>Int</code></a>$DefinitionEnd
         |$ContentStart
         |<p>a VALUE1 b VALUE2 c [Cannot find macro: $$KEY_UNREACHED]
         |$ContentEnd""".stripMargin
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
         |def <b>foo</b>(): <a href="psi_element://scala.Unit"><code>Unit</code></a>$DefinitionEnd
         |$ContentStart<p><tt>None</tt>$ContentEnd
         |""".stripMargin
    )

  def testMacro_Undefined(): Unit =
    doGenerateDocContentTest(
      """/** Returns true if the option is $none, false otherwise */
        |class A {}
        |""".stripMargin,
      """<p>Returns true if the option is [Cannot find macro: $none], false otherwise""".stripMargin
    )

  def testMacro_Recursive_ShouldNotFail_1(): Unit =
    doGenerateDocContentTest(
      s"""/**
         | * test $$myTag
         | * @define myTag myTag description $$myTag
         | */
         |class ${|}RecursiveDefine
         |""".stripMargin,
      """<p>test myTag description""".stripMargin
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
      """<p>test myTag1 description myTag2 description""".stripMargin
    )

  def testAnnotationArgs(): Unit = {
    val fileContent =
      s"""class Outer {
         |  @deprecated("use 'foo' instead", "1.2.3")
         |  @transient
         |  def ${|}boo() {}
         |}""".stripMargin

    val expectedDoc =
      s"""<a href="psi_element://Outer"><code>Outer</code></a>
         |@<a href="psi_element://scala.deprecated"><code>deprecated</code></a>(&quot;use 'foo' instead&quot;, &quot;1.2.3&quot;)
         |@<a href="psi_element://scala.transient"><code>transient</code></a>
         |def <b>boo</b>(): <a href="psi_element://scala.Unit"><code>Unit</code></a>""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedDoc)
  }

  def testAnnotationArgs_WithInnerHtmlTextShouldBeEscaped(): Unit = {
    val fileContent =
      s"""class Outer {
         |  @deprecatedName("inner tags <p>example</p>", "since 2020")
         |  def ${|}boo() {}
         |}""".stripMargin

    val expectedDoc =
      s"""<a href="psi_element://Outer"><code>Outer</code></a>
         |@<a href="psi_element://scala.deprecatedName"><code>deprecatedName</code></a>(&quot;inner tags &lt;p&gt;example&lt;/p&gt;&quot;, &quot;since 2020&quot;)
         |def <b>boo</b>(): <a href="psi_element://scala.Unit"><code>Unit</code></a>""".stripMargin
    doGenerateDocDefinitionTest(fileContent, expectedDoc)
  }

  def testAnnotation_Throws_ShouldIgnoreExceptionClassArgument(): Unit = {
    // NOTE: the exception class is already shown in the annotation type, see SCL-17608
    val fileContent =
      """@throws(classOf[Exception])
        |@throws[Exception]("reason 1")
        |@throws(classOf[java.util.ConcurrentModificationException])
        |@throws[java.util.ConcurrentModificationException]("reason 2")
        |def goo() {}
        |""".stripMargin

    val expectedDoc =
      s"""@<a href="psi_element://scala.throws"><code>throws</code></a>[<a href="psi_element://scala.Exception"><code>Exception</code></a>]
         |@<a href="psi_element://scala.throws"><code>throws</code></a>[<a href="psi_element://scala.Exception"><code>Exception</code></a>](&quot;reason 1&quot;)
         |@<a href="psi_element://scala.throws"><code>throws</code></a>[<a href="psi_element://java.util.ConcurrentModificationException"><code>ConcurrentModificationException</code></a>]
         |@<a href="psi_element://scala.throws"><code>throws</code></a>[<a href="psi_element://java.util.ConcurrentModificationException"><code>ConcurrentModificationException</code></a>](&quot;reason 2&quot;)
         |def <b>goo</b>(): <a href="psi_element://scala.Unit"><code>Unit</code></a>""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedDoc)
  }

  def testTrait_SpecialChars_InfixType(): Unit =
    doGenerateDocDefinitionTest(
      s"""object A {
         |  trait <:<[A,B]
         |  def ${|}f(a: Int <:< String): Unit = {}
         |}""".stripMargin,
      "<a href=\"psi_element://A\"><code>A</code></a>\n" +
        "def <b>f</b>(" +
        "a: <a href=\"psi_element://scala.Int\"><code>Int</code></a>" +
        " <a href=\"psi_element://A.&lt;:&lt;\"><code>&lt;:&lt;</code></a> " +
        "<a href=\"psi_element://scala.Predef.String\"><code>String</code></a>" +
        "): <a href=\"psi_element://scala.Unit\"><code>Unit</code></a>"
    )

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
      """<p>
        |text 1
        |/**
        |* text inner 1
        |*/
        |text 2
        |<p>
        |<pre><code>/**   text inner   3   */</code></pre> """.stripMargin

    doGenerateDocContentTest(fileContent, expectedContent)
  }

  def testJavadocInlinedTag_Code(): Unit =
    doGenerateDocContentTest(
      s"""/**
         | * {@code}<br>
         | * {@code  }<br>
         | * {@code [[this.is.not.an.actual.Link]]}<br>
         | */
         |class ${|}X
         |""".stripMargin,
      """<p>
        |<code></code><br>
        |<code></code><br>
        |<code>[[this.is.not.an.actual.Link]]</code><br>
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
      """<p>"""
    )

  def testJavadocInlinedTag_Literal_Value(): Unit =
    doGenerateDocContentTest(
      s"""/**
         | * {@literal some text}
         | * {@value some text}
         | */
         |class ${|}X
         |""".stripMargin,
      """<p>
        |<tt>some text</tt>
        |<tt>some text</tt>
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
      """<p>
        |<a href="psi_element://scala.util.DynamicVariable"><code>scala.util.DynamicVariable</code></a><br>
        |<a href="psi_element://scala.util.DynamicVariable"><code>label</code></a><br>
        |<a href="psi_element://scala.util.DynamicVariable">label text</a><br>
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
      """<p>
        |<font color=red></font><br>
        |<font color=red></font><br>
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
      """<p>
        |<font color=red>org.Unresolved</font><br>
        |<font color=red>label text</font><br>
        |""".stripMargin
    )

  def testJavadocInlinedTag_Unknown(): Unit =
    doGenerateDocContentTest(
      s"""/**
         | * {@unknown javadoc tag}
         | */
         |class ${|}X
         |""".stripMargin,
      """<p>{@unknown javadoc tag}""".stripMargin
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
      """<p>
        |line 1
        |line 2
        |<p>
        |line 3
        |line 4
        |<p>
        |line 5
        |line 6
        |<p>
        |line 7
        |line 8
        |<p>
        |<u>line 9</u>
        |<u>line</u> 10
        |line <u>11</u>
        |line <u>12</u> text
        |""".stripMargin

    doGenerateDocContentDanglingTest(input, expectedContent)
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

    val expectedContent = """<p>line 1"""

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

    val expectedContent = """<p>line 1"""
    doGenerateDocContentDanglingTest(input1, expectedContent)
  }

  def testTreatLineBreaksAsParagraphs_TrailingSpaceBeforeTag_WithoutSpaceBeforeTag(): Unit = {
    val input1 =
      """/** line 1
        | *
        | *
        | *@since 42
        | */""".stripMargin

    val expectedContent = """<p>line 1"""
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
      s"""<p><pre><code>
         |  code line 1
         | $blank
         |  code line 2
         | $blank
         | $blank
         |  code line 3
         |  code line 4
         |  </code></pre>
         |""".stripMargin

    doGenerateDocContentDanglingTest(input, expectedContent)
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
      s"""<p>
         |<pre>
         |  line 1
         | $blank
         |  line 2
         |  </pre>
         |<p>
         |before <pre>
         |  line 1
         | $blank
         |  line 2
         |  </pre> after
         |<p><u>line 1 line 2</u>
         |<p>before<b>line 1 line 2</b>after
         |""".stripMargin

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
      """<p>description
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
}
