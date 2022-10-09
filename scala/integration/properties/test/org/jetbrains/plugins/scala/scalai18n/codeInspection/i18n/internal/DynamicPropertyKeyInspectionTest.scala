package org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.internal

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}

class DynamicPropertyKeyInspectionTest extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[DynamicPropertyKeyInspection]

  override protected val description =
    ScalaI18nBundle.message("internal.only.pass.hardcoded.strings.as.property.keys")

  override protected def librariesLoaders: Seq[LibraryLoader] = super.librariesLoaders ++ Seq(
    IvyManagedLoader("org.jetbrains" % "annotations" % "22.0.0")
  )

  override protected def createTestText(text: String): String =
    s"""import org.jetbrains.annotations.PropertyKey
       |
       |object MyBundle {
       |  private final val Bundle= "MyBundle"
       |
       |  def message(@PropertyKey(resourceBundle = BUNDLE) key: String, params: Any*): String = ???
       |}
       |
       |//noinspection ScalaUnresolvedPropertyKey
       |class MyMainScala {
       |  $text
       |}
       |""".stripMargin


  def test_no_errors_for_simple_string_literal(): Unit = {
    checkTextHasNoErrors(raw"""MyBundle.message("my.key1")""")
  }

  def test_has_errors_for_interpolated_string_literal(): Unit = {
    checkTextHasError(raw"""MyBundle.message(${START}s"my.interpolated.key1"$END)""")
  }

  def test_has_errors_non_string_literal(): Unit = {
    checkTextHasError(
      raw"""val key: String =
           |  if (???) "property.key.1"
           |  else "property.key.2"
           |MyBundle.message(${START}key$END)
           |""".stripMargin
    )
  }

  def test_no_errors_for_value_definition(): Unit = {
    checkTextHasNoErrors(
      raw"""
           |@PropertyKey(resourceBundle = "MyBundle")
           |val value: String = "invalid.key"
           |""".stripMargin
    )
  }
}
