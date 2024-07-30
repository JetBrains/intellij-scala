package org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.internal

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.IvyManagedLoader
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.ScalaInvalidPropertyKeyInspection

abstract class ScalaInvalidPropertyKeyInspectionTestBase extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ScalaInvalidPropertyKeyInspection]

  override protected def librariesLoaders =
    super.librariesLoaders :+ IvyManagedLoader("org.jetbrains" % "annotations" % "18.0.0")

  override def setUp(): Unit = {
    super.setUp()
    myFixture.addFileToProject("i18n.properties",
      """
        |com.example.existing=Welcome to our App!
        |com.example.1arg=Hello, {0}!
        |com.example.2args=Hello, {0}! How are you, {1}?
        |""".stripMargin)
  }

  override val description: String = ""
}

abstract class ScalaInvalidPropertyKeyInspectionTestBase_WrongUsage extends ScalaInvalidPropertyKeyInspectionTestBase {
  override protected def createTestText(text: String): String =
    s"""
       |import org.jetbrains.annotations.PropertyKey
       |import java.util.ResourceBundle
       |
       |object MyBundle {
       |  def message(@PropertyKey(resourceBundle = "i18n") key: String, params: Object*): String = ???
       |}
       |
       |object MyMainScala {
       |  $text
       |}
       |""".stripMargin
}

class ScalaInvalidPropertyKeyInspectionTest_MissingReference extends ScalaInvalidPropertyKeyInspectionTestBase_WrongUsage {
  override def descriptionMatches(s: String): Boolean = s.endsWith("doesn't appear to be a valid property key")

  def test_existing_key(): Unit = {
    checkTextHasNoErrors(raw"""MyBundle.message("com.example.existing")""")
  }

  def test_existing_key_infix(): Unit = {
    checkTextHasNoErrors(raw"""MyBundle message "com.example.existing"  """)
  }

  def test_missing_key(): Unit = {
    checkTextHasError(raw"""MyBundle.message($START"com.example.missing"$END)""")
  }

  def test_missing_key_infix(): Unit = {
    checkTextHasError(raw"""MyBundle message $START"com.example.missing"$END""")
  }

  def test_missing_key_with_args(): Unit = {
    checkTextHasError(raw"""MyBundle.message($START"com.example.missing"$END, "Alice")""")
  }

  def test_missing_key_infix_with_args(): Unit = {
    checkTextHasError(raw"""MyBundle message ($START"com.example.missing"$END, "Alice")""")
  }
}
