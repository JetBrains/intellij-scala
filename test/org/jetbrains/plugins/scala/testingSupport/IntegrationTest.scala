package org.jetbrains.plugins.scala.testingSupport

import javax.swing.SwingUtilities

import com.intellij.execution.{PsiLocation, RunnerAndConfigurationSettings, Location}
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiDocumentManager}
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration

import scala.annotation.tailrec

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

  protected def createLocation(lineNumber: Int, offset: Int, fileName: String): PsiLocation[PsiElement]

  def getProject: Project

  protected def addFileToProject(fileName: String, fileText: String)

  protected def checkConfigAndSettings(configAndSettings: RunnerAndConfigurationSettings, testClass: String, testName: Option[String] = None): Boolean

  protected def checkConfigAndSettings(configAndSettings: RunnerAndConfigurationSettings, testClass: String, testName: String): Boolean =
    checkConfigAndSettings(configAndSettings, testClass, Some(testName))

  protected def checkConfig(testClass: String, testName: Option[String], config: AbstractTestRunConfiguration): Boolean = {
    config.getTestClassPath == testClass && testName.map(config.getTestName == _).getOrElse(config.getTestName == "")
  }

  protected def checkResultTreeHasExactNamedPath(root: AbstractTestProxy, names: String*): Boolean =
    checkResultTreeHasExactNamedPath(root, names)

  protected def checkResultTreeDoesNotHaveNodes(root: AbstractTestProxy, names: String*): Boolean =
    checkResultTreeDoesNotHaveNodes(root, names)

  protected def checkResultTreeDoesNotHaveNodes(root: AbstractTestProxy, names: Iterable[String]): Boolean = {
    import scala.collection.JavaConversions._
    if (root.isLeaf && !names.contains(root.getName)) true
    else !names.contains(root.getName) && root.getChildren.toList.forall(checkResultTreeDoesNotHaveNodes(_, names))
  }

  protected def getExactNamePathFromResultTree(root: AbstractTestProxy, names: Iterable[String], allowTail: Boolean = false): Option[List[AbstractTestProxy]] = {
    @tailrec
    def buildConditions(names: Iterable[String], acc: List[AbstractTestProxy => Boolean] = List()):
    List[AbstractTestProxy => Boolean] = names.size match {
      case 0 => List(_ => true) //got an empty list of names as initial input
      case 1 =>
        ((node: AbstractTestProxy) => node.getName == names.head && node.isLeaf) :: acc //last element must be leaf
      case _ => buildConditions(names.tail,
        ((node: AbstractTestProxy) => node.getName == names.head && !node.isLeaf) :: acc)
    }
    getPathFromResultTree(root, buildConditions(names).reverse, allowTail)
  }

  protected def getPathFromResultTree(root: AbstractTestProxy,
                                      conditions: Iterable[AbstractTestProxy => Boolean], allowTail: Boolean = false):
  Option[List[AbstractTestProxy]] = {
    import scala.collection.JavaConversions._
    if (conditions.isEmpty) {
      if (allowTail) return Some(List()) else return None
    }
    if (conditions.head(root)) {
      val children = root.getChildren
      if (children.isEmpty && conditions.size == 1) Some(List(root))
      else children.toList.map(getPathFromResultTree(_, conditions.tail, allowTail)).find(_.isDefined).
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
                        expectedText: String = "OK", debug: Boolean = false, duration: Int = 3000,
                        checkOutputs: Boolean = false) = {

    val runConfig = createTestFromLocation(lineNumber, offset, fileName)

    val (res, testTreeRoot) = runTestFromConfig(configurationCheck, runConfig, checkOutputs, duration, debug)

    SwingUtilities.invokeLater(new Runnable() {
      override def run(): Unit = {
        assert(testTreeRoot.isDefined && testTreeCheck(testTreeRoot.get))

        if (checkOutputs) {
          assert(res == expectedText)
        }
      }
    })
  }

  def runDuplicateConfigTest(lineNumber: Int, offset: Int, fileName: String,
                             configurationCheck: RunnerAndConfigurationSettings => Boolean): Unit = {
    val config1 = createTestFromLocation(lineNumber, offset, fileName)
    val config2 = createTestFromLocation(lineNumber, offset, fileName)
    assert(config1.getName == config2.getName)
    assert(config1.getType == config2.getType)
    assert(config1.getFolderName == config2.getFolderName)
    assert(config1.getConfiguration.getName == config2.getConfiguration.getName)
  }

  def runGoToSourceTest(lineNumber: Int, offset: Int, fileName: String,
                        configurationCheck: RunnerAndConfigurationSettings => Boolean, testNames: Iterable[String],
                        sourceLine: Int) = {
    val runConfig = createTestFromLocation(lineNumber, offset, fileName)

    val (res, testTreeRoot) = runTestFromConfig(configurationCheck, runConfig)

    SwingUtilities.invokeLater(new Runnable() {
      override def run(): Unit = {
        assert(testTreeRoot.isDefined)
        checkGoToSourceTest(testTreeRoot.get, testNames, fileName, sourceLine)
      }
    })
  }

  private def checkGoToSourceTest(testRoot: AbstractTestProxy, testNames: Iterable[String], sourceFile: String, sourceLine: Int) {
    val testPathOpt = getExactNamePathFromResultTree(testRoot, testNames, allowTail = true)
    assert(testPathOpt.isDefined)
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


}
