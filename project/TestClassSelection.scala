/**
 * Selects discovered test classes by name for the shared test commands.
 * All leaves class names unrestricted; Matching selects classes using one sbt testOnly glob.
 * Both cases still compose with category/tag rules, bucketing and existing user class filters.
 *
 * Callers supply ordinary patterns without leading whitespace. JUnitTestCommands formats
 * the selection as sbt input immediately before execution, keeping parser details out of
 * command definitions. Use All instead of an empty-string sentinel.
 */
sealed trait TestClassSelection

object TestClassSelection {
  /**
   * Adds no restriction on test class names. Other selection rules, such as categories,
   * tags, buckets and user class filters, still apply.
   */
  case object All extends TestClassSelection

  /**
   * Selects test classes whose fully qualified names match one sbt testOnly glob.
   * The pattern must be nonempty and contain no whitespace, including leading or trailing
   * whitespace. Multiple selectors and framework arguments are outside this API's scope.
   * Patterns are not trimmed or otherwise rewritten; sbt handles their glob syntax.
   * Constructor checks also apply when copying a Matching value.
   *
   * @param glob one class-name pattern without sbt input formatting
   *             Examples:
   *             {{{
   *             Matching("com.intellij.*")                  // Classes under this package.
   *             Matching("com.intellij.scala.PluginXmlTest") // One fully qualified class.
   *             Matching("*")                               // Every class name.
   *             }}}
   * @throws IllegalArgumentException if the pattern is empty or contains whitespace
   */
  final case class Matching(glob: String) extends TestClassSelection {
    require(glob.nonEmpty, "Test-class glob must not be empty; use TestClassSelection.All instead.")
    require(!glob.exists(_.isWhitespace), "Test-class glob must be a single pattern without whitespace.")
  }
}
