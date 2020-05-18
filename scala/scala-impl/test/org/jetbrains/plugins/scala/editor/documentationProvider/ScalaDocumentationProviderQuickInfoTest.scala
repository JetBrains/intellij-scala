package org.jetbrains.plugins.scala.editor.documentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.util.assertions.StringAssertions._

/*
 * - quick info: escape < & > for type parameters in the beginning, middle, end, SCL-7725
 */
class ScalaDocumentationProviderQuickInfoTest extends ScalaDocumentationProviderTestBase {

  override protected def generateDoc(editor: Editor, file: PsiFile): String =
    generateQuickInfo(editor, file)

  protected def generateQuickInfo(editor: Editor, file: PsiFile): String = {
    val (referredElement, elementAtCaret) = extractReferredAndOriginalElements(editor, file)
    documentationProvider.getQuickNavigateInfo(referredElement, elementAtCaret)
  }

  def testEscapeGenericsBounds(): Unit =
    doGenerateDocTest(
      s"""trait Trait[A]
         |abstract class ${|}Class[T <: Trait[_ >: Object]]
         |  extends Comparable[_ <: Trait[_ >: String]]""".stripMargin,
      s"""[${getModule.getName}] default
         |abstract class <a href="psi_element://Class"><code>Class</code></a>[T &lt;:
         | <a href="psi_element://Trait"><code>Trait</code></a>[_ &gt;:
         | <a href="psi_element://java.lang.Object"><code>Object</code></a>]]
         | extends <a href="psi_element://java.lang.Comparable"><code>Comparable</code></a>[_ &lt;:
         | <a href="psi_element://Trait"><code>Trait</code></a>[_ &gt;:
         | <a href="psi_element://scala.Predef.String"><code>String</code></a>]]""".stripMargin
    )

  def testExtendsListDoesntContainWithObject(): Unit = {
    myFixture.addFileToProject("commons.scala",
      """class BaseClass
        |trait BaseTrait""".stripMargin
    )
    val classesWithoutObject = Seq(
      s"class ${|}MyClass2 extends BaseClass",
      s"class ${|}MyClass4 extends BaseTrait",
      s"class ${|}MyClass3 extends BaseClass with BaseTrait",
      s"class ${|}MyTrait1",
      s"class ${|}MyTrait2 extends BaseTrait"
    )
    // testing exact quick info value would be very noisy, it's enough to test just presence of ` with Object` which can be escaped!
    val withObjectRegex = "(\\s|\\n)with .*Object".r
    classesWithoutObject.foreach { content =>
      val quickInfo = generateDoc(content)
      assertStringNotMatches(quickInfo, withObjectRegex)
    }
  }

  def testExtendsListShouldContainObjectIfThereAreNoBaseClasses(): Unit = {
    myFixture.addFileToProject("commons.scala",
      """class BaseClass
        |trait BaseTrait""".stripMargin
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
}