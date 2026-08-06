package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.negative

import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.Scala3UnusedDeclarationInspectionTestBase

/**
 * Regression tests for SCL-25515:
 * "[unused-declaration] False positives for package-private top-level declarations and enum cases".
 *
 * Top-level `private` declarations in Scala 3 are visible in the entire enclosing package,
 * not just the defining file. Likewise, `private[pkg]` types make their members visible
 * within `pkg`. Usages from other files in the same package must not be reported as unused.
 */
class Scala3UsedTopLevelDeclarationInspectionTest extends Scala3UnusedDeclarationInspectionTestBase {

  def test_top_level_private_def_used_from_other_file(): Unit = {
    myFixture.addFileToProject(
      "reproduction/HelperMethodConsumer.scala",
      """package reproduction
        |
        |object HelperMethodConsumer {
        |  def invokeSharedHelper(): Unit =
        |    sharedHelperMethod()
        |}
        |""".stripMargin
    )
    checkTextHasNoErrors(
      """package reproduction
        |
        |private def sharedHelperMethod(): Unit = ()
        |""".stripMargin
    )
  }

  def test_top_level_private_val_used_from_other_file(): Unit = {
    myFixture.addFileToProject(
      "reproduction/HelperValueConsumer.scala",
      """package reproduction
        |
        |object HelperValueConsumer {
        |  def consume(): Int = sharedHelperValue
        |}
        |""".stripMargin
    )
    checkTextHasNoErrors(
      """package reproduction
        |
        |private val sharedHelperValue: Int = 42
        |""".stripMargin
    )
  }

  def test_package_private_top_level_enum_case_used_from_other_file(): Unit = {
    myFixture.addFileToProject(
      "reproduction/CategoryConsumer.scala",
      """package reproduction
        |
        |object CategoryConsumer {
        |  def selectedCategory(): ExampleCategory =
        |    ExampleCategory.SelectedCategory
        |}
        |""".stripMargin
    )
    checkTextHasNoErrors(
      """package reproduction
        |
        |private[reproduction] enum ExampleCategory {
        |  case SelectedCategory
        |}
        |""".stripMargin
    )
  }

  def test_top_level_private_enum_case_used_from_other_file(): Unit = {
    myFixture.addFileToProject(
      "reproduction/CategoryConsumer.scala",
      """package reproduction
        |
        |object CategoryConsumer {
        |  def selectedCategory(): ExampleCategory =
        |    ExampleCategory.SelectedCategory
        |}
        |""".stripMargin
    )
    checkTextHasNoErrors(
      """package reproduction
        |
        |private enum ExampleCategory {
        |  case SelectedCategory
        |}
        |""".stripMargin
    )
  }

  // Control: same shape but public; ensures the test setup is meaningful.
  def test_public_top_level_def_used_from_other_file(): Unit = {
    myFixture.addFileToProject(
      "reproduction/HelperMethodConsumer.scala",
      """package reproduction
        |
        |object HelperMethodConsumer {
        |  def invokeSharedHelper(): Unit =
        |    sharedHelperMethod()
        |}
        |""".stripMargin
    )
    checkTextHasNoErrors(
      """package reproduction
        |
        |def sharedHelperMethod(): Unit = ()
        |""".stripMargin
    )
  }
}
