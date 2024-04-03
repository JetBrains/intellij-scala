package org.jetbrains.plugins.scala.editor.documentationProvider

import org.jetbrains.plugins.scala.{DependencyManagerBase, ScalaVersion}
import org.jetbrains.plugins.scala.base.libraryLoaders.LibraryLoader
import org.jetbrains.plugins.scala.editor.documentationProvider.util.ScalaDocumentationsSectionsTestingBase
import org.jetbrains.plugins.scala.lang.navigation.ScalaSyntheticClassesNavigationalElementTest

class ScalaDocumentationProviderTest_SyntheticScalaLibraryElements
  extends ScalaDocumentationProviderTestBase
  with ScalaDocumentationsSectionsTestingBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override protected def librariesLoaders: Seq[LibraryLoader] =
    ScalaSyntheticClassesNavigationalElementTest.libraryLoadersWithSeparateScalaLibraries(super.librariesLoaders)


  def testSyntheticClass_Any(): Unit = {
    configureScalaFromFileText(s"""val x: ${CARET}Any = 0""")
    doGenerateDocAtCaretTest(s"""<html><head>$CssStyleSection<base href="jar:file:///${DependencyManagerBase.ivyHome}/cache/org.scala-lang/scala-library/srcs/scala-library-2.13.14-bin-ed3dfc9-SNAPSHOT-sources.jar!/scala/Any.scala"></head><body><div class='definition'><pre><icon src="AllIcons.Nodes.Package"/> <a href="psi_element://scala"><code>scala</code></a>
                               |
                               |<span style="color:#000080;font-weight:bold;">abstract</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">Any</span></pre></div><div class='content'>Class <tt>Any</tt> is the root of the Scala class hierarchy. Every class in a Scala execution environment inherits directly or indirectly from this class.<p>Starting with Scala 2.10 it is possible to directly extend <tt>Any</tt> using <i>universal traits</i>. A <i>universal trait</i> is a trait that extends <tt>Any</tt>, only has <tt>def</tt>s as members, and does no initialization.</p><p>The main use case for universal traits is to allow basic inheritance of methods for <a href="psi_element://scala.AnyVal"><code>value classes</code></a>. For example,</p><div class='styled-code'><pre style="padding: 0px; margin: 0px"><span style="color:#000080;font-weight:bold;">trait&#32;</span><span style="">Printable&#32;</span><span style="color:#000080;font-weight:bold;">extends&#32;</span><span style="">Any&#32;{<br></span><span style="">&#32;&#32;</span><span style="color:#000080;font-weight:bold;">def&#32;</span><span style="">print():&#32;Unit&#32;=&#32;println(</span><span style="color:#000080;font-weight:bold;">this</span><span style="">)<br></span><span style="">}<br></span><span style="color:#000080;font-weight:bold;">class&#32;</span><span style="">Wrapper(</span><span style="color:#000080;font-weight:bold;">val&#32;</span><span style="">underlying:&#32;Int)&#32;</span><span style="color:#000080;font-weight:bold;">extends&#32;</span><span style="">AnyVal&#32;</span><span style="color:#000080;font-weight:bold;">with&#32;</span><span style="">Printable<br></span><span style=""><br></span><span style="color:#000080;font-weight:bold;">val&#32;</span><span style="">w&#32;=&#32;</span><span style="color:#000080;font-weight:bold;">new&#32;</span><span style="">Wrapper(</span><span style="color:#0000ff;">3</span><span style="">)<br></span><span style="">w.print()</span></pre></div><p>See the <a href="https://docs.scala-lang.org/overviews/core/value-classes.html">Value Classes and Universal Traits</a> for more details on the interplay of universal traits and value classes.</p></div></body></html>""".stripMargin)
  }

  def testSyntheticClass_Singleton(): Unit = {
    configureScalaFromFileText(s"""val x: ${CARET}Singleton = 0""")
    doGenerateDocAtCaretTest(s"""<html><head>$CssStyleSection<base href="jar:file:///${DependencyManagerBase.ivyHome}/cache/org.scala-lang/scala-library/srcs/scala-library-2.13.14-bin-ed3dfc9-SNAPSHOT-sources.jar!/scala/Singleton.scala"></head><body><div class='definition'><pre><icon src="AllIcons.Nodes.Package"/> <a href="psi_element://scala"><code>scala</code></a>
                               |
                               |<span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">trait</span> <span style="color:#000000;">Singleton</span>
                               |<span style="color:#000080;font-weight:bold;">extends</span> <span style=""><a href="psi_element://scala.Any"><code>Any</code></a></span></pre></div><div class='content'><tt>Singleton</tt> is used by the compiler as a supertype for singleton types. This includes literal types, as they are also singleton types.<div class='styled-code'><pre style="padding: 0px; margin: 0px"><span style="">scala&gt;&#32;</span><span style="color:#000080;font-weight:bold;">object&#32;</span><span style="">A&#32;{&#32;</span><span style="color:#000080;font-weight:bold;">val&#32;</span><span style="">x&#32;=&#32;</span><span style="color:#0000ff;">42&#32;</span><span style="">}<br></span><span style="">defined&#32;</span><span style="color:#000080;font-weight:bold;">object&#32;</span><span style="">A<br></span><span style=""><br></span><span style="">scala&gt;&#32;implicitly[A.</span><span style="color:#000080;font-weight:bold;">type&#32;</span><span style="">&lt;:&lt;&#32;Singleton]<br></span><span style="">res12:&#32;A.</span><span style="color:#000080;font-weight:bold;">type&#32;</span><span style="">&lt;:&lt;&#32;Singleton&#32;=&#32;generalized&#32;constraint<br></span><span style=""><br></span><span style="">scala&gt;&#32;implicitly[A.x.</span><span style="color:#000080;font-weight:bold;">type&#32;</span><span style="">&lt;:&lt;&#32;Singleton]<br></span><span style="">res13:&#32;A.x.</span><span style="color:#000080;font-weight:bold;">type&#32;</span><span style="">&lt;:&lt;&#32;Singleton&#32;=&#32;generalized&#32;constraint<br></span><span style=""><br></span><span style="">scala&gt;&#32;implicitly[</span><span style="color:#0000ff;">42&#32;</span><span style="">&lt;:&lt;&#32;Singleton]<br></span><span style="">res14:&#32;</span><span style="color:#0000ff;">42&#32;</span><span style="">&lt;:&lt;&#32;Singleton&#32;=&#32;generalized&#32;constraint<br></span><span style=""><br></span><span style="">scala&gt;&#32;implicitly[Int&#32;&lt;:&lt;&#32;Singleton]<br></span><span style="">^<br></span><span style="">error:&#32;Cannot&#32;prove&#32;that&#32;Int&#32;&lt;:&lt;&#32;Singleton.</span></pre></div><p><tt>Singleton</tt> has a special meaning when it appears as an upper bound on a formal type parameter. Normally, type inference in Scala widens singleton types to the underlying non-singleton type. When a type parameter has an explicit upper bound of <tt>Singleton</tt>, the compiler infers a singleton type.</p><div class='styled-code'><pre style="padding: 0px; margin: 0px"><span style="">scala&gt;&#32;</span><span style="color:#000080;font-weight:bold;">def&#32;</span><span style="">check42[T](x:&#32;T)(</span><span style="color:#000080;font-weight:bold;">implicit&#32;</span><span style="">ev:&#32;T&#32;=:=&#32;</span><span style="color:#0000ff;">42</span><span style="">):&#32;T&#32;=&#32;x<br></span><span style="">check42:&#32;[T](x:&#32;T)(</span><span style="color:#000080;font-weight:bold;">implicit&#32;</span><span style="">ev:&#32;T&#32;=:=&#32;</span><span style="color:#0000ff;">42</span><span style="">)T<br></span><span style=""><br></span><span style="">scala&gt;&#32;</span><span style="color:#000080;font-weight:bold;">val&#32;</span><span style="">x1&#32;=&#32;check42(</span><span style="color:#0000ff;">42</span><span style="">)<br></span><span style="">^<br></span><span style="">error:&#32;Cannot&#32;prove&#32;that&#32;Int&#32;=:=&#32;</span><span style="color:#0000ff;">42.<br></span><span style="color:#0000ff;"><br></span><span style="">scala&gt;&#32;</span><span style="color:#000080;font-weight:bold;">def&#32;</span><span style="">singleCheck42[T&#32;&lt;:&#32;Singleton](x:&#32;T)(</span><span style="color:#000080;font-weight:bold;">implicit&#32;</span><span style="">ev:&#32;T&#32;=:=&#32;</span><span style="color:#0000ff;">42</span><span style="">):&#32;T&#32;=&#32;x<br></span><span style="">singleCheck42:&#32;[T&#32;&lt;:&#32;Singleton](x:&#32;T)(</span><span style="color:#000080;font-weight:bold;">implicit&#32;</span><span style="">ev:&#32;T&#32;=:=&#32;</span><span style="color:#0000ff;">42</span><span style="">)T<br></span><span style=""><br></span><span style="">scala&gt;&#32;</span><span style="color:#000080;font-weight:bold;">val&#32;</span><span style="">x2&#32;=&#32;singleCheck42(</span><span style="color:#0000ff;">42</span><span style="">)<br></span><span style="">x2:&#32;Int&#32;=&#32;</span><span style="color:#0000ff;">42</span></pre></div><p>See also <a href="https://docs.scala-lang.org/sips/42.type.html">SIP-23 about Literal-based Singleton Types</a>.</p></div></body></html>""".stripMargin)
  }

  def testSyntheticMethod_IsInstanceOf_InAny(): Unit = {
    configureScalaFromFileText(s"""val x: AnyRef = null ; x.${CARET}isInstanceOf[String]""")
    doGenerateDocAtCaretTest(s"""<html><head>$CssStyleSection<base href="jar:file:///${DependencyManagerBase.ivyHome}/cache/org.scala-lang/scala-library/srcs/scala-library-2.13.14-bin-ed3dfc9-SNAPSHOT-sources.jar!/scala/Any.scala"></head><body><div class='definition'><pre><a href="psi_element://scala.Any"><code>scala.Any</code></a>
                               |
                               |<span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">isInstanceOf</span>[<span style="color:#20999d;">T0</span>]: <span style=""><a href="psi_element://scala.Boolean"><code>Boolean</code></a></span></pre></div><div class='content'>Test whether the dynamic type of the receiver object has the same erasure as <tt>T0</tt>.<p>Depending on what <tt>T0</tt> is, the test is done in one of the below ways:</p><ul><li><tt>T0</tt> is a non-parameterized class type, e.g. <tt>BigDecimal</tt>: this method returns <tt>true</tt> if the value of the receiver object is a <tt>BigDecimal</tt> or a subtype of <tt>BigDecimal</tt>.</li><li><tt>T0</tt> is a parameterized class type, e.g. <tt>List[Int]</tt>: this method returns <tt>true</tt> if the value of the receiver object is some <tt>List[X]</tt> for any <tt>X</tt>. For example, <tt>List(1, 2, 3).isInstanceOf[List[String]]</tt> will return true.</li><li><tt>T0</tt> is some singleton type <tt>x.type</tt> or literal <tt>x</tt>: this method returns <tt>this.eq(x)</tt>. For example, <tt>x.isInstanceOf[1]</tt> is equivalent to <tt>x.eq(1)</tt></li><li><tt>T0</tt> is an intersection <tt>X with Y</tt> or <tt>X &amp; Y: this method is equivalent to </tt>x.isInstanceOf[X] && x.isInstanceOf[Y]<tt></tt></li><li><tt>T0</tt> is a union <tt>X | Y</tt>: this method is equivalent to <tt>x.isInstanceOf[X] || x.isInstanceOf[Y]</tt></li><li><tt>T0</tt> is a type parameter or an abstract type member: this method is equivalent to <tt>isInstanceOf[U]</tt> where <tt>U</tt> is <tt>T0</tt>'s upper bound, <tt>Any</tt> if <tt>T0</tt> is unbounded. For example, <tt>x.isInstanceOf[A]</tt> where <tt>A</tt> is an unbounded type parameter will return true for any value of <tt>x</tt>.</li></ul><p>This is exactly equivalent to the type pattern <tt>_: T0</tt></p></div><table class='sections'><tr><td valign='top' class='section'><p>Returns:</td><td valign='top'><tt>true</tt> if the receiver object is an instance of erasure of type <tt>T0</tt>; <tt>false</tt> otherwise.</td><tr><td valign='top' class='section'><p>Note:</td><td valign='top'>due to the unexpectedness of <tt>List(1, 2, 3).isInstanceOf[List[String]]</tt> returning true and <tt>x.isInstanceOf[A]</tt> where <tt>A</tt> is a type parameter or abstract member returning true, these forms issue a warning.</td></table></body></html>""".stripMargin)
  }

  def testSyntheticMethod_HashHash_InAny(): Unit = {
    configureScalaFromFileText(s"""val x: Any = 0 ; x.##$CARET""")
    doGenerateDocAtCaretTest(s"""<html><head>$CssStyleSection<base href="jar:file:///${DependencyManagerBase.ivyHome}/cache/org.scala-lang/scala-library/srcs/scala-library-2.13.14-bin-ed3dfc9-SNAPSHOT-sources.jar!/scala/Any.scala"></head><body><div class='definition'><pre><a href="psi_element://scala.Any"><code>scala.Any</code></a>
                               |
                               |<span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">def</span> <span style="color:#000000;">##</span>: <span style=""><a href="psi_element://scala.Int"><code>Int</code></a></span></pre></div><div class='content'>Equivalent to <tt>x.hashCode</tt> except for boxed numeric types and <tt>null</tt>. For numerics, it returns a hash value which is consistent with value equality: if two value type instances compare as true, then ## will produce the same hash value for each of them. For <tt>null</tt> returns a hashcode where <tt>null.hashCode</tt> throws a <tt>NullPointerException</tt>.</div><table class='sections'><tr><td valign='top' class='section'><p>Returns:</td><td valign='top'>a hash value consistent with ==</td></table></body></html>""".stripMargin)
  }

  //TODO: fix when SCL-22350 is fixed
  def testSyntheticMethod_HashHash_AnyKind(): Unit = {
    return
    configureScalaFromFileText(s"""def f[T <: ${CARET}AnyKind] = null""")
    doGenerateDocAtCaretTest(???)
  }

  //TODO: fix when SCL-22350 is fixed
  def testSyntheticMethod_HashHash_Matchable(): Unit = {
    return
    configureScala3FromFileText(s"""val matchable: ${CARET}Matchable = null""")
    doGenerateDocAtCaretTest(???)
  }
  //TODO: fix when SCL-22350 is fixed
  def testSyntheticMethod_HashHash_IntersectionType(): Unit = {
    return
    configureScala3FromFileText(s"""println(null : String $CARET& CharSequence)""")
    doGenerateDocAtCaretTest(???)
  }

  //TODO: fix when SCL-22350 is fixed
  def testSyntheticMethod_HashHash_UnionType(): Unit = {
    return
    configureScala3FromFileText(s"""println(null : String $CARET| CharSequence)""")
    doGenerateDocAtCaretTest(???)
  }
}
