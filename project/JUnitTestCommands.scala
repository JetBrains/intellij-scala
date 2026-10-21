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
   * Combines optional class bucketing with an additional per-class condition.
   * Returns None when both are absent, so unbucketed category commands add no filter.
   * An absent bucket or additional condition imposes no restriction on class selection.
   *
   * Bucket membership is checked first. The additional condition is evaluated only for
   * classes that pass that check. Pass lazily initialized predicates through a wrapper
   * lambda so constructing the filter does not trigger their initialization, such as a
   * TeamCity retry lookup.
   *
   * @param bucket validated bucket selection, or None for all buckets
   * @param additionalFilter optional condition, defaulting to no additional restriction
   * @return a combined class filter, or None when no restrictions were supplied
   */
  def classFilter(
    bucket: Option[TestBucketing.Bucket],
    additionalFilter: Option[String => Boolean] = None
  ): Option[Tests.Filter] =
    if (bucket.isEmpty && additionalFilter.isEmpty) None
    else Some(Tests.Filter(testName =>
      bucket.forall(_.matches(testName)) && additionalFilter.forall(_(testName))
    ))

  /**
   * Runs JUnit 4 and Jupiter tests with category/tag selection and optional class bucketing.
   * Call this from a command callback: bucket configuration is read and validated once
   * per invocation, before test discovery or execution. TC_SBT_TEST_BUCKETS_TOTAL and
   * TC_SBT_TEST_BUCKETS_CURRENT must either both be absent (no bucketing), or be integers
   * with a positive total and a zero-based current index smaller than the total.
   *
   * Selection happens at two levels. sbt intersects the optional class filter with the
   * testOnly glob and any existing user class filters before framework execution. All
   * methods of a class therefore stay in the same bucket. JUnit 4 then applies category
   * inclusion/exclusion and Jupiter applies the equivalent tag rules within those classes,
   * including rules on individual methods. Empty inclusion/exclusion lists add no restriction.
   *
   * Bucket membership is checked before additionalFilter, so a rejected class cannot trigger
   * an additional condition such as a TeamCity retry lookup. The default adds no condition.
   * Wrap lazily initialized predicates in a lambda at the call site to defer initialization
   * until an accepted class is evaluated. This helper itself has no TeamCity dependency.
   *
   * Options belong to the current project's Test / testOptions, where testOnly runs.
   * A root project's collected dependency Test / definedTests are filtered here as well;
   * this does not rely on testOnly task aggregation. additionalSettings are appended after
   * the generated test options, allowing command-specific setup in the same temporary state.
   * User session settings are preserved, and command-owned settings do not affect subsequent
   * commands, including after a failed test run.
   *
   * @param glob testOnly input, including leading whitespace before a non-empty selector
   *             Examples:
   *             {{{
   *             ""                 // All otherwise-selected classes.
   *             " com.intellij.*"  // Only classes matching this package glob.
   *             }}}
   * @param additionalFilter optional per-class condition, evaluated after bucket membership
   *                         Example (the wrapper defers the lazy TeamCity lookup):
   *                         {{{
   *                         Some(testName => TeamCityTestRetryFiltering.shouldRunTestInRerun(testName))
   *                         }}}
   * @param additionalSettings command-specific sbt settings, empty by default
   *                           Example (Ultimate's plugin setup for this test run):
   *                           {{{
   *                           Seq(disablePluginsBeforeRunningTests())
   *                           }}}
   * @return the incoming state after successful execution; test failures propagate to sbt
   */
  def runTestOnly(
    state: State,
    glob: String,
    includeCategories: Seq[String],
    excludeCategories: Seq[String],
    additionalFilter: Option[String => Boolean] = None,
    additionalSettings: Seq[Setting[?]] = Seq.empty
  ): State = {
    val bucket = TestBucketing.fromEnvironment(sys.env).fold(sys.error, identity)
    val frameworkOptions = JUnitTestCommands.frameworkOptions
    val categoryOptions = JUnitTestCommands.categoryOptions(includeCategories, excludeCategories)
    val classFilter = JUnitTestCommands.classFilter(bucket, additionalFilter)
    val options = frameworkOptions ++ categoryOptions ++ classFilter.toSeq
    val settings = Seq(
      Test / testOptions ++= options
    ) ++ additionalSettings

    runTestOnlyWithTemporarySettings(state, glob, settings)
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
