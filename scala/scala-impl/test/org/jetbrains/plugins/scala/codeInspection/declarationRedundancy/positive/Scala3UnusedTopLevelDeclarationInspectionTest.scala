package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.positive

import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.Scala3UnusedDeclarationInspectionTestBase

/**
 * Positive counterpart to [[org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.negative.Scala3UsedTopLevelDeclarationInspectionTest]]
 * for SCL-25515: top-level `private` declarations and cases of `private[pkg]` enums
 * must still be reported as unused when they really are unused — including when an
 * unrelated declaration with the same name lives next to them in the same package.
 */
class Scala3UnusedTopLevelDeclarationInspectionTest extends Scala3UnusedDeclarationInspectionTestBase {

  def test_top_level_private_def_unused(): Unit = {
    checkTextHasError(
      s"""package reproduction
         |
         |private def ${START}sharedHelperMethod$END(): Unit = ()
         |""".stripMargin
    )
  }

  def test_top_level_private_val_unused(): Unit = {
    checkTextHasError(
      s"""package reproduction
         |
         |private val ${START}sharedHelperValue$END: Int = 42
         |""".stripMargin
    )
  }

  /** Enum reported as unused — its sole case is unused too, so both highlights are expected. */
  def test_package_private_top_level_enum_with_unused_case(): Unit = {
    checkTextHasError(
      s"""package reproduction
         |
         |private[reproduction] enum ${START}ExampleCategory$END {
         |  case ${START}SelectedCategory$END
         |}
         |""".stripMargin
    )
  }

  /** The enum itself is used as a return type from another file, but its case is unused. */
  def test_package_private_top_level_enum_case_unused_while_enum_used(): Unit = {
    myFixture.addFileToProject(
      "reproduction/CategoryConsumer.scala",
      """package reproduction
        |
        |object CategoryConsumer {
        |  def select(): ExampleCategory = null
        |}
        |""".stripMargin
    )
    checkTextHasError(
      s"""package reproduction
         |
         |private[reproduction] enum ExampleCategory {
         |  case ${START}SelectedCategory$END
         |}
         |""".stripMargin
    )
  }

  /**
   * A method with the same simple name as the top-level private def lives in another file
   * in the same package, but is only declared there (never invoked). The top-level def is
   * still unused and must be reported.
   */
  def test_top_level_private_def_unused_when_same_name_only_declared_elsewhere(): Unit = {
    myFixture.addFileToProject(
      "reproduction/Other.scala",
      """package reproduction
        |
        |object Other {
        |  def sharedHelperMethod(): Unit = ()
        |}
        |""".stripMargin
    )
    checkTextHasError(
      s"""package reproduction
         |
         |private def ${START}sharedHelperMethod$END(): Unit = ()
         |""".stripMargin
    )
  }

  /**
   * Same-name identifier exists in another file in a *different* package: it resolves to
   * something unrelated and is outside the top-level private's PackageScope, so the
   * declaration is still unused.
   */
  def test_top_level_private_val_unused_when_same_name_resolves_in_other_package(): Unit = {
    myFixture.addFileToProject(
      "elsewhere/Other.scala",
      """package elsewhere
        |
        |object Other {
        |  val sharedHelperValue: Int = 7
        |  def consume(): Int = sharedHelperValue
        |}
        |""".stripMargin
    )
    checkTextHasError(
      s"""package reproduction
         |
         |private val ${START}sharedHelperValue$END: Int = 42
         |""".stripMargin
    )
  }

  /**
   * Same-name reference exists in another file in the *same* package, but resolves to a
   * locally-scoped member of an unrelated object (not the top-level private). The top-level
   * declaration is therefore unused.
   *
   * Because [[org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.TextSearch]]
   * is intentionally cheap and does not perform real reference resolution, any same-named
   * reference in the package counts as a usage — so the inspection misses this case. The
   * test guards against accidentally tightening that behaviour: if a future improvement adds
   * resolution, switch to [[checkTextHasError]] and remove the `checkTextHasNoErrors` call.
   */
  def test_top_level_private_def_used_only_via_unrelated_same_name_call_is_a_false_negative(): Unit = {
    myFixture.addFileToProject(
      "reproduction/Other.scala",
      """package reproduction
        |
        |object Other {
        |  private def sharedHelperMethod(): Unit = ()
        |  def consume(): Unit = sharedHelperMethod()
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
}
