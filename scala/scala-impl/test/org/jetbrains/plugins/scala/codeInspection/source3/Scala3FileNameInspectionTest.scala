package org.jetbrains.plugins.scala.codeInspection.source3

import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenameProcessor
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.codeInspection.{ScalaFileNameInspection, ScalaInspectionBundle, ScalaInspectionTestBase}

class Scala3FileNameInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection = classOf[ScalaFileNameInspection]
  override protected val description = ScalaInspectionBundle.message("fileName.does.not.match")

  override protected def onFileCreated(file: PsiFile): Unit =
    new RenameProcessor(getProject, file, "Foo.scala", false, false).run()

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_0

  def test_one_toplevel_class_same_name(): Unit =
    checkTextHasNoErrors(
      """
        |class Foo {}
        |""".stripMargin
    )

  def test_one_toplevel_class_different_names(): Unit =
    checkTextHasError(
      s"""
        |class ${START}Bar${END} {}
        |""".stripMargin
    )

  def test_one_toplevel_object_same_name(): Unit =
    checkTextHasNoErrors(
      s"""
         |object ${START}Foo${END} {}
         |""".stripMargin
    )

  def test_one_toplevel_object_different_names(): Unit =
    checkTextHasError(
      s"""
         |object ${START}Bar${END} {}
         |""".stripMargin
    )

  def test_one_toplevel_trait_same_name(): Unit =
    checkTextHasNoErrors(
      s"""
         |trait ${START}Foo${END} {}
         |""".stripMargin
    )

  def test_one_toplevel_trait_different_names(): Unit =
    checkTextHasError(
      s"""
         |trait ${START}Bar${END} {}
         |""".stripMargin
    )

  def test_one_toplevel_enum_same_names(): Unit =
    checkTextHasNoErrors(
      s"""
         |enum ${START}Foo${END} {}
         |""".stripMargin
    )

  def test_one_toplevel_enum_different_names(): Unit =
    checkTextHasError(
      s"""
         |enum ${START}Bar${END} {}
         |""".stripMargin
    )

  def test_one_toplevel_abstract_type_same_name(): Unit =
    checkTextHasNoErrors(
      s"""
         |type ${START}Foo${END}
         |""".stripMargin
    )

  def test_one_toplevel_abstract_type_different_names(): Unit =
    checkTextHasError(
      s"""
         |type ${START}Bar${END}
         |""".stripMargin
    )

  def test_one_toplevel_opaque_type_same_name(): Unit =
    checkTextHasNoErrors(
      s"""
         |opaque type ${START}Foo${END} = Int
         |""".stripMargin
    )

  def test_one_toplevel_opaque_type_different_names(): Unit =
    checkTextHasError(
      s"""
         |opaque type ${START}Bar${END} = Int
         |""".stripMargin
    )

  def test_one_toplevel_type_alias_different_names(): Unit =
    checkTextHasNoErrors(
      s"""
         |type ${START}Foo${END} = Int
         |""".stripMargin
    )

  def test_two_toplevel_elements_different_names(): Unit =
    checkTextHasNoErrors(
      """
        |val a = 1
        |
        |class Bar {}
        |""".stripMargin
    )

  def test_imports_are_ignored(): Unit =
    checkTextHasError(
      s"""
         |import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
         |
         |class ${START}Bar${END} {}
         |""".stripMargin
    )

  def test_nontoplevel_elements_are_ignored(): Unit =
    checkTextHasError(
      s"""
         |class ${START}Bar${END} {
         |  val a = 1
         |}
         |""".stripMargin
    )

  def test_multiple_typedefs_one_matching(): Unit =
    checkTextHasNoErrors(
      s"""
         |object Foo
         |class Bar
         |""".stripMargin
    )

  def test_multiple_typedefs_all_matching(): Unit =
    checkTextHasNoErrors(
      s"""
         |class Foo
         |object Foo
         |""".stripMargin
    )

  def test_multiple_typedefs_none_matching(): Unit =
    checkTextHasNoErrors(
      s"""
         |class Bar
         |object Boo
         |""".stripMargin
    )

  def test_single_val(): Unit =
    checkTextHasNoErrors(
      s"""
         |val a = 1
         |""".stripMargin
    )

  def test_named_given_alias_wrong_but_its_ok(): Unit =
    checkTextHasNoErrors(
      s"""
         |given Bar: Any = 1
         |""".stripMargin
    )

  def test_named_given_alias_ok(): Unit =
    checkTextHasNoErrors(
      s"""
         |given Foo: Any = 1
         |""".stripMargin
    )

  def test_unnamed_given_alias_ok(): Unit =
    checkTextHasNoErrors(
      s"""
         |given Any = 1
         |""".stripMargin
    )

  def test_unnamed_given_alias_also_ok(): Unit =
    checkTextHasNoErrors(
      s"""
         |given Any = 1
         |""".stripMargin
    )

  def test_named_given_def_wrong(): Unit =
    checkTextHasError(
      s"""
         |given ${START}Bar$END: Any: Int {
         |  def foo = 3
         |}
         |""".stripMargin
    )

  def test_named_given_def_ok(): Unit =
    checkTextHasNoErrors(
      s"""
         |given Foo: Any: Int = {
         |  def foo = 3
         |}
         |""".stripMargin
    )

  def test_unnamed_given_def_ok(): Unit =
    checkTextHasNoErrors(
      s"""
         |given Any {
         |  def foo = 3
         |}
         |""".stripMargin
    )

  def test_unnamed_given_def_also_ok(): Unit =
    checkTextHasNoErrors(
      s"""
         |given Foo {
         |  def foo = 3
         |}
         |""".stripMargin
    )
}
