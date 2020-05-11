package org.jetbrains.plugins.scala.editor.documentationProvider

// TODO: Currently this contains just simple health check tests.
//  Add more before improving quick-doc SCL-17101
class ScalaDocumentationProviderTest extends ScalaDocumentationProviderTestBase {

  def testClass(): Unit =
    doShortTest(
      s"""/** description of A */
         |class ${|}A {}""".stripMargin,
      """<div class="definition"><pre>class <b>A</b></pre></div>
        |<div class='content'> description of A <p></div>
        |<table class='sections'><p></table>
        |""".stripMargin
    )

  def testClassInPackage(): Unit =
    doShortTest(
      s"""package a.b.c
         |/** description of A */
         |class ${|}A""".stripMargin,
      """<div class="definition"><font size="-1"><b>a.b.c</b></font><pre>class <b>A</b></pre></div>
        |<div class='content'> description of A <p></div>
        |<table class='sections'><p></table>
        |""".stripMargin
    )

  // for not it's not a business requirement just fixing implementation in tests
  def testClassExtendingAnotherClassShouldNotInheritDoc(): Unit =
    doShortTest(
      s"""/** description of A */
         |class A
         |class ${|}B extends A
         |""".stripMargin,
      """<div class="definition"><pre>
        |class <b>B</b> extends <a href="psi_element://A"><code>A</code></a>
        |</pre></div>
        |""".stripMargin
    )

  def testClassExtendingAnotherJavaClassShouldNotInheritDoc(): Unit = {
    getFixture.addFileToProject("J.java",
      """/** description of base class J */
        |class J {}
        |""".stripMargin
    )
    doShortTest(
      s"""class ${|}B extends J""".stripMargin,
      """<div class="definition"><pre>
        |class <b>B</b> extends <a href="psi_element://J"><code>J</code></a>
        |</pre></div>
        |""".stripMargin
    )
  }

  def testMethod(): Unit =
    doShortTest(
      s"""class A {
         |  /** description of foo */
         |  def ${|}foo: String = ???
         |}""".stripMargin,
      """<div class="definition"><a href="psi_element://A"><code>A</code></a><pre>def <b>foo</b>: String</pre></div>
        |<div class='content'> description of foo <p></div>
        |<table class='sections'><p></table>
        |""".stripMargin
    )

  def testMethodOverriding(): Unit = {
    getFixture.addFileToProject("BaseScalaClass.scala",
      """class BaseScalaClass {
        |  /** description of base method from BaseScalaClass */
        |  def baseMethod: String = ???
        |}
        |""".stripMargin
    )
    doShortTest(
      s"""class A extends BaseScalaClass {
         |  /** description of base method from A */
         |  def ${|}baseMethod: String = ???
         |}
         |""".stripMargin,
      """<div class="definition">
        |<a href="psi_element://A"><code>A</code></a>
        |<pre>def <b>baseMethod</b>: String</pre>
        |</div>
        |<div class='content'> description of base method from A <p></div>
        |<table class='sections'><p></table>
        |""".stripMargin
    )
  }

  def testMethodWithEmptyDocOverriding(): Unit = {
    getFixture.addFileToProject("BaseScalaClass.scala",
      """class BaseScalaClass {
        |  /** description of base method from BaseScalaClass */
        |  def baseMethod: String = ???
        |}
        |""".stripMargin
    )
    // TODO: do we need override keyword as text in <pre> section?
    //  Java uses `Overrides` section for that (e.g. Overrides: foo in class BaseClass)
    doShortTest(
      s"""class A extends BaseScalaClass {
         |  override def ${|}baseMethod: String = ???
         |}""".stripMargin,
      """<div class="definition">
        |<a href="psi_element://A"><code>A</code></a>
        |<pre>override def <b>baseMethod</b>: String</pre>
        |</div>
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
    doShortTest(
      s"""class A extends BaseJavaClass {
         |  override def ${|}baseMethod: String = ???
         |}
         |""".stripMargin,
      """<div class="definition">
        |<a href="psi_element://A"><code>A</code></a>
        |<pre>override def <b>baseMethod</b>: String</pre>
        |</div>
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
    doShortTest(
      s"""class A extends BaseJavaClass {
         |  override def ${|}getModules: String = ???
         |}
         |""".stripMargin,
      """<div class="definition">
        |<a href="psi_element://A"><code>A</code></a>
        |<pre>override def <b>getModules</b>: String</pre>
        |</div>
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
}