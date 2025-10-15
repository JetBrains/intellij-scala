package org.jetbrains.sbt.project

import junitparams.naming.TestCaseName
import junitparams.{JUnitParamsRunner, Parameters}
import org.jetbrains.sbt.project.SbtMigrateConfigurationsAction.ConfigDetails
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

import scala.annotation.unused

@RunWith(classOf[JUnitParamsRunner])
class SbtMigrateConfigurationsActionTest {

  // If separate modules for prod/test are enabled, then only main/test are passed to SbtMigrateConfigurationsAction#doFindModulesForConfig
  private val moduleNames_ProdTestSourcesEnabled = Seq(
    "root.main", "root.test",
    "root.foo.main", "root.foo.test",
    "root.foo.dummy.main", "root.foo.dummy.test",
    "root~1.main", "root~1.test",
    "root~1.foo.main", "root~1.foo.test"
  )

  private val moduleNames_ProdTestSourcesDisabled = Seq("root", "root.foo", "root.foo.dummy", "root~1", "root~1.foo")

  @unused("used reflectively by the @Parameters annotation")
  private def moduleNameAndMainClassHeuristicsConsistencyTestParameters: Array[AnyRef] = Array(
    // modules found based on the name -> root.foo.main, root.foo.test, root~1.foo.main, root~1.foo.test
    // modules with the main class -> root~1.foo.test
    Array(ConfigDetails(isTest = false, "foo", Some("Main1")), Seq("root~1.foo.test")),
    // modules found based on the name -> empty
    // modules with the main class -> root.foo.dummy.test
    Array(ConfigDetails(isTest = false, "notKnownName", Some("Main3")), Seq("root.foo.dummy.test")),
    // modules found based on the name -> empty
    // modules with the main class -> root~1.main, root~1.test
    Array(ConfigDetails(isTest = false, "root.foo.main", Some("Main4")), Seq("root~1.main", "root~1.test")),
    // modules found based on the name -> empty
    // modules with the main class -> empty
    Array(ConfigDetails(isTest = false, "notKnownName", Some("Alone")), Seq()),
    // modules found based on the name -> root.foo.main, root.foo.test
    // modules with the main class -> root~1.main, root~1.test
    Array(ConfigDetails(isTest = false, "root.foo", Some("Main4")), Seq("root~1.main", "root~1.test", "root.foo.main", "root.foo.test"))
  )

  /**
   * Test the combination of two heuristic results:
   *  1. Finding suitable modules based on their names.
   *  1. Finding suitable modules based on the existence of main classes.
   */
  @Test
  @Parameters(method = "moduleNameAndMainClassHeuristicsConsistencyTestParameters")
  @TestCaseName("{method}[config = {0}, expected = {1}]")
  def moduleNameAndMainClassHeuristicsConsistencyTest(configDetails: ConfigDetails, expectedResult: Seq[String]): Unit = {
    val moduleNameToClassesInside = Map (
      "root~1.foo.test" -> Seq("Main1"),
      "root.foo.dummy.test" -> Seq("Main3"),
      "root~1.main" -> Seq("Main4"),
      "root~1.test"-> Seq("Main4"),
    )
    execute(
      configDetails,
      moduleNames_ProdTestSourcesEnabled,
      prodTestSourcesSeparated = true,
      isDowngradingFromSeparateMainTestModules = Some(false),
      expectedResult,
      moduleNameToClassesInside
    )
  }

  /*
   * The following tests are focused on testing `org.jetbrains.sbt.project.SbtMigrateConfigurationsAction.isConfigurationCompatibleWithModuleName`,
   * which is responsible for finding suitable modules based on their names.
   */

  @unused("used reflectively by the @Parameters annotation")
  private def separateModulesForProdTestEnabled_isDowngradingNoneOrFalseTestParameters: Array[AnyRef] = Array(
    Array(ConfigDetails(isTest = false, "foo", Some("Main1")), Seq("root.foo.main", "root.foo.test", "root~1.foo.main", "root~1.foo.test")),
    Array(ConfigDetails(isTest = true, "foo", Some("Main1")), Seq("root.foo.test", "root~1.foo.test")),
    Array(ConfigDetails(isTest = true, "foo", Some("Main1")), Seq("root.foo.test", "root~1.foo.test")),
    Array(ConfigDetails(isTest = false, "notKnownName", Some("Main1")), Seq()),
    Array(ConfigDetails(isTest = false, "root.foo", Some("Main4")), Seq("root.foo.main", "root.foo.test")),
    Array(ConfigDetails(isTest = false, "root.foo.main", None), Seq())
  )

  @Test
  @Parameters(method = "separateModulesForProdTestEnabled_isDowngradingNoneOrFalseTestParameters")
  @TestCaseName("{method}[config = {0}, expected = {1}]")
  def separateModulesForProdTestEnabled_isDowngradingNoneOrFalseTest(configDetails: ConfigDetails, expectedResult: Seq[String]): Unit =
    Seq(None, Some(false)).foreach { isDowngrading =>
      execute(
        configDetails,
        moduleNames_ProdTestSourcesEnabled,
        prodTestSourcesSeparated = true,
        isDowngradingFromSeparateMainTestModules = isDowngrading,
        expectedResult
      )
    }

  @unused("used reflectively by the @Parameters annotation")
  // downgrading from separate main/test modules
  private def separateModulesForProdTestDisabled_isDowngradingTrueTestParameters: Array[AnyRef] = Array(
    Array(ConfigDetails(isTest = false, "root.foo.main", Some("Main")), Seq("root.foo")),
    Array(ConfigDetails(isTest = false, "root.foo.something", Some("Main")), Seq()),
    Array(ConfigDetails(isTest = false, "foo", Some("Main")), Seq()),
    Array(ConfigDetails(isTest = true, "foo.main", Some("Main")), Seq())
  )

  @Test
  @Parameters(method = "separateModulesForProdTestDisabled_isDowngradingTrueTestParameters")
  @TestCaseName("{method}[config = {0}, expected = {1}]")
  def separateModulesForProdTestDisabled_isDowngradingTrueTest(configDetails: ConfigDetails, expectedResult: Seq[String]): Unit =
    execute(
      configDetails,
      moduleNames_ProdTestSourcesDisabled,
      prodTestSourcesSeparated = false,
      isDowngradingFromSeparateMainTestModules = Some(true),
      expectedResult
    )

  @unused("used reflectively by the @Parameters annotation")
  //upgrading from the old grouping scheme to the new one
  private def separateModulesForProdTestDisabled_isDowngradingFalseTestParameters: Array[AnyRef] = Array(
    Array(ConfigDetails(isTest = true, "root.foo.main", None), Seq()),
    Array(ConfigDetails(isTest = false, "foo", Some("Main")), Seq("root.foo", "root~1.foo")),
    Array(ConfigDetails(isTest = false, "foo.dummy", Some("Main")), Seq("root.foo.dummy"))
  )
  
  @Test
  @Parameters(method = "separateModulesForProdTestDisabled_isDowngradingFalseTestParameters")
  @TestCaseName("{method}[config = {0}, expected = {1}]")
  def separateModulesForProdTestDisabled_isDowngradingFalseTest(configDetails: ConfigDetails, expectedResult: Seq[String]): Unit =
    execute(
      configDetails,
      moduleNames_ProdTestSourcesDisabled,
      prodTestSourcesSeparated = false,
      isDowngradingFromSeparateMainTestModules = Some(false),
      expectedResult
    )
    
  @unused("used reflectively by the @Parameters annotation")
  // It's unclear what occurred - the user called the action from all actions.
  // This might be upgrading from the old grouping to the new grouping,
  // or downgrading from separate modules for prod/test.
  private def separateModulesForProdTestDisabled_isDowngradingNoneTestParameters: Array[AnyRef] = Array(
    Array(ConfigDetails(isTest = false, "root.foo.main", Some("Main")), Seq("root.foo")),
    Array(ConfigDetails(isTest = false, "foo", Some("Main")), Seq("root.foo", "root~1.foo")),
    Array(ConfigDetails(isTest = true, "foo.main", Some("Main")), Seq())
  )
  
  @Test
  @Parameters(method = "separateModulesForProdTestDisabled_isDowngradingNoneTestParameters")
  @TestCaseName("{method}[config = {0}, expected = {1}]")
  def separateModulesForProdTestDisabled_isDowngradingNoneTest(configDetails: ConfigDetails, expectedResult: Seq[String]): Unit =
    execute(
      configDetails,
      moduleNames_ProdTestSourcesDisabled,
      prodTestSourcesSeparated = false,
      isDowngradingFromSeparateMainTestModules = None,
      expectedResult
    )

  private def execute(
    configDetails: ConfigDetails,
    moduleNames: Seq[String],
    prodTestSourcesSeparated: Boolean,
    isDowngradingFromSeparateMainTestModules: Option[Boolean],
    expectedResult: Seq[String],
    mapModuleToMainClasses: Map[String, Seq[String]] = Map.empty,
  ): Unit = {
    // These methods below simulate methods inside org.jetbrains.sbt.project.SbtMigrateConfigurationsAction.findModulesForConfig
    def _findModuleWithMainClass(mainClassName: String, moduleNames: Seq[String]): Option[String] =
      moduleNames.find { m =>
        val mainClasses = mapModuleToMainClasses.getOrElse(m, Seq.empty)
        mainClasses.contains(mainClassName)
      }

    def _findAllModulesWithMainClass(mainClassName: String): Seq[String] =
      mapModuleToMainClasses.filter { case (_, classNames) =>
        classNames.contains(mainClassName)
      }.keys.toSeq

    val result = SbtMigrateConfigurationsAction.doFindModulesForConfig(
      configDetails,
      moduleNames,
      prodTestSourcesSeparated,
      isDowngradingFromSeparateMainTestModules,
      _findModuleWithMainClass,
      _findAllModulesWithMainClass,
    )
    assertEquals(expectedResult.sorted, result.sorted)
  }
}
