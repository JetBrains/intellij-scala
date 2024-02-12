package org.jetbrains.plugins.scala.editor.documentationProvider

import org.jetbrains.plugins.scala.editor.documentationProvider.util.ScalaDocumentationsDefinitionSectionTesting
import org.jetbrains.plugins.scala.util.AliasExports.stringClass

final class ScalaDocumentationProviderTest_Scala2Definitions extends ScalaDocumentationProviderTestBase
  with ScalaDocumentationsDefinitionSectionTesting {

  def testClass(): Unit =
    doGenerateDocDefinitionTest(
      s"""package a.b.c
         |
         |class ${|}A
         |""".stripMargin,
      s"""<icon src="AllIcons.Nodes.Package"/> <a href="psi_element://a.b.c"><code>a.b.c</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">A</span>
         |""".stripMargin
    )

  def testClass_TopLevel(): Unit =
    doGenerateDocDefinitionTest(
      s"""class ${|}A""",
      """
        |<span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">A</span>
        |""".stripMargin
    )

  def testClass_WithSuperClass(): Unit =
    doGenerateDocDefinitionTest(
      s"""package a.b.c
         |
         |class ${|}A extends Exception""".stripMargin,
      s"""<icon src="AllIcons.Nodes.Package"/> <a href="psi_element://a.b.c"><code>a.b.c</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">A</span>
         |<span style="color:#000080;font-weight:bold;">extends</span> <span style="color:#000000;"><a href="psi_element://java.lang.Exception"><code>Exception</code></a></span>
         |""".stripMargin
    )

  def testTrait(): Unit =
    doGenerateDocDefinitionTest(
      s"""package a.b.c
         |
         |trait ${|}T
         |""".stripMargin,
      s"""<icon src="AllIcons.Nodes.Package"/> <a href="psi_element://a.b.c"><code>a.b.c</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">trait</span> <span style="color:#000000;">T</span>
         |""".stripMargin
    )

  def testObject(): Unit =
    doGenerateDocDefinitionTest(
      s"""package a.b.c
         |
         |object ${|}O
         |""".stripMargin,
      s"""<icon src="AllIcons.Nodes.Package"/> <a href="psi_element://a.b.c"><code>a.b.c</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">object</span> <span style="color:#000000;">O</span>
         |""".stripMargin
    )

  def testTypeAlias(): Unit =
    doGenerateDocDefinitionTest(
      s"""object O {
         |  type ${|}MyType = java.lang.Exception
         |}""".stripMargin,
      s"""
         |<a href="psi_element://O"><code>O</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">type</span> <span style="color:#20999d;">MyType</span> = <span style="color:#000000;"><a href="psi_element://java.lang.Exception"><code>Exception</code></a></span>
         |""".stripMargin
    )

  def testClass_WithSuperClassAndTraits(): Unit =
    doGenerateDocDefinitionTest(
      s"""package a.b.c
         |trait T1
         |trait T2
         |class ${|}A extends Exception with T1 with T2""".stripMargin,
      s"""<icon src="AllIcons.Nodes.Package"/> <a href="psi_element://a.b.c"><code>a.b.c</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">A</span>
         |<span style="color:#000080;font-weight:bold;">extends</span> <span style="color:#000000;"><a href="psi_element://java.lang.Exception"><code>Exception</code></a></span>
         |<span style="color:#000080;font-weight:bold;">with</span> <span style="color:#000000;"><a href="psi_element://a.b.c.T1"><code>T1</code></a></span> <span style="color:#000080;font-weight:bold;">with</span> <span style="color:#000000;"><a href="psi_element://a.b.c.T2"><code>T2</code></a></span>
         |""".stripMargin
    )

  // for not it's not a business requirement just fixing implementation in tests
  def testClassExtendingAnotherClassShouldNotInheritDoc(): Unit =
    doGenerateDocDefinitionTest(
      s"""/** description of A */
         |class A
         |class ${|}B extends A
         |""".stripMargin,
      s"""
         |<span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">B</span>
         |<span style="color:#000080;font-weight:bold;">extends</span> <span style="color:#000000;"><a href="psi_element://A"><code>A</code></a></span>
         |""".stripMargin
    )

  def testClass_WithVariousGenericsWithBounds(): Unit =
    doGenerateDocDefinitionTest(
      s"""trait Trait[A]
         |abstract class ${|}Class[T <: Trait[_ >: Object]]
         |  extends Comparable[_ <: Trait[_ >: String]]""".stripMargin,
      s"""<span style="color:#000080;font-weight:bold;">abstract</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">Class</span>[<span style="color:#20999d;">T</span> &lt;: <span style="color:#000000;"><a href="psi_element://Trait"><code>Trait</code></a></span>[_ &gt;: <span style="color:#000000;"><a href="psi_element://java.lang.Object"><code>Object</code></a></span>]]
         |<span style="color:#000080;font-weight:bold;">extends</span> <span style="color:#000000;"><a href="psi_element://java.lang.Comparable"><code>Comparable</code></a></span>[_ &lt;: <span style="color:#000000;"><a href="psi_element://Trait"><code>Trait</code></a></span>[_ &gt;: <span style="color:#000000;"><a href="psi_element://java.lang.String"><code>String</code></a></span>]]""".stripMargin
    )

  def testMethod_WithAccessModifier(): Unit =
    doGenerateDocDefinitionTest(
      s"""class X {
         |  protected def ${|}f1 = 42
         |}
         |""".stripMargin,
      s"""<a href="psi_element://X"><code>X</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">protected</span> <span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">f1</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>
         |""".stripMargin
    )

  def testMethod_WithAccessModifierWithThisQualifier(): Unit =
    doGenerateDocDefinitionTest(
      s"""class X {
         |  protected[this] def ${|}f1 = 42
         |}
         |""".stripMargin,
      s"""<a href="psi_element://X"><code>X</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">protected</span>[<span style="color:#000080;font-weight:bold;">this</span>] <span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">f1</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>""".stripMargin
    )

  def testMethod_HigherKindedTypeParameters(): Unit =
    doGenerateDocDefinitionTest(
      s"""object O {
         |  def ${|}f[A[_, B]] = 42
         |}""".stripMargin,
      s"""<a href="psi_element://O"><code>O</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">f</span>[<span style="color:#20999d;">A</span>[<span style="color:#20999d;">_</span>, <span style="color:#20999d;">B</span>]]: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>
         |""".stripMargin
    )

  def testMethod_HigherKindedTypeParameters_1(): Unit =
    doGenerateDocDefinitionTest(
      s"""trait ${|}T[X[_, Y[_, Z]]]
         |""".stripMargin,
      s"""<span style="color:#000080;font-weight:bold;">trait</span> <span style="color:#000000;">T</span>[<span style="color:#20999d;">X</span>[<span style="color:#20999d;">_</span>, <span style="color:#20999d;">Y</span>[<span style="color:#20999d;">_</span>, <span style="color:#20999d;">Z</span>]]]
         |""".stripMargin
    )

  def testBindingPatternFromCaseClause(): Unit =
    doGenerateDocDefinitionTest(
      s"""Array(1, 2, 3) match {
         |  case Array(_, x, y) =>
         |    println(${|}x + y)
         |}""".stripMargin,
      """
        |Pattern: <span style="color:#008000;font-weight:bold;">x</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>
        |""".stripMargin
    )

  def testMethodAnonymousClass(): Unit =
    doGenerateDocDefinitionTest(
      s"""new Object {
         |  def ${|}foo = 42
         |}""".stripMargin,
      """
        |<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">foo</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>
        |""".stripMargin
    )

  def testTrait_SpecialChars_InfixType(): Unit =
    doGenerateDocDefinitionTest(
      s"""object A {
         |  trait <:<[A,B]
         |  def ${|}f(a: Int <:< String): Unit = {}
         |}""".stripMargin,
      s"""<a href="psi_element://A"><code>A</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">f</span>(a: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span> <span style="color:#000000;"><a href="psi_element://A.&lt;:&lt;"><code>&lt;:&lt;</code></a></span> <span style="color:#000000;"><a href="psi_element://$stringClass"><code>String</code></a></span>): <span style=""><a href="psi_element://scala.Unit"><code>Unit</code></a></span>
         |""".stripMargin
    )

  def testBrokenCode_ClassWithoutExtendsListItems(): Unit =
    doGenerateDocDefinitionTest(
      s"""class Test${|} extends""".stripMargin,
      """<span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">Test</span>
        |""".stripMargin
    )

  def testAnnotationArgs(): Unit = {
    val fileContent =
      s"""class Outer {
         |  @deprecated("use 'foo' instead", "1.2.3")
         |  @transient
         |  def ${|}boo() {}
         |}""".stripMargin

    val expectedDoc =
      """<a href="psi_element://Outer"><code>Outer</code></a>
        |
        |<span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://scala.deprecated"><code>deprecated</code></a></span>(<span style="color:#008000;font-weight:bold;">&quot;use 'foo' instead&quot;</span>, <span style="color:#008000;font-weight:bold;">&quot;1.2.3&quot;</span>)
        |<span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://scala.transient"><code>transient</code></a></span>
        |<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">boo</span>(): <span style=""><a href="psi_element://scala.Unit"><code>Unit</code></a></span>
        |""".stripMargin
    doGenerateDocDefinitionTest(fileContent, expectedDoc)
  }

  def testAnnotationArgs_WithInnerHtmlTextShouldBeEscaped(): Unit = {
    val fileContent =
      s"""class Outer {
         |  @deprecatedName("inner tags <p>example</p>", "since 2020")
         |  def ${|}boo() = {}
         |}""".stripMargin

    val expectedDoc =
      """<a href="psi_element://Outer"><code>Outer</code></a>
        |
        |<span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://scala.deprecatedName"><code>deprecatedName</code></a></span>(<span style="color:#008000;font-weight:bold;">&quot;inner tags &lt;p&gt;example&lt;/p&gt;&quot;</span>, <span style="color:#008000;font-weight:bold;">&quot;since 2020&quot;</span>)
        |<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">boo</span>(): <span style=""><a href="psi_element://scala.Unit"><code>Unit</code></a></span>
        |""".stripMargin
    doGenerateDocDefinitionTest(fileContent, expectedDoc)
  }

  def testAnnotation_Throws_ShouldIgnoreExceptionClassArgument(): Unit = {
    // NOTE: the exception class is already shown in the annotation type, see SCL-17608
    val fileContent =
      s"""@throws(classOf[Exception])
         |@throws[Exception]("reason 1")
         |@throws(classOf[java.util.ConcurrentModificationException])
         |@throws[java.util.ConcurrentModificationException]("reason 2")
         |def ${CARET}goo() {}
         |""".stripMargin

    val expectedDoc =
      """<span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://scala.throws"><code>throws</code></a></span>[<span style="color:#808000;"><a href="psi_element://java.lang.Exception"><code>Exception</code></a></span>](classOf[Exception])
        |<span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://scala.throws"><code>throws</code></a></span>[<span style="color:#808000;"><a href="psi_element://java.lang.Exception"><code>Exception</code></a></span>](<span style="color:#008000;font-weight:bold;">&quot;reason 1&quot;</span>)
        |<span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://scala.throws"><code>throws</code></a></span>[<span style="color:#808000;"><a href="psi_element://java.util.ConcurrentModificationException"><code>ConcurrentModificationException</code></a></span>](classOf[java.util.ConcurrentModificationException])
        |<span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://scala.throws"><code>throws</code></a></span>[<span style="color:#808000;"><a href="psi_element://java.util.ConcurrentModificationException"><code>ConcurrentModificationException</code></a></span>](<span style="color:#008000;font-weight:bold;">&quot;reason 2&quot;</span>)
        |<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">goo</span>(): <span style=""><a href="psi_element://scala.Unit"><code>Unit</code></a></span>
        |""".stripMargin
    doGenerateDocDefinitionTest(fileContent, expectedDoc)
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
      s"""
         |@deprecated(42)
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
      s"""
         |@deprecated(true)
         |def ${|}foo() = {}
         |""".stripMargin

    val expectedDoc =
      """<span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://scala.deprecated"><code>deprecated</code></a></span>(<span style="color:#000080;font-weight:bold;">true</span>)
        |<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">foo</span>(): <span style=""><a href="psi_element://scala.Unit"><code>Unit</code></a></span>
        |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedDoc)
  }

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

  def testAbstractClass(): Unit = {
    val fileContent =
      s"""
         |abstract class ${|}AbstractClass
         |""".stripMargin

    val expectedContent =
      """<span style="color:#000080;font-weight:bold;">abstract</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">AbstractClass</span>
        |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testAnnotation(): Unit = {
    val fileContent =
      s"""
         |@scala.io.Source(url = "https://foo.com/")
         |trait ${|}Foo
         |""".stripMargin

    val expectedContent =
      """<span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://scala.io.Source"><code>Source</code></a></span>(url = <span style="color:#008000;font-weight:bold;">&quot;https://foo.com/&quot;</span>)
        |<span style="color:#000080;font-weight:bold;">trait</span> <span style="color:#000000;">Foo</span>
        |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testValWithFunctionType(): Unit = {
    val fileContent =
      s"""class Outer {
         |  val ${|}f = (x: Int) => x
         |}""".stripMargin

    val expectedContent =
      """<a href="psi_element://Outer"><code>Outer</code></a>
        |
        |<span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-style:italic;">f</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span> => <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>
        |""".stripMargin

    val expectedContent2 =
      """
        |<span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-style:italic;">f</span>: <span style="color:#20999d;"><span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span> => <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span></span>
        |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testImplicit(): Unit = {
    val fileContent =
      s"""
         |import scala.concurrent.ExecutionContext
         |def ${|}f()(implicit ec: ExecutionContext): Int = ???
         |""".stripMargin

    val expectedContent =
      """<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">f</span>()(<span style="color:#000080;font-weight:bold;">implicit</span> ec: <span style="color:#000000;"><a href="psi_element://scala.concurrent.ExecutionContext"><code>ExecutionContext</code></a></span>): <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>
        |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testTwoParameters(): Unit = {
    val fileContent =
      s"""
         |def ${|}foo(a: Int, b: Int): Unit = ???
         |""".stripMargin

    val expectedContent =
      """<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">foo</span>(a: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>, b: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>): <span style=""><a href="psi_element://scala.Unit"><code>Unit</code></a></span>
        |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testThreeParameters(): Unit = {
    val fileContent =
      s"""
         |def ${|}foo(a: Int, b: Int, c: Int): Unit = ???
         |""".stripMargin

    val expectedContent =
      """<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">foo</span>(a: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>, b: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>, c: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>): <span style=""><a href="psi_element://scala.Unit"><code>Unit</code></a></span>
        |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testImplicitParameters(): Unit = {
    val fileContent =
      s"""
         |def ${|}foo(a: Int, b: Int)(implicit c: Int, d: Int): Unit = ???
         |""".stripMargin

    val expectedContent =
      """<span style="color:#000080;font-weight:bold;">def</span>
        | <span style="color:#000000;">foo</span>(a: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>, b: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>)
        |(<span style="color:#000080;font-weight:bold;">implicit</span> c: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>, d: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>)
        |: <span style=""><a href="psi_element://scala.Unit"><code>Unit</code></a></span>
        |""".stripMargin.withoutNewLines

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testMethod_WithParametersWithDefaultValues(): Unit =
    doGenerateDocDefinitionTest(
      s"""class A {
         |  def ${|}foo(i: Int, s: String = "default value", b: Boolean): String = ???
         |}""".stripMargin,
      s"""<a href="psi_element://A"><code>A</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">foo</span>(i: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>, s: <span style="color:#000000;"><a href="psi_element://java.lang.String"><code>String</code></a></span> = …, b: <span style=""><a href="psi_element://scala.Boolean"><code>Boolean</code></a></span>): <span style="color:#000000;"><a href="psi_element://java.lang.String"><code>String</code></a></span>
         |""".stripMargin
    )

  def testNestedTraits(): Unit = {
    val fileContent =
      s"""
       |trait MyTrait[T]
       |abstract class Class1[T, V]
       |class ${|}Class2[T]() extends Class1[T, MyTrait[T]]
       |""".stripMargin

    val expectedContent =
      s"""
         |<span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">Class2</span>[<span style="color:#20999d;">T</span>]()
         |<span style="color:#000080;font-weight:bold;">extends</span> <span style="color:#000000;"><a href="psi_element://Class1"><code>Class1</code></a></span>[<span style="color:#20999d;">T</span>, <span style="color:#000000;"><a href="psi_element://MyTrait"><code>MyTrait</code></a></span>[<span style="color:#20999d;">T</span>]]
         |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testPrivateVal(): Unit = {
    val fileContent =
      s"""class Outer {
         |  private val ${|}a: Int = 0
         |}""".stripMargin

    val expectedContent =
      s"""<a href="psi_element://Outer"><code>Outer</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">private</span> <span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-style:italic;">a</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>
         |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testProtectedVal(): Unit = {
    val fileContent =
      s"""class Outer {
         |  protected val ${|}a: Int = 0
         |}""".stripMargin

    val expectedContent =
      s"""<a href="psi_element://Outer"><code>Outer</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">protected</span> <span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-style:italic;">a</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>
         |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testPrivateLazyVal(): Unit = {
    val fileContent =
      s"""class Outer {
         |  private lazy val ${|}a: Int = 0
         |}""".stripMargin

    val expectedContent =
      s"""<a href="psi_element://Outer"><code>Outer</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">private</span> <span style="color:#000080;font-weight:bold;">lazy</span> <span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-style:italic;">a</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span>
         |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }

  def testUntupledPrivateVal(): Unit = {
    val fileContent =
      s"""class Outer {
         |  val a: Int = 0
         |  val b: String = ""
         |  private val (c, ${|}d) = (a, b)
         |}""".stripMargin

    val expectedContent =
      s"""<a href="psi_element://Outer"><code>Outer</code></a>
         |
         |<span style="color:#000080;font-weight:bold;">private</span> <span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-style:italic;">d</span>: <span style="color:#000000;"><a href="psi_element://java.lang.String"><code>String</code></a></span>
         |""".stripMargin

    doGenerateDocDefinitionTest(fileContent, expectedContent)
  }
}
