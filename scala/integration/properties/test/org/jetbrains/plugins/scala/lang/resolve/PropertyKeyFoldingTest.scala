package org.jetbrains.plugins.scala
package lang.resolve

import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.IvyManagedLoader
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class PropertyKeyFoldingTest extends ScalaLightCodeInsightFixtureTestCase {

  private def assertFolded(editor: Editor, foldedText: String, placeholder: String): Unit = {
    val foldingRegions = editor.getFoldingModel.getAllFoldRegions
    val matchingRegion = foldingRegions.find { it =>
      it.getPlaceholderText == placeholder && it.getDocument.getText.substring(it.getStartOffset, it.getEndOffset) == foldedText
    }.orNull

    if (matchingRegion == null) {
      val documentText = editor.getDocument.getText
      val existingFoldingsString = foldingRegions.map { r =>
        val folded = documentText.substring(r.getStartOffset, r.getEndOffset)
        val placeholder = r.getPlaceholderText
        s"'$folded' -> '$placeholder'"
      }.mkString(", ")

      throw new AssertionError(s"no folding '$foldedText' -> '$placeholder' found in $existingFoldingsString")
    }
  }

  override protected def librariesLoaders =
    super.librariesLoaders :+ IvyManagedLoader("org.jetbrains" % "annotations" % "18.0.0")

  def testSingleProperty(): Unit = {
    myFixture.addFileToProject("i18n.properties", "com.example.localization.welcomeMessage=Welcome to our App!")
    myFixture.addFileToProject("MyClass.scala",
      """
        |import org.jetbrains.annotations.PropertyKey;
        |import java.util.ResourceBundle;
        |
        |object MyClass {
        |  private val BUNDLE_NAME = "i18n"
        |  private val BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);
        |
        |  def main(args: Array[String]): Unit = {
        |    print(message("com.example.localization.welcomeMessage"))
        |    print(MyClass.message("com.example.localization.welcomeMessage"))
        |  }
        |
        |  def message(@PropertyKey(resourceBundle = BUNDLE_NAME) key: String) {
        |    BUNDLE.getString(key);
        |  }
        |}""".stripMargin)


    myFixture.testHighlighting("MyClass.scala")

    assertFolded(myFixture.getEditor,
      "message(\"com.example.localization.welcomeMessage\")",
      "\"Welcome to our App!\""
    )
    assertFolded(myFixture.getEditor,
      "MyClass.message(\"com.example.localization.welcomeMessage\")",
      "\"Welcome to our App!\""
    )
  }


  def testPropertyWithParameters(): Unit = {
    myFixture.addFileToProject("i18n.properties", "com.example.localization.welcomeMessage=Welcome {0} to our App!")
    myFixture.addFileToProject("MyClass.scala",
      """
        |import org.jetbrains.annotations.PropertyKey;
        |import java.util.ResourceBundle;
        |
        |object MyClass {
        |  private val BUNDLE_NAME = "i18n"
        |  private val BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);
        |
        |  def main(args: Array[String]): Unit = {
        |    print(message("com.example.localization.welcomeMessage", "My Friend"));
        |    val param = args(0);
        |    print(MyClass.message("com.example.localization.welcomeMessage", param));
        |  }
        |
        |  def message(@PropertyKey(resourceBundle = BUNDLE_NAME) key: String, params: Object*) {
        |    BUNDLE.getString(key);
        |  }
        |}
        |""".stripMargin)

    myFixture.testHighlighting("MyClass.scala")

    assertFolded(myFixture.getEditor,
      "message(\"com.example.localization.welcomeMessage\", \"My Friend\")",
      "\"Welcome My Friend to our App!\""
    )
    assertFolded(myFixture.getEditor,
      "MyClass.message(\"com.example.localization.welcomeMessage\", param)",
      "\"Welcome {param} to our App!\""
    )
  }

}