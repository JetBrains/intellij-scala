package org.jetbrains.plugins.scala.editor.documentationProvider

class ScalaDocumentationProviderOldTest extends ScalaDocumentationProviderTestBase {

  def testSimpleClassDefinition(): Unit =
    doShortGenerateDocTest(
      s"""package foo
         |
         |class ${|}A
         |""".stripMargin,
      s"""$DefinitionStart
         |foo
         |class <b>A</b>
         |$DefinitionEnd""".stripMargin
    )

  def testSimpleClassDefinition_WithoutPackage(): Unit =
    doGenerateDocTest(
      s"""class ${|}A""",
      s"""<html><body>
         |$DefinitionStart
         |class <b>A</b>
         |$DefinitionEnd
         |</body></html>""".stripMargin
    )

  def testSimpleTags(): Unit = {
    val fileText =
      s"""/**
         |  * Description
         |  *
         |  * @see something
         |  * @example 2 + 2 = 4
         |  * @note some note
         |  * @todo some task
         |  */
         |class ${|}A {}
         |""".stripMargin
    val expectedDoc  =
      s"""$ContentStart   Description     <p>$ContentEnd
         |$SectionsStart<p>
         |<tr><td valign='top' class='section'><p>See also:</td>
         |<td valign='top'><dd>something</dd></td>
         |<tr><td valign='top' class='section'><p>Example:</td>
         |<td valign='top'>2 + 2 = 4</td>
         |<tr><td valign='top' class='section'><p>Note:</td>
         |<td valign='top'>some note</td>
         |<tr><td valign='top' class='section'><p>Todo:</td>
         |<td valign='top'>some task</td>
         |$SectionsEnd""".stripMargin

    doGenerateDocWithoutDefinitionTest(fileText,  expectedDoc)
  }

  //note strong requirement, just fixing current behaviour
  def testAuthorTagShouldBeIgnored(): Unit = {
    val fileText =
      s"""/**
         |  * Description
         |  * @author Some Name 1
         |  * @author Some Name 2
         |  */
         |class ${|}A {}
         |""".stripMargin
    val expectedDoc  =
      s"""$ContentStart   Description   <p>$ContentEnd
         |$EmptySections""".stripMargin

    doGenerateDocWithoutDefinitionTest(fileText,  expectedDoc)
  }

  def testTagsWithParamsForMethod(): Unit = {
    val fileText =
      s"""/**
         | * Description
         | *
         | * @note some note
         | * @param i aaa
         | * @param j bbb
         | * @param k ccc
         | * @tparam T ddd
         | * @tparam E eee
         | *           ggg
         | */
         |def ${|}f[T, E](i: Int, j: Int, k: String) {}
         |""".stripMargin
    val expectedDoc  =
      s"""$ContentStart   Description     <p>$ContentEnd
         |$SectionsStart<p>
         |<tr><td valign='top' class='section'><p>Params:</td>
         |<td valign='top'>i &ndash; aaa  <p>j &ndash; bbb  <p>k &ndash; ccc  </td>
         |<tr><td valign='top' class='section'><p>Type parameters:</td>
         |<td valign='top'>&lt;T&gt; &ndash;  ddd  <p>&lt;E&gt; &ndash;  eee            ggg</td>
         |<tr><td valign='top' class='section'><p>Note:</td>
         |<td valign='top'>some note</td>
         |$SectionsEnd""".stripMargin

    doGenerateDocWithoutDefinitionTest(fileText, expectedDoc)
  }

  def testThrowsTagForMethod(): Unit = {
    val fileText =
      s"""/**
         |  * @throws Exception some condition 1
         |  * @throws java.lang.IllegalAccessException some condition 2
         |  */
         |def ${|}g() {}
         |""".stripMargin
    val expectedDoc  =
      s"""$SectionsStart<p>
         |<tr><td valign='top' class='section'><p>Throws:</td>
         |<td valign='top'>
         |<p><a href="psi_element://java.lang.Exception"><code>Exception</code></a>
         | &ndash; some condition 1
         |  <p><a href="psi_element://java.lang.IllegalAccessException"><code>IllegalAccessException</code></a>
         | &ndash; some condition 2</td>
         |$SectionsEnd""".stripMargin

    doGenerateDocWithoutDefinitionTest(fileText, expectedDoc)
  }

  def testTagsWithParamsForClass(): Unit = {
    val fileText =
      s"""/**
         |  * @note some note
         |  * @param o
         |  * @tparam E
         |  * @param f description for f
         |  * @tparam K description for K
         |  */
         |class ${|}A[E, K](o: Any, f: AnyVal) {}
         |""".stripMargin
    val expectedDoc  =
      s"""$SectionsStart<p>
         |<tr><td valign='top' class='section'><p>Params:</td>
         |<td valign='top'>o &ndash;  <p>f &ndash; description for f  </td>
         |<tr><td valign='top' class='section'><p>Type parameters:</td>
         |<td valign='top'>&lt;E&gt; &ndash;  <p>&lt;K&gt; &ndash;  description for K</td>
         |<tr><td valign='top' class='section'><p>Note:</td>
         |<td valign='top'>some note</td>
         |$SectionsEnd""".stripMargin

    doGenerateDocWithoutDefinitionTest(fileText, expectedDoc)
  }

  def testTagsWithParamsForTypeAlias(): Unit = {
    val fileText =
      s"""/**
         |  * @tparam A
         |  * @tparam B description for B
         |  * @tparam C
         |  */
         |type ${|}myType[A, B, C] = java.util.HashMap[A, java.util.HashMap[B, C]]
         |""".stripMargin
    val expectedDoc  =
      s"""$SectionsStart<p>
         |<tr><td valign='top' class='section'><p>Type parameters:</td>
         |<td valign='top'>&lt;A&gt; &ndash;  <p>&lt;B&gt; &ndash;  description for B  <p>&lt;C&gt; &ndash; </td>
         |$SectionsEnd""".stripMargin
    doGenerateDocWithoutDefinitionTest(fileText, expectedDoc)
  }

  def testFontStyles_Nested_Underscore_Power_Italic(): Unit =
    doGenerateDocWithoutDefinitionTest(
      s"""/**
         | * __xxx^yyy''zzz''yyy^xxx__
         | */
         |val ${|}a = 1
         |""".stripMargin,
      s"$ContentStart   <u>xxx<sup>yyy<i>zzz</i>yyy</sup>xxx</u> $ContentEnd$EmptySections"
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
      s"""$SectionsStart<p>
         |<tr><td valign='top' class='section'><p>Note:</td>
         |<td valign='top'><b><u>bold with underscore text</u></b></td>
         |<tr><td valign='top' class='section'><p>See also:</td>
         |<td valign='top'><dd>abc</dd><sub>index text <u>index with underscore text</u> index text 2</sub><dd>def</dd></td>
         |$SectionsEnd""".stripMargin

    doGenerateDocWithoutDefinitionTest(fileText, expectedDoc)
  }

  def testCodeLinks(): Unit = {
    val fileText =
      s"""/**
         | * [[http://jetbrains.ru     ]]
         | * [[http://jetbrains.com/idea/scala   Scala Plugin        ]]
         | * [[http://google.com This is google]]
         | * [[http://scala-lang.org]]
         | * ,,__[[http://jetbrains.com]]__,,
         | * [[java.lang.String]] ^[[java.lang.Integer]]^
         | */
         |val ${|}a = 1
         |""".stripMargin
    val expectedDoc =
      s"""$ContentStart
         |   <a href="http://jetbrains.ru     ">http://jetbrains.ru     </a>
         |   <a href="http://jetbrains.com/idea/scala">  Scala Plugin</a>
         |   <a href="http://google.com">This is google</a>
         |   <a href="http://scala-lang.org">http://scala-lang.org</a>
         |   <sub><u><a href="http://jetbrains.com">http://jetbrains.com</a></u></sub>
         |   <a href="psi_element://java.lang.String"><code>String</code></a> <sup><a href="psi_element://java.lang.Integer"><code>Integer</code></a></sup>
         | $ContentEnd
         |$EmptySections""".stripMargin

    doGenerateDocWithoutDefinitionTest(fileText, expectedDoc)
  }

  def testMalformedFontStyles(): Unit =
    doGenerateDocWithoutDefinitionTest(
      s"""/**
         | * ^blah-blah
         | *
         | * __aaaaaaa,,bbbbbbb
         | */
         |val ${|}a = 1
         |""".stripMargin,
      s"""$ContentStart
         |   <sup>blah-blah  </sup>
         |   <u>aaaaaaa<sub>bbbbbbb </sub>
         |$ContentEnd
         |$EmptySections""".stripMargin
    )


  def testMalformedTags(): Unit =
    doGenerateDocWithoutDefinitionTest(
      s"""/**
         |  * @gmm
         |  * @
         |  @see
         |  * @param
         |  * @note aaaaa
         |  */
         |val ${|}a = 1
         |""".stripMargin,
      s"""$SectionsStart<p>
         |<tr><td valign='top' class='section'><p>See also:</td>
         |<td valign='top'></td>
         |<tr><td valign='top' class='section'><p>Note:</td>
         |<td valign='top'>aaaaa</td>
         |$SectionsEnd""".stripMargin
    )

  def testInheritDocWithMacro(): Unit = {
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

    val expectedDoc  =
      s"""$DefinitionStart
         |<a href="psi_element://B"><code>B</code></a>
         |override def <b>f</b>(i: <a href="psi_element://scala.Int"><code>Int</code></a>):
         | <a href="psi_element://scala.Int"><code>Int</code></a>
         |$DefinitionEnd
         |$ContentStart
         |
         |      The function f defined in B returns some integer without no special property. (previously defined in A)
         |
         |      Some notes on implementation performance, the function runs in O(1).
         |   <p>
         |$ContentEnd
         |$SectionsStart<p>
         |<tr><td valign='top' class='section'><p>Params:</td><td valign='top'>i &ndash; An important parameter</td>
         |$SectionsEnd""".stripMargin

    "qwe" substring (0, 2)

    doShortGenerateDocTest(fileText, expectedDoc)
  }

  def testMacro_Simple(): Unit =
    doGenerateDocWithoutDefinitionTest(
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
      s"""$ContentStart
         |   Function defined in A
         | <p>$ContentEnd
         |$EmptySections""".stripMargin
    )

  def testMacroComplicated(): Unit =
    doGenerateDocWithoutDefinitionTest(
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
      s"""$ContentStart
         |   a VALUE1 b VALUE2 c [Cannot find macro: $$KEY_UNREACHED] <p>
         |$ContentEnd
         |$EmptySections""".stripMargin
    )

  def testMacroWiki(): Unit =
    doGenerateDocWithoutDefinitionTest(
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
      s"""$ContentStart
         |   <tt>None</tt>
         | <p>
         |$ContentEnd
         |$EmptySections""".stripMargin
    )

  def testAnnotationArgs(): Unit = {
    val fileContent =
      s"""class Outer {
         |  @deprecated("use 'foo' instead", "1.2.3")
         |  @transient
         |  def ${|}boo() {}
         |}""".stripMargin

    val expectedDoc =
      s"""$DefinitionStart
         |<a href="psi_element://Outer"><code>Outer</code></a>
         |@<a href="psi_element://scala.deprecated"><code>deprecated</code></a>(&quot;use 'foo' instead&quot;, &quot;1.2.3&quot;)
         |@<a href="psi_element://scala.transient"><code>transient</code></a>
         |def <b>boo</b>(): <a href="psi_element://scala.Unit"><code>Unit</code></a>
         |$DefinitionEnd""".stripMargin

    doShortGenerateDocTest(fileContent, expectedDoc)
  }

  def testAnnotationArgs_WithInnerHtmlTextShouldBeEscaped(): Unit = {
    val fileContent =
      s"""class Outer {
         |  @deprecatedName("inner tags <p>example</p>", "since 2020")
         |  def ${|}boo() {}
         |}""".stripMargin

    val expectedDoc =
      s"""$DefinitionStart
         |<a href="psi_element://Outer"><code>Outer</code></a>
         |@<a href="psi_element://scala.deprecatedName"><code>deprecatedName</code></a>(&quot;inner tags &lt;p&gt;example&lt;/p&gt;&quot;, &quot;since 2020&quot;)
         |def <b>boo</b>(): <a href="psi_element://scala.Unit"><code>Unit</code></a>
         |$DefinitionEnd""".stripMargin

    doShortGenerateDocTest(fileContent, expectedDoc)
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
      s"""$DefinitionStart
         |@<a href="psi_element://scala.throws"><code>throws</code></a>[<a href="psi_element://scala.Exception"><code>Exception</code></a>]
         |@<a href="psi_element://scala.throws"><code>throws</code></a>[<a href="psi_element://scala.Exception"><code>Exception</code></a>](&quot;reason 1&quot;)
         |@<a href="psi_element://scala.throws"><code>throws</code></a>[<a href="psi_element://java.util.ConcurrentModificationException"><code>ConcurrentModificationException</code></a>]
         |@<a href="psi_element://scala.throws"><code>throws</code></a>[<a href="psi_element://java.util.ConcurrentModificationException"><code>ConcurrentModificationException</code></a>](&quot;reason 2&quot;)
         |def <b>goo</b>(): <a href="psi_element://scala.Unit"><code>Unit</code></a>
         |$DefinitionEnd""".stripMargin

    doShortGenerateDocTest(fileContent, expectedDoc)
  }

  def testTrait_SpecialChars_InfixType(): Unit = {
    val fileContent =
      s"""object A {
         |  trait <:<[A,B]
         |  def ${|}f(a: Int <:< String): Unit = {}
         |}""".stripMargin

    val expectedDoc =
      s"""$DefinitionStart
         |<a href="psi_element://A"><code>A</code></a>
         |def <b>f</b>(a: <a href="psi_element://scala.Int"><code>Int</code></a> &lt;:&lt; <a href="psi_element://scala.Predef.String"><code>String</code></a>): <a href="psi_element://scala.Unit"><code>Unit</code></a>
         |$DefinitionEnd
         |""".stripMargin

    doShortGenerateDocTest(fileContent, expectedDoc)
  }
}
