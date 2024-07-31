package org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.internal

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.util.registry.Registry
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
    Registry.get("ast.loading.filter").setValue(false, getTestRootDisposable)
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
       |  def message2(@PropertyKey(resourceBundle = "i18n") key: String, aFirstParam: Object, params: Object*): String = ???
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

  def test_unrelated_call(): Unit =
    checkTextHasNoErrors(
      raw"""
           |def fun(s: String): Unit = ()
           |
           |fun("com.example.missing")
           |""".stripMargin
    )
}


class ScalaInvalidPropertyKeyInspectionTest_InvalidBundleReference extends ScalaInvalidPropertyKeyInspectionTestBase {
  override def descriptionMatches(s: String): Boolean = s.startsWith("Invalid resource bundle reference")

  def test_direct_existing(): Unit = checkTextHasNoErrors(
    """
      |import org.jetbrains.annotations.PropertyKey
      |
      |object MyBundle {
      |  def message(@PropertyKey(resourceBundle = "i18n") key: String, params: Object*): String = ???
      |}
      |""".stripMargin
  )

  def test_direct_missing(): Unit = checkTextHasError(
    s"""
       |import org.jetbrains.annotations.PropertyKey
       |
       |object MyBundle {
       |  def message(@PropertyKey(resourceBundle = $START"i18n-missing"$END) key: String, params: Object*): String = ???
       |}
       |""".stripMargin
  )

  def test_indirect_existing(): Unit = checkTextHasNoErrors(
    """
      |import org.jetbrains.annotations.PropertyKey
      |
      |object MyBundle {
      |  private val BUNDLE = "i18n"
      |  def message(@PropertyKey(resourceBundle = BUNDLE) key: String, params: Object*): String = ???
      |}
      |""".stripMargin
  )

  def test_indirect_missing(): Unit = checkTextHasError(
    s"""
       |import org.jetbrains.annotations.PropertyKey
       |
       |object MyBundle {
       |  private val BUNDLE = "i18n-missing"
       |  def message(@PropertyKey(resourceBundle = ${START}BUNDLE$END) key: String, params: Object*): String = ???
       |}
       |""".stripMargin
  )
}

class ScalaInvalidPropertyKeyInspectionTest_InvalidArgCount extends ScalaInvalidPropertyKeyInspectionTestBase_WrongUsage {
  override def descriptionMatches(s: String): Boolean = s.startsWith("Property '") && s.contains("expected")

  def test_exact_args(): Unit = checkTextHasNoErrors(
    """
      |MyBundle.message("com.example.1arg", "Alice")
      |""".stripMargin
  )

  def test_too_few_args(): Unit = checkTextHasError(
    s"""
       |MyBundle.message("com.example.1arg$START")$END
       |""".stripMargin
  )

  def test_too_few_args_infix(): Unit = checkTextHasError(
    s"""
       |MyBundle message "com.example.1arg$START"$END
       |""".stripMargin
  )

  def test_too_few_args_infix2(): Unit = checkTextHasError(
    s"""
       |MyBundle message ("com.example.2args", "blub$START")$END
       |""".stripMargin
  )

  def test_too_many_args(): Unit = checkTextHasError(
    s"""
       |MyBundle.message("com.example.1arg", "Alice", $START"Bob"$END)
       |""".stripMargin
  )

  def test_too_many_args_infix(): Unit = checkTextHasError(
    s"""
       |MyBundle message ("com.example.1arg", "Alice", $START"Bob"$END)
       |""".stripMargin
  )

  def test_applicability_issue(): Unit = checkTextHasNoErrors(
    """
      |MyBundle.message2("com.example.2args")
      |""".stripMargin
  )

  def test_varargs_are_not_reported(): Unit = checkTextHasNoErrors(
    """
      |val blub = Seq.empty[String]
      |MyBundle.message("com.example.2args", blub: _*)
      |""".stripMargin
  )
}
