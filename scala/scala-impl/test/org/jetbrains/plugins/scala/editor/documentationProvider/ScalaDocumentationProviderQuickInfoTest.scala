package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.util.assertions.StringAssertions._

class ScalaDocumentationProviderQuickInfoTest extends ScalaDocumentationProviderTestBase {

  override protected def generateDoc(editor: Editor, file: PsiFile): String =
    generateQuickInfo(editor, file)

  protected def generateQuickInfo(editor: Editor, file: PsiFile): String = {
    val (referredElement, elementAtCaret) = extractReferredAndOriginalElements(editor, file)
    documentationProvider.getQuickNavigateInfo(referredElement, elementAtCaret)
  }

  private def moduleName: String = getModule.getName

  def testSimpleClass(): Unit =
    doGenerateDocTest(
      s"""class ${|}MyClass""",
      s"""[$moduleName] default
         |class MyClass""".stripMargin +
        """ extends <a href="psi_element://java.lang.Object"><code>Object</code></a>""".stripMargin
    )

  def testSimpleTrait(): Unit =
    doGenerateDocTest(
      s"""trait ${|}MyTrait""",
      s"""[$moduleName] default
         |trait MyTrait""".stripMargin
    )

  def testSimpleObject(): Unit =
    doGenerateDocTest(
      s"""object ${|}MyObject""",
      s"""[$moduleName] default
         |object MyObject""".stripMargin +
        """ extends <a href="psi_element://java.lang.Object"><code>Object</code></a>"""
    )

  def testClassWithModifiers(): Unit =
    doGenerateDocTest(
      s"""abstract sealed class ${|}MyClass""",
      s"""[$moduleName] default
         |abstract sealed class MyClass""".stripMargin +
        """ extends <a href="psi_element://java.lang.Object"><code>Object</code></a>"""
    )

  def testClassWithModifiers_1(): Unit =
    doGenerateDocTest(
      s"""final class ${|}MyClass""",
      s"""[$moduleName] default
         |final class MyClass""".stripMargin +
        """ extends <a href="psi_element://java.lang.Object"><code>Object</code></a>"""
    )

  def testClassWithGenericParameter(): Unit =
    doGenerateDocTest(
      s"""class ${|}Class[T]""",
      s"[$moduleName] default\n" +
        "class Class[T]" +
        " extends <a href=\"psi_element://java.lang.Object\"><code>Object</code></a>"
    )

  def testClassWithGenericParameter_WithBounds(): Unit =
    doGenerateDocTest(
      s"""trait Trait[A]
         |class ${|}Class[T <: Trait[_ >: Object]]
         |""".stripMargin,
      s"[$moduleName] default\n" +
        "class Class[T &lt;:" +
        " <a href=\"psi_element://Trait\"><code>Trait</code></a>[_ &gt;:" +
        " <a href=\"psi_element://java.lang.Object\"><code>Object</code></a>]]" +
        " extends <a href=\"psi_element://java.lang.Object\"><code>Object</code></a>"
    )

  def testClassWithGenericParameter_WithRecursiveBounds(): Unit =
    doGenerateDocTest(
      s"""trait Trait[T]
         |class ${|}Class2[T <: Trait[T]]
         |""".stripMargin,
      s"[$moduleName] default\n" +
        "class Class2[T &lt;:" +
        " <a href=\"psi_element://Trait\"><code>Trait</code></a>[T]]" +
        " extends <a href=\"psi_element://java.lang.Object\"><code>Object</code></a>"
    )

  def testClassWithGenericParameter_WithRecursiveBounds_1(): Unit =
    doGenerateDocTest(
      s"""trait Trait[T]
         |class ${|}Class4[T <: Trait[_ >: Trait[T]]]
         |""".stripMargin,
      s"[$moduleName] default\n" +
        "class Class4[T &lt;:" +
        " <a href=\"psi_element://Trait\"><code>Trait</code></a>[_ &gt;:" +
        " <a href=\"psi_element://Trait\"><code>Trait</code></a>[T]]]" +
        " extends <a href=\"psi_element://java.lang.Object\"><code>Object</code></a>"
    )

  def testClassWithSuperWithGenerics(): Unit =
    doGenerateDocTest(
      s"""trait Trait[A]
         |abstract class ${|}Class extends Comparable[_ <: Trait[_ >: String]]
         |""".stripMargin,
      s"[$moduleName] default\n" +
        "abstract class Class" +
        " extends <a href=\"psi_element://java.lang.Comparable\"><code>Comparable</code></a>[_ &lt;:" +
        " <a href=\"psi_element://Trait\"><code>Trait</code></a>[_ &gt;:" +
        " <a href=\"psi_element://scala.Predef.String\"><code>String</code></a>]]"
    )

  def testClassExtendsListShouldNotContainWithObject(): Unit = {
    myFixture.addFileToProject("commons.scala",
      """class BaseClass
        |trait BaseTrait1
        |trait BaseTrait2
        |""".stripMargin
    )
    val classesWithoutObject = Seq(
      s"class ${|}MyClass2 extends BaseClass",
      s"class ${|}MyClass4 extends BaseTrait1",
      s"class ${|}MyClass3 extends BaseClass with BaseTrait1",
      s"class ${|}MyTrait1",
      s"class ${|}MyTrait2 extends BaseTrait1",
      s"class ${|}MyTrait3 extends BaseTrait1 with BaseTrait2"
    )
    // testing exact quick info value would be very noisy, it's enough to test just presence of ` with Object` which can be escaped!
    val withObjectRegex      = "(\\s|\\n)with .*Object".r
    classesWithoutObject.foreach { content =>
      val quickInfo = generateDoc(content)
      assertStringNotMatches(quickInfo, withObjectRegex)
    }
  }

  def testClassExtendsListShouldContainObjectIfThereAreNoBaseClasses(): Unit = {
    myFixture.addFileToProject("commons.scala",
      """class BaseClass
        |trait BaseTrait1
        |trait BaseTrait2
        |""".stripMargin
    )

    val classesWithObject = Seq(
      s"class ${|}MyClass1"
    )

    val extendsObjectRegex = "(\\s|\\n)extends .*Object".r
    classesWithObject.foreach { content =>
      val quickInfo = generateDoc(content)
      assertStringMatches(quickInfo, extendsObjectRegex)
    }
  }

  def testValueDefinition(): Unit =
    doGenerateDocTest(
      s"""class Wrapper {
         |  val (field1, ${|}field2) = (42, "hello")
         |}""".stripMargin,
      """<a href="psi_element://Wrapper"><code>Wrapper</code></a> <default>
        |val field2: <a href="psi_element://java.lang.String"><code>String</code></a> = (42, "hello")""".stripMargin
    )

  def testValueDeclaration(): Unit =
    doGenerateDocTest(
      s"""abstract class Wrapper {
         |  val ${|}field2: String
         |}""".stripMargin,
      """<a href="psi_element://Wrapper"><code>Wrapper</code></a> <default>
        |val field2: <a href="psi_element://scala.Predef.String"><code>String</code></a>""".stripMargin
    )

  def testVariableDefinition(): Unit =
    doGenerateDocTest(
      s"""class Wrapper {
         |  var (field1, ${|}field2) = (42, "hello")
         |}""".stripMargin,
      """<a href="psi_element://Wrapper"><code>Wrapper</code></a> <default>
        |var field2: <a href="psi_element://java.lang.String"><code>String</code></a> = (42, "hello")""".stripMargin
    )

  def testVariableDeclaration(): Unit =
    doGenerateDocTest(
      s"""abstract class Wrapper {
         |  var ${|}field2: String
         |}""".stripMargin,
      """<a href="psi_element://Wrapper"><code>Wrapper</code></a> <default>
        |var field2: <a href="psi_element://scala.Predef.String"><code>String</code></a>""".stripMargin
    )

  def testValueWithModifiers(): Unit =
    doGenerateDocTest(
      s"""class Wrapper {
         |  protected final lazy val ${|}field2 = "hello"
         |}""".stripMargin,
      """<a href="psi_element://Wrapper"><code>Wrapper</code></a> <default>
        |protected final lazy val field2: <a href="psi_element://java.lang.String"><code>String</code></a> = "hello"""".stripMargin
    )

  def testHigherKindedTypeParameters(): Unit =
    doGenerateDocTest(
      s"""object O {
         |  def ${|}f[A[_, B]] = 42
         |}""".stripMargin,
      """<a href="psi_element://O"><code>O</code></a> <default>
        |def f[A[_, B]]: <a href="psi_element://scala.Int"><code>Int</code></a>""".stripMargin
    )

  def testHigherKindedTypeParameters_1(): Unit =
    doGenerateDocTest(
      s"""trait ${|}T[X[_, Y[_, Z]]]
         |""".stripMargin,
      """[light_idea_test_case] default
        |trait T[X[_, Y[_, Z]]]""".stripMargin
    )

  def testMethod(): Unit =
    doGenerateDocTest(
      s"""class X {
         | def ${|}f1 = 42
         |}
         |""".stripMargin,
      """<a href="psi_element://X"><code>X</code></a> <default>
        |def f1: <a href="psi_element://scala.Int"><code>Int</code></a>""".stripMargin
    )

  def testMethodWithAccessModifier(): Unit =
    doGenerateDocTest(
      s"""class X {
         |  protected def ${|}f1 = 42
         |}
         |""".stripMargin,
      """<a href="psi_element://X"><code>X</code></a> <default>
        |protected def f1: <a href="psi_element://scala.Int"><code>Int</code></a>""".stripMargin
    )

  def testMethodWithAccessModifierWithThisQualifier(): Unit =
    doGenerateDocTest(
      s"""class X {
         |  protected[this] def ${|}f1 = 42
         |}
         |""".stripMargin,
      """<a href="psi_element://X"><code>X</code></a> <default>
        |protected def f1: <a href="psi_element://scala.Int"><code>Int</code></a>""".stripMargin
    )

  def testTypeWithColon(): Unit =
    doGenerateDocTest(
      s"""trait MyTrait[T]
         |class :::[T1, T2]
         |class ${|}ClassWithGenericColons1[A <: MyTrait[:::[Int, String]]]
         |  extends MyTrait[Int ::: String]
         |""".stripMargin,
      "[light_idea_test_case] default\n" +
        "class ClassWithGenericColons1[A &lt;: " +
        "<a href=\"psi_element://MyTrait\"><code>MyTrait</code></a>" +
        "[<a href=\"psi_element://scala.Int\"><code>Int</code></a>" +
        " <a href=\"psi_element://:::\"><code>:::</code></a> " +
        "<a href=\"psi_element://scala.Predef.String\"><code>String</code></a>]]" +
        " extends " +
        "<a href=\"psi_element://MyTrait\"><code>MyTrait</code></a>" +
        "[<a href=\"psi_element://scala.Int\"><code>Int</code></a>" +
        " <a href=\"psi_element://:::\"><code>:::</code></a> " +
        "<a href=\"psi_element://scala.Predef.String\"><code>String</code></a>]"
    )
}