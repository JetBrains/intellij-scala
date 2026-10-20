import com.github.sbt.junit.jupiter.sbt.Import.jupiterTestFramework
import sbt.*
import sbt.Keys.*

/**
 * JUnit 4 and Jupiter options and temporary test execution for Community and Ultimate commands.
 */
object JUnitTestCommands {
  /**
   * Configures progress output, readable names and failure diagnostics for the JUnit 4
   * and Jupiter sbt adapters. Category/tag selection is supplied separately by categoryOptions.
   */
  def frameworkOptions: Seq[TestOption] = {
    val common = Seq(
      "-v", // Log test starts to show progress while tests are running.
      "-s", // Decode Scala-encoded names to keep test output readable.
      "-a", // Include assertion failure stack traces for diagnostics.
      "+c", // Include exception class names in failure messages.
      "+q"  // Disable quiet mode so passing tests' output stays visible.
    )

    Seq(
      Tests.Argument(TestFrameworks.JUnit, common: _*),
      Tests.Argument(jupiterTestFramework, common: _*)
    )
  }

  /**
   * Applies the same inclusion/exclusion lists as JUnit 4 categories and Jupiter tags.
   * Empty lists omit the corresponding argument. These selection options compose with
   * frameworkOptions, sbt class filters and testOnly name selectors without changing
   * the adapters' output or diagnostic settings.
   */
  def categoryOptions(includeCategories: Seq[String], excludeCategories: Seq[String]): Seq[TestOption] = {
    def args(keyword: String): Seq[String] = {
      def handle(verb: String, categories: Seq[String]): Seq[String] =
        if (categories.isEmpty) Seq.empty
        else Seq(categories.mkString(start = s"--$verb-$keyword=", sep = ",", end = ""))

      val includeArgs = handle(verb = "include", categories = includeCategories)
      val excludeArgs = handle(verb = "exclude", categories = excludeCategories)
      includeArgs ++ excludeArgs
    }

    Seq(
      Tests.Argument(TestFrameworks.JUnit, args(keyword = "categories"): _*),
      Tests.Argument(jupiterTestFramework, args(keyword = "tags"): _*)
    )
  }

  /**
   * Runs the current project's testOnly with command-specific settings and the supplied glob.
   * appendWithSession preserves user session settings; returning the incoming state after
   * execution keeps the appended options from affecting later commands. Failures propagate
   * to sbt's command handler, which retains that incoming command state as well.
   *
   * runInputTask does not aggregate tasks. A root project's collected Test / definedTests
   * are still selected and executed through that root project's Test / testOptions.
   */
  def runTestOnlyWithTemporarySettings(state: State, glob: String, settings: Seq[Setting[?]]): State = {
    val newState = state.appendWithSession(settings)
    Project.extract(newState).runInputTask(Test / testOnly, glob, newState)
    state
  }
}
