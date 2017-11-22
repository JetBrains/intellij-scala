package org.jetbrains.plugins.scala.testingSupport

import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.{PsiLocation, RunnerAndConfigurationSettings}
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.smartTree.{TreeElement, TreeElementWrapper}
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import com.intellij.testFramework.EdtTestUtil
import com.intellij.util.concurrency.Semaphore
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.plugins.scala.lang.structureView.elements.impl.TestStructureViewElement
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl.TestItemRepresentation
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestRunConfiguration, AllInPackageTestData, ClassTestData, SingleTestData}

import scala.annotation.tailrec
import scala.collection.JavaConverters._

/**
  * @author Roman.Shein
  * @since 20.01.2015.
  */
trait IntegrationTest {

  protected def runTestFromConfig(
                                   configurationCheck: RunnerAndConfigurationSettings => Boolean,
                                   runConfig: RunnerAndConfigurationSettings,
                                   checkOutputs: Boolean = false,
                                   duration: Int = 3000,
                                   debug: Boolean = false
                                 ): (String, Option[AbstractTestProxy])

  protected def createTestFromLocation(lineNumber: Int, offset: Int, fileName: String): RunnerAndConfigurationSettings

  protected def createTestFromPackage(packageName: String): RunnerAndConfigurationSettings

  protected def createTestFromModule(moduleName: String): RunnerAndConfigurationSettings

  protected def createLocation(lineNumber: Int, offset: Int, fileName: String): PsiLocation[PsiElement]

  protected def runFileStructureViewTest(testClassName: String, status: Int, tests: String*)

  protected def runFileStructureViewTest(testClassName: String, testName: String, parentTestName: Option[String] = None, testStatus: Int = TestStructureViewElement.normalStatusId)

  protected def checkTestNodeInFileStructure(root: TreeElementWrapper, nodeName: String, parentName: Option[String], status: Int): Boolean = {

    def helper(root: AbstractTreeNode[_], currentParentName: String): Boolean = {
      root.getValue.isInstanceOf[TestStructureViewElement] && {
        val presentation = root.getValue.asInstanceOf[TreeElement].getPresentation
        presentation.isInstanceOf[TestItemRepresentation] && presentation.getPresentableText == nodeName &&
          presentation.asInstanceOf[TestItemRepresentation].testStatus == status &&
          parentName.forall(currentParentName == _)
      } ||
        root.getChildren.asScala.exists(helper(_, root.getValue.asInstanceOf[TreeElement].getPresentation.getPresentableText))
    }

    var res = false

    EdtTestUtil.runInEdtAndWait(() => res = helper(root, ""))
    res
  }

  protected def buildFileStructure(fileName: String): TreeElementWrapper

  def getProject: Project

  protected def addFileToProject(fileName: String, fileText: String)

  protected def checkConfigAndSettings(configAndSettings: RunnerAndConfigurationSettings, testClass: String, testNames: String*): Boolean = {
    val config = configAndSettings.getConfiguration
    checkConfig(testClass, testNames, config.asInstanceOf[AbstractTestRunConfiguration])
  }

  protected def checkPackageConfigAndSettings(configAndSettings: RunnerAndConfigurationSettings, packageName: String = "", generatedName: String = ""): Boolean = {
    val config = configAndSettings.getConfiguration
    val testConfig = config.asInstanceOf[AbstractTestRunConfiguration]
    testConfig.testConfigurationData match {
      case packageData: AllInPackageTestData => packageData.getTestPackagePath == packageName
      case _ => false
    }
  }

  protected def checkConfig(testClass: String, testNames: Seq[String], config: AbstractTestRunConfiguration): Boolean = {
    config.getTestClassPath == testClass && (config.testConfigurationData match {
      case testData: SingleTestData =>
        val configTests = parseTestName(testData.testName)
        configTests.size == testNames.size && ((configTests zip testNames) forall { case (actual, required) => actual == required })
      case _: ClassTestData => testNames.isEmpty
    })
  }

  protected def checkResultTreeHasExactNamedPath(root: AbstractTestProxy, names: String*): Boolean =
    checkResultTreeHasExactNamedPath(root, names)

  protected def checkResultTreeDoesNotHaveNodes(root: AbstractTestProxy, names: String*): Boolean =
    checkResultTreeDoesNotHaveNodes(root, names.toVector)

  protected def checkResultTreeDoesNotHaveNodes(root: AbstractTestProxy, names: Vector[String]): Boolean = {
    if (root.isLeaf && !names.contains(root.getName)) true
    else !names.contains(root.getName) && root.getChildren.asScala.forall(checkResultTreeDoesNotHaveNodes(_, names))
  }

  protected def getExactNamePathFromResultTree(root: AbstractTestProxy, names: Iterable[String], allowTail: Boolean = false): Option[List[AbstractTestProxy]] = {
    @tailrec
    def buildConditions(names: Iterable[String], acc: List[AbstractTestProxy => Boolean] = List()):
    List[AbstractTestProxy => Boolean] = names.size match {
      case 0 => List(_ => true) //got an empty list of names as initial input
      case 1 =>
        ((node: AbstractTestProxy) => node.getName == names.head && (node.isLeaf || allowTail)) :: acc //last element must be leaf
      case _ => buildConditions(names.tail,
        ((node: AbstractTestProxy) => node.getName == names.head && !node.isLeaf) :: acc)
    }
    getPathFromResultTree(root, buildConditions(names).reverse, allowTail)
  }

  protected def getPathFromResultTree(root: AbstractTestProxy,
                                      conditions: Iterable[AbstractTestProxy => Boolean], allowTail: Boolean = false):
  Option[List[AbstractTestProxy]] = {
    if (conditions.isEmpty) {
      if (allowTail) return Some(List()) else return None
    }
    if (conditions.head(root)) {
      val children = root.getChildren
      if (children.isEmpty && conditions.size == 1) Some(List(root))
      else children.asScala.map(getPathFromResultTree(_, conditions.tail, allowTail)).find(_.isDefined).
        flatten.map(tail => root :: tail)
    } else {
      None
    }
  }

  protected def checkResultTreeHasExactNamedPath(root: AbstractTestProxy, names: Iterable[String], allowTail: Boolean = false): Boolean =
    getExactNamePathFromResultTree(root, names, allowTail).isDefined

  protected def checkResultTreeHasPath(root: AbstractTestProxy, conditions: Iterable[AbstractTestProxy => Boolean],
                                       allowTail: Boolean = false): Boolean =
    getPathFromResultTree(root, conditions, allowTail).isDefined

  def runTestByLocation(lineNumber: Int, offset: Int, fileName: String,
                        configurationCheck: RunnerAndConfigurationSettings => Boolean,
                        testTreeCheck: AbstractTestProxy => Boolean,
                        expectedText: String = "OK", debug: Boolean = false, duration: Int = 10000,
                        checkOutputs: Boolean = false): Unit = {

    val runConfig = createTestFromLocation(lineNumber, offset, fileName)

    runTestByConfig(runConfig, configurationCheck, testTreeCheck, expectedText, debug, duration, checkOutputs)
  }

  def runTestByConfig(runConfig: RunnerAndConfigurationSettings, configurationCheck: RunnerAndConfigurationSettings => Boolean,
                      testTreeCheck: AbstractTestProxy => Boolean,
                      expectedText: String = "OK", debug: Boolean = false, duration: Int = 10000,
                      checkOutputs: Boolean = false): Unit = {
    val (res, testTreeRoot) = runTestFromConfig(configurationCheck, runConfig, checkOutputs, duration, debug)

    val semaphore = new Semaphore
    semaphore.down()

    invokeLater(semaphore.up())

    semaphore.waitFor()

    assert(testTreeRoot.isDefined, s"testTreeRoot not defined")
    assert(testTreeCheck(testTreeRoot.get), s"testTreeCheck failed for root ${testTreeRoot.get}")

    if (checkOutputs) {
      assert(res == expectedText, s"output was '$res' expected '$expectedText'")
    }
  }

  def runDuplicateConfigTest(lineNumber: Int, offset: Int, fileName: String,
                             configurationCheck: RunnerAndConfigurationSettings => Boolean): Unit = {
    val config1 = createTestFromLocation(lineNumber, offset, fileName)
    val config2 = createTestFromLocation(lineNumber, offset, fileName)
    //    assert(configurationCheck(config1))
    //    assert(configurationCheck(config2))
    assert(config1.getName == config2.getName)
    assert(config1.getType == config2.getType)
    assert(config1.getFolderName == config2.getFolderName)
    assert(config1.getConfiguration.getName == config2.getConfiguration.getName)
  }

  def runGoToSourceTest(lineNumber: Int, offset: Int, fileName: String,
                        configurationCheck: RunnerAndConfigurationSettings => Boolean, testNames: Iterable[String],
                        sourceLine: Int): Unit = {
    val runConfig = createTestFromLocation(lineNumber, offset, fileName)

    val (_, testTreeRoot) = runTestFromConfig(configurationCheck, runConfig)

    assert(testTreeRoot.isDefined)
    EdtTestUtil.runInEdtAndWait(() => checkGoToSourceTest(testTreeRoot.get, testNames, fileName, sourceLine))
  }

  private def checkGoToSourceTest(testRoot: AbstractTestProxy, testNames: Iterable[String], sourceFile: String, sourceLine: Int) {
    val testPathOpt = getExactNamePathFromResultTree(testRoot, testNames, allowTail = true)
    assert(testPathOpt.isDefined, s"no test path found under ${testRoot.getName} for test names ${testNames.mkString(", ")}")
    val test = testPathOpt.get.last
    val project = getProject
    val location = test.getLocation(project, GlobalSearchScope.projectScope(project))
    val psiElement = location.getPsiElement
    val psiFile = psiElement.getContainingFile
    val textRange = psiElement.getTextRange
    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
    assert(psiFile.getName == sourceFile)
    val startLineNumber = document.getLineNumber(textRange.getStartOffset)
    assert(startLineNumber == sourceLine)
  }

  private def parseTestName(testName: String): Seq[String] = {
    testName.split("\n").map(TestRunnerUtil.unescapeTestName)
  }

}
