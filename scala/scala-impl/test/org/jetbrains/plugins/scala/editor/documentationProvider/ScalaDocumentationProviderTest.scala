package org.jetbrains.plugins.scala.editor.documentationProvider

// TODO: Currently this contains just simple health check tests.
//  Add more before improving quick-doc SCL-17101
// TODO2: in-editor doc: code example in the end of the doc produces new line
class ScalaDocumentationProviderTest extends ScalaDocumentationProviderTestBase {

  def testClass(): Unit =
    doShortGenerateDocTest(
      s"""/** description of A */
         |class ${|}A {}""".stripMargin,
      s"""${DefinitionStart}class <b>A</b>$DefinitionEnd
         |<div class='content'> description of A <p></div>
         |<table class='sections'><p></table>
         |""".stripMargin
    )

  def testClassInPackage(): Unit =
    doShortGenerateDocTest(
      s"""package a.b.c
         |/** description of A */
         |class ${|}A""".stripMargin,
      s"""$DefinitionStart
         |a.b.c
         |class <b>A</b>
         |$DefinitionEnd
         |<div class='content'> description of A <p></div>
         |<table class='sections'><p></table>
         |""".stripMargin
    )

  // for not it's not a business requirement just fixing implementation in tests
  def testClassExtendingAnotherClassShouldNotInheritDoc(): Unit =
    doShortGenerateDocTest(
      s"""/** description of A */
         |class A
         |class ${|}B extends A
         |""".stripMargin,
      s"""$DefinitionStart
         |class <b>B</b> extends <a href="psi_element://A"><code>A</code></a>
         |$DefinitionEnd
         |""".stripMargin
    )

  def testClassExtendingAnotherJavaClassShouldNotInheritDoc(): Unit = {
    getFixture.addFileToProject("J.java",
      s"""/** description of base class J */
         |class J {}
         |""".stripMargin
    )
    doShortGenerateDocTest(
      s"""class ${|}B extends J""".stripMargin,
      s"""$DefinitionStart
         |class <b>B</b> extends <a href="psi_element://J"><code>J</code></a>
         |$DefinitionEnd
         |""".stripMargin
    )
  }

  def testMethod(): Unit =
    doShortGenerateDocTest(
      s"""class A {
         |  /** description of foo */
         |  def ${|}foo: String = ???
         |}""".stripMargin,
      s"""$DefinitionStart
         |<a href="psi_element://A"><code>A</code></a>
         |def <b>foo</b>: <a href="psi_element://scala.Predef.String"><code>String</code></a>
         |$DefinitionEnd
         |<div class='content'> description of foo <p></div>
         |<table class='sections'><p></table>
         |""".stripMargin
    )

  def testMethodOverriding(): Unit = {
    getFixture.addFileToProject("BaseScalaClass.scala",
      s"""class BaseScalaClass {
         |  /** description of base method from BaseScalaClass */
         |  def baseMethod: String = ???
         |}
         |""".stripMargin
    )
    doShortGenerateDocTest(
      s"""class A extends BaseScalaClass {
         |  /** description of base method from A */
         |  def ${|}baseMethod: String = ???
         |}
         |""".stripMargin,
      s"""$DefinitionStart
         |<a href="psi_element://A"><code>A</code></a>
         |def <b>baseMethod</b>: <a href="psi_element://scala.Predef.String"><code>String</code></a>
         |$DefinitionEnd
         |<div class='content'> description of base method from A <p></div>
         |<table class='sections'><p></table>
         |""".stripMargin
    )
  }

  def testMethodWithEmptyDocOverriding(): Unit = {
    getFixture.addFileToProject("BaseScalaClass.scala",
      s"""class BaseScalaClass {
         |  /** description of base method from BaseScalaClass */
         |  def baseMethod: String = ???
         |}
         |""".stripMargin
    )
    // TODO: do we need override keyword as text in <pre> section?
    //  Java uses `Overrides` section for that (e.g. Overrides: foo in class BaseClass)
    doShortGenerateDocTest(
      s"""class A extends BaseScalaClass {
         |  override def ${|}baseMethod: String = ???
         |}""".stripMargin,
      s"""$DefinitionStart
         |<a href="psi_element://A"><code>A</code></a>
         |override def <b>baseMethod</b>: <a href="psi_element://scala.Predef.String"><code>String</code></a>
         |$DefinitionEnd
         |<div class='content'>
         |<b>Description copied from class: </b>
         |<a href="psi_element://BaseScalaClass"><code>BaseScalaClass</code></a>
         |</div>
         |<div class='content'> description of base method from BaseScalaClass <p></div>
         |<table class='sections'><p></table>""".stripMargin
    )
  }

  def testMethodWithEmptyDocOverridingJavaMethod(): Unit = {
    getFixture.addFileToProject("BaseJavaClass.java",
      s"""public class BaseJavaClass {
         |  /** description of base method from BaseJavaClass */
         |  String ${|}baseMethod() { return null; }
         |}
         |""".stripMargin
    )
    doShortGenerateDocTest(
      s"""class A extends BaseJavaClass {
         |  override def ${|}baseMethod: String = ???
         |}
         |""".stripMargin,
      s"""$DefinitionStart
         |<a href="psi_element://A"><code>A</code></a>
         |override def <b>baseMethod</b>: <a href="psi_element://scala.Predef.String"><code>String</code></a>
         |$DefinitionEnd
         |<div class='content'>
         |<b>Description copied from class: </b>
         |<a href="psi_element://BaseJavaClass"><code>BaseJavaClass</code></a>
         |</div>
         |<div class='content'> description of base method from BaseJavaClass <p></div>
         |<table class='sections'><p></table>""".stripMargin
    )
  }

  def testMethodWithEmptyDocOverridingJavaMethod_TagsInJavadoc(): Unit = {
    getFixture.addFileToProject("BaseJavaClass.java",
      s"""public class BaseJavaClass {
         |  /** @return modules to compile before run. Empty list to build project */
         |  String[] getModules() {  return null;  }
         |}""".stripMargin
    )
    doShortGenerateDocTest(
      s"""class A extends BaseJavaClass {
         |  override def ${|}getModules: String = ???
         |}
         |""".stripMargin,
      s"""$DefinitionStart
         |<a href="psi_element://A"><code>A</code></a>
         |override def <b>getModules</b>: <a href="psi_element://scala.Predef.String"><code>String</code></a>
         |$DefinitionEnd
         |<div class='content'>
         |<b>Description copied from class: </b>
         |<a href="psi_element://BaseJavaClass"><code>BaseJavaClass</code></a>
         |</div>
         |<table class='sections'>
         |<p><tr>
         |<td valign='top' class='section'><p>Returns:</td>
         |<td valign='top'><p>modules to compile before run. Empty list to build project </td>
         |</table>""".stripMargin
    )
  }

  def testMethodWithAccessModifier(): Unit =
    doShortGenerateDocTest(
      s"""class X {
         |  protected def ${|}f1 = 42
         |}
         |""".stripMargin,
      s"""$DefinitionStart
         |<a href="psi_element://X"><code>X</code></a>
         |protected def <b>f1</b>: <a href="psi_element://scala.Int"><code>Int</code></a>
         |$DefinitionEnd""".stripMargin
    )

  def testMethodWithAccessModifierWithThisQualifier(): Unit =
    doShortGenerateDocTest(
      s"""class X {
         |  protected[this] def ${|}f1 = 42
         |}
         |""".stripMargin,
      s"""$DefinitionStart
         |<a href="psi_element://X"><code>X</code></a>
         |protected[this] def <b>f1</b>: <a href="psi_element://scala.Int"><code>Int</code></a>
         |$DefinitionEnd""".stripMargin
    )

  def testClassWithVariousGenericsWithBounds(): Unit =
    doShortGenerateDocTest(
      s"""trait Trait[A]
         |abstract class ${|}Class[T <: Trait[_ >: Object]]
         |  extends Comparable[_ <: Trait[_ >: String]]""".stripMargin,
      s"""$DefinitionStart
         |abstract class <b>Class</b>[T &lt;: Trait[_ &gt;: Object]]
         | extends <a href="psi_element://java.lang.Comparable"><code>Comparable</code></a>[_ &lt;:
         | <a href="psi_element://Trait"><code>Trait</code></a>[_ &gt;:
         | <a href="psi_element://scala.Predef.String"><code>String</code></a>]]
         |$DefinitionEnd""".stripMargin
    )

  def testHigherKindedTypeParameters(): Unit =
    doShortGenerateDocTest(
      s"""object O {
         |  def ${|}f[A[_, B]] = 42
         |}""".stripMargin,
      s"""$DefinitionStart
         |<a href="psi_element://O"><code>O</code></a>
         |def <b>f</b>[A[_, B]]: <a href="psi_element://scala.Int"><code>Int</code></a>
         |$DefinitionEnd""".stripMargin
    )

  def testHigherKindedTypeParameters_1(): Unit =
    doShortGenerateDocTest(
      s"""trait ${|}T[X[_, Y[_, Z]]]
         |""".stripMargin,
      s"""$DefinitionStart
         |trait <b>T</b>[X[_, Y[_, Z]]]
         |$DefinitionEnd""".stripMargin
    )

  def testTypeAlias(): Unit =
    doShortGenerateDocTest(
      s"""object O {
         |  type ${|}MyType = java.lang.Exception
         |}""".stripMargin,
      s"""$DefinitionStart
         |<a href="psi_element://O"><code>O</code></a>
         |type <b>MyType</b> = <a href="psi_element://java.lang.Exception"><code>Exception</code></a>
         |$DefinitionEnd""".stripMargin
    )


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

  private def sharedValVarExpectedDoc(docDefinitionSection: String): String = {
    s"""$DocStart
       |$DefinitionStart$docDefinitionSection$DefinitionEnd
       |$ContentStart   some description     $ContentEnd
       |$SectionsStart
       |<p><tr><td valign='top' class='section'><p>Note:</td><td valign='top'>some note</td>
       |$SectionsEnd
       |$DocEnd""".stripMargin
  }

  private def doValVarTest(definitionBody: String, expectedDocDefinitionSection: String): Unit =
    doGenerateDocTest(
      sharedValVarDefinition(definitionBody),
      sharedValVarExpectedDoc(expectedDocDefinitionSection)
    )

  def testMemberValue(): Unit =
    doValVarTest(
      s"""val ${|}v = 1""",
      """<a href="psi_element://X"><code>X</code></a>val <b>v</b>: <a href="psi_element://scala.Int"><code>Int</code></a>"""
    )

  def testMemberVariable(): Unit =
    doValVarTest(
      s"""var ${|}v = 1""",
      """<a href="psi_element://X"><code>X</code></a>var <b>v</b>: <a href="psi_element://scala.Int"><code>Int</code></a>"""
    )

  def testMemberValuePattern(): Unit =
    doValVarTest(
      s"""val (v1, ${|}v2) = (1, "str")""",
      """<a href="psi_element://X"><code>X</code></a>val <b>v2</b>: <a href="psi_element://java.lang.String"><code>String</code></a>"""
    )

  def testMemberValuePattern_1(): Unit =
    doValVarTest(
      s"""val Tuple2(v1, ${|}v2) = (1, "str")""",
      // java.lang.String cause it's an inferred type
      """<a href="psi_element://X"><code>X</code></a>val <b>v2</b>: <a href="psi_element://java.lang.String"><code>String</code></a>"""
    )

  def testMemberVariablePattern(): Unit =
    doValVarTest(
      s"""var (v1, ${|}v2) = (1, "str")""",
      """<a href="psi_element://X"><code>X</code></a>var <b>v2</b>: <a href="psi_element://java.lang.String"><code>String</code></a>"""
    )

  def testMemberVariablePattern_1(): Unit =
    doValVarTest(
      s"""var Tuple2(v1, ${|}v2) = (1, "str")""",
      """<a href="psi_element://X"><code>X</code></a>var <b>v2</b>: <a href="psi_element://java.lang.String"><code>String</code></a>"""
    )

  def testMemberValue_Abstract(): Unit =
    doValVarTest(
      s"""val ${|}v: Int""",
      """<a href="psi_element://X"><code>X</code></a>val <b>v</b>: <a href="psi_element://scala.Int"><code>Int</code></a>"""
    )

  def testMemberVariable_Abstract(): Unit =
    doValVarTest(
      s"""var ${|}v: Int""",
      """<a href="psi_element://X"><code>X</code></a>var <b>v</b>: <a href="psi_element://scala.Int"><code>Int</code></a>"""
    )
}