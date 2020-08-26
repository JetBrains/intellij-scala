package org.jetbrains.plugins.scala.testingSupport

import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.{PsiLocation, RunnerAndConfigurationSettings}
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import com.intellij.testFramework.EdtTestUtil
import org.jetbrains.plugins.scala.testingSupport.test.testdata.{AllInPackageTestData, ClassTestData, SingleTestData}
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestRunConfiguration, SearchForTest}
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions._
import org.junit.Assert
import org.junit.Assert._

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._
import scala.util.Try

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait IntegrationTest {

  protected def DoNotCheck: AbstractTestProxy => Boolean = _ => true
  protected def DoNotCheck(implicit d: DummyImplicit): AbstractTestProxy => Unit = _ => ()

  protected def runTestFromConfig(
    configurationAssert: RunnerAndConfigurationSettings => Unit,
    runConfig: RunnerAndConfigurationSettings,
    checkOutputs: Boolean = false,
    duration: Int = 3000
  ): (String, Option[AbstractTestProxy])

  protected def createTestFromLocation(lineNumber: Int, offset: Int, fileName: String): RunnerAndConfigurationSettings

  protected def createTestFromPackage(packageName: String): RunnerAndConfigurationSettings

  protected def createTestFromModule(moduleName: String): RunnerAndConfigurationSettings

  protected def createLocation(lineNumber: Int, offset: Int, fileName: String): PsiLocation[PsiElement]

  def getProject: Project

  protected def assertConfigAndSettings(configAndSettings: RunnerAndConfigurationSettings, testClass: String, testNames: String*): Unit = {
    val config = configAndSettings.getConfiguration
    assertConfig(testClass, testNames, config.asInstanceOf[AbstractTestRunConfiguration])
  }

  protected def assertPackageConfigAndSettings(configAndSettings: RunnerAndConfigurationSettings, packageName: String = "", generatedName: String = ""): Unit = {
    val config = configAndSettings.getConfiguration
    val testConfig = config.asInstanceOf[AbstractTestRunConfiguration]
    val packageData = assertIsA[AllInPackageTestData](testConfig.testConfigurationData)
    assertEquals("package name are not equal", packageName, packageData.getTestPackagePath)
  }

  private def assertModule(config: AbstractTestRunConfiguration): Unit =
    config.testConfigurationData.searchTest match {
      case SearchForTest.IN_WHOLE_PROJECT =>
      case _ =>
        assertNotNull("module should not be null", config.getModule)
    }

  private def assertConfig(testClass: String, testNames: Seq[String], config: AbstractTestRunConfiguration): Unit = {
    assertEquals(testClass, config.testConfigurationData.asInstanceOf[ClassTestData].getTestClassPath)
    config.testConfigurationData match {
      case testData: SingleTestData =>
        val configTests = parseTestName(testData.testName)
        assertArrayEquals("test names should be the same as expected", testNames, configTests)
      case _: ClassTestData =>
        assertTrue("test names should be empty for whole-class test run configuration", testNames.isEmpty)
    }
    assertModule(config)
  }

  protected def assertResultTreeDoesNotHaveNodes(root: AbstractTestProxy, names: String*): Unit =
    if (names.contains(root.getName))
      fail(s"Test tree contains unexpected node '${root.getName}'")
    else if (!root.isLeaf) {
      val children = root.getChildren.asScala
      children.foreach(assertResultTreeDoesNotHaveNodes(_, names: _*))
    }

  private def pathString(names: Iterable[String]) =
    names.mkString(" / ")

  protected def getExactNamePathFromResultTree(root: AbstractTestProxy, names: Iterable[String], allowTail: Boolean = false): Option[List[AbstractTestProxy]] = {
    @tailrec
    def buildConditions(
      names: Iterable[String],
      acc: List[AbstractTestProxy => Boolean] = List()
    ): List[AbstractTestProxy => Boolean] = names.toList match {
      case Nil => List(DoNotCheck) //got an empty list of names as initial input
      case head :: Nil =>
        //last element must be leaf
        val cond = (node: AbstractTestProxy) => node.getName == head && (node.isLeaf || allowTail)
        cond :: acc
      case head :: tail =>
        val cond = (node: AbstractTestProxy) => node.getName == head && !node.isLeaf
        buildConditions(tail, cond :: acc)
    }

    getPathFromResultTree(root, buildConditions(names).reverse, allowTail)
  }

  protected def getPathFromResultTree(root: AbstractTestProxy,
                                      conditions: Iterable[AbstractTestProxy => Boolean],
                                      allowTail: Boolean = false): Option[List[AbstractTestProxy]] = {
    conditions.toList match {
      case Nil =>
        if (allowTail) Some(List())
        else None
      case condHead :: condTail if condHead(root) =>
        val children = root.getChildren
        if (children.isEmpty && condTail.isEmpty) {
          Some(List(root))
        } else {
          children.asScala
            .map(getPathFromResultTree(_, condTail, allowTail))
            .find(_.isDefined)
            .flatten
            .map(tail => root :: tail)
        }
      case _ =>
        None
    }
  }

  protected def checkResultTreeHasExactNamedPath(root: AbstractTestProxy, names: Iterable[String], allowTail: Boolean = false): Boolean =
    getExactNamePathFromResultTree(root, names, allowTail).isDefined

  private def allAvailablePaths(root: AbstractTestProxy): Seq[Seq[String]] = {
    def inner(node: AbstractTestProxy, curPath: List[String]): Seq[Seq[String]] = {
      val path = node.getName :: curPath
      if (node.isLeaf)
        path :: Nil
      else
        node.getChildren.asScala.flatMap(inner(_, path)).toSeq
    }
    val result = inner(root, Nil)
    result.map(_.reverse).sortBy(_.mkString)
  }

  protected def assertResultTreeHasExactNamedPath(root: AbstractTestProxy, names: Iterable[String], allowTail: Boolean = false): Unit =
    getExactNamePathFromResultTree(root, names, allowTail) match {
      case None =>
        val allPaths = allAvailablePaths(root)
        val allPathsText = allPaths.map(pathString).mkString("\n")
        fail(s"Test tree doesn't contain test with expected path '${pathString(names)}'\navailable paths:\n$allPathsText")
      case _ =>
    }

  protected def assertResultTreeHasNotGotExactNamedPath(root: AbstractTestProxy, names: Iterable[String], allowTail: Boolean = false): Unit =
    getExactNamePathFromResultTree(root, names, allowTail) match {
      case Some(_) =>
        fail(s"Test tree contains test with unexpected path '${pathString(names)}'")
      case _ =>
    }

  protected def assertResultTreeHasExactNamedPaths(root: AbstractTestProxy, allowTail: Boolean = false)(names: Iterable[Iterable[String]]): Unit =
    names.foreach(assertResultTreeHasExactNamedPath(root, _, allowTail))

  protected def assertResultTreeHasNotGotExactNamedPaths(root: AbstractTestProxy, allowTail: Boolean = false)(names: Iterable[Iterable[String]]): Unit =
    names.foreach(assertResultTreeHasNotGotExactNamedPath(root, _, allowTail))
  
  protected def checkResultTreeHasPath(root: AbstractTestProxy, conditions: Iterable[AbstractTestProxy => Boolean],
                                       allowTail: Boolean = false): Boolean =
    getPathFromResultTree(root, conditions, allowTail).isDefined

  def runTestByLocation2(lineNumber: Int, offset: Int, fileName: String,
                         configurationAssert: RunnerAndConfigurationSettings => Unit,
                         testTreeAssert: AbstractTestProxy => Unit,
                         expectedText: String = "OK",
                         duration: Int = 10000,
                         checkOutputs: Boolean = false): Unit = {
    val runConfig = createTestFromLocation(lineNumber, offset, fileName)
    runTestByConfig2(runConfig, configurationAssert, testTreeAssert, expectedText, duration, checkOutputs)
  }

  def runTestByConfig(runConfig: RunnerAndConfigurationSettings,
                      configurationCheck: RunnerAndConfigurationSettings => Boolean,
                      testTreeCheck: AbstractTestProxy => Boolean,
                      expectedText: String = "OK",
                      duration: Int = 10000,
                      checkOutputs: Boolean = false): Unit =
    runTestByConfig2(
      runConfig,
      assertFromCheck(configurationCheck),
      assertFromCheck(testTreeCheck),
      expectedText,
      duration,
      checkOutputs
    )

  def runTestByConfig2(runConfig: RunnerAndConfigurationSettings,
                       configurationAssert: RunnerAndConfigurationSettings => Unit,
                       testTreeAssert: AbstractTestProxy => Unit,
                       expectedText: String = "OK",
                       duration: Int = 10000,
                       checkOutputs: Boolean = false): Unit = {
    val (res, testTreeRoot) = runTestFromConfig(configurationAssert, runConfig, checkOutputs, duration)

    assertTrue(s"testTreeRoot not defined", testTreeRoot.isDefined)
    testTreeAssert(testTreeRoot.get)

    if (checkOutputs) {
      assertTrue(s"output was '$res' expected to contain '$expectedText'", res.contains(expectedText))
    }
  }

  protected def checkFromAssert(assertBody: => Unit): Boolean =
    Try(assertBody).map(_ => true)
      .recover { case _: AssertionError => false }
      .get

  protected def assertFromCheck(configurationCheck: RunnerAndConfigurationSettings => Boolean): RunnerAndConfigurationSettings => Unit = { runConfig =>
    assertTrue(s"config check failed for ${runConfig.getName}", configurationCheck(runConfig))
  }

  protected def assertFromCheck(testTreeCheck: AbstractTestProxy => Boolean)
                               (implicit d: DummyImplicit): AbstractTestProxy => Unit = { testTreeRoot =>
    assertTrue(s"testTreeCheck failed for root $testTreeRoot", testTreeCheck(testTreeRoot))
  }

  def runDuplicateConfigTest(lineNumber: Int, offset: Int, fileName: String,
                             assertConfigurationCheck: RunnerAndConfigurationSettings => Unit): Unit = {
    val config1 = createTestFromLocation(lineNumber, offset, fileName)
    val config2 = createTestFromLocation(lineNumber, offset, fileName)
    //assertConfigurationCheck(config1)
    //assertConfigurationCheck(config2)
    assertEquals(config1.getName, config2.getName)
    assertEquals(config1.getType, config2.getType)
    assertEquals(config1.getFolderName, config2.getFolderName)
    assertEquals(config1.getConfiguration.getName, config2.getConfiguration.getName)
  }

  def runGoToSourceTest(lineNumber: Int, offset: Int,
                        fileName: String,
                        assertConfiguration: RunnerAndConfigurationSettings => Unit,
                        testNames: Iterable[String],
                        sourceLine: Int,
                        sourceFile: Option[String] = None): Unit = {
    val runConfig = createTestFromLocation(lineNumber, offset, fileName)

    val (_, testTreeRoot) = runTestFromConfig(assertConfiguration, runConfig)

    assertTrue("testTreeRoot not defined", testTreeRoot.isDefined)
    EdtTestUtil.runInEdtAndWait(() => {
      assertGoToSourceTest(testTreeRoot.get, testNames, sourceFile.getOrElse(fileName), sourceLine)
    })
  }

  private def assertGoToSourceTest(testRoot: AbstractTestProxy,
                                   testNames: Iterable[String],
                                   sourceFile: String,
                                   sourceLine: Int): Unit = {
    val testPathOpt = getExactNamePathFromResultTree(testRoot, testNames, allowTail = true)
    assertTrue(s"no test path found under ${testRoot.getName} for test names ${testNames.mkString(", ")}", testPathOpt.isDefined)
    val test = testPathOpt.get.last
    val project = getProject
    val location = test.getLocation(project, GlobalSearchScope.projectScope(project))
    val psiElement = location.getPsiElement
    val psiFile = psiElement.getContainingFile
    val textRange = psiElement.getTextRange
    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
    assertEquals(sourceFile, psiFile.getName)
    val startLineNumber = document.getLineNumber(textRange.getStartOffset)
    assertEquals(sourceLine, startLineNumber)
  }

  protected def parseTestName(testName: String): Seq[String] = {
    testName.split("\n").map(unescapeTestName)
  }

  protected def unescapeTestName(str: String): String = {
    TestRunnerUtil.unescapeTestName(str)
  }

  def assertArrayEquals(message: String, expecteds: Seq[String], actuals: Seq[String]): Unit =
    Assert.assertEquals(message, expecteds, actuals)
}
