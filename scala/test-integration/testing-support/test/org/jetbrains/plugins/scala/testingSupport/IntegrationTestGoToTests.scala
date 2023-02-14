package org.jetbrains.plugins.scala.testingSupport

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.configurations.TestLocation.CaretLocation
import org.jetbrains.plugins.scala.extensions.{PsiNamedElementExt, inReadAction}
import org.junit.Assert.{assertEquals, assertNotNull, fail}
import org.junit.ComparisonFailure

import scala.annotation.tailrec
import scala.jdk.CollectionConverters.CollectionHasAsScala

trait IntegrationTestGoToTests extends AnyRef
  with IntegrationTestConfigurationRunning
  with IntegrationTestTreeViewAssertions
  with IntegrationTestConfigAssertions
  with IntegrationTestConfigurationCreation {

  protected def getProject: Project

  protected def runGoToSourceTest(
    caretLocation: CaretLocation,
    assertConfiguration: RunnerAndConfigurationSettings => Unit,
    testPath: TestNodePath,
    sourceLine: Int,
    sourceFile: Option[String] = None
  )(implicit testOptions: TestRunOptions): Unit =
    runGoToSourceTest(
      caretLocation,
      assertConfiguration,
      testPath,
      GoToLocation(sourceFile.getOrElse(caretLocation.fileName), sourceLine)
    )(testOptions)

  case class GoToLocation(sourceFile: String, sourceLine: Int)

  protected def runGoToSourceTest(
    caretLocation: CaretLocation,
    assertConfiguration: RunnerAndConfigurationSettings => Unit,
    testPath: TestNodePath,
    expectedLocation: GoToLocation
  )(implicit testOptions: TestRunOptions): Unit = {
    val runConfig = createTestFromCaretLocation(caretLocation)

    assertConfiguration(runConfig)

    val runResult = runTestFromConfig(runConfig, testOptions.duration)

    val testTreeRoot = runResult.requireTestTreeRoot
    assertGoToSourceTest(testTreeRoot, testPath, expectedLocation)
  }

  protected def assertGoToSourceTest(
    testRoot: AbstractTestProxy,
    testPath: TestNodePath,
    expectedLocation: GoToLocation
  ): Unit = inReadAction {
    val testPathOpt = getExactNamePathFromResultTree(testRoot, testPath, allowTail = true)

    val project = getProject

    val psiElement = {
      val leafNode = testPathOpt.nodes.last
      val location = leafNode.getLocation(project, GlobalSearchScope.projectScope(project))
      assertNotNull(s"location should not be null for leaf node: $leafNode", location)
      location.getPsiElement
    }

    val psiFile = psiElement.getContainingFile
    val textRange = psiElement.getTextRange

    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
    assertEquals(expectedLocation.sourceFile, psiFile.name)

    val startLineNumber = document.getLineNumber(textRange.getStartOffset)
    assertEquals(expectedLocation.sourceLine, startLineNumber)
  }

  private def getExactNamePathFromResultTree(
    root: AbstractTestProxy,
    path: TestNodePath,
    allowTail: Boolean
  ): TestProxyNodePath = {
    val result = getExactNamePathFromResultTreeOpt(root, path, allowTail)
    result.getOrElse {
      // using ComparisonFailure to allow viewing results in "diff" view
      throw new ComparisonFailure(
        s"Test tree `${root.getName}` doesn't contain test with expected path `${path.pathString}`",
        pathsDetailsText(path.parts),
        allAvailablePathsDetails(root),
      )
    }
  }

  private val AlwaysTrue: AbstractTestProxy => Boolean = _ => true

  private def getExactNamePathFromResultTreeOpt(
    root: AbstractTestProxy,
    path: TestNodePath,
    allowTail: Boolean
  ): Option[TestProxyNodePath] = {
    @tailrec
    def buildConditions(
      names: Iterable[String],
      acc: List[AbstractTestProxy => Boolean] = List()
    ): List[AbstractTestProxy => Boolean] = names.toList match {
      case Nil =>
        List(AlwaysTrue) //got an empty list of names as initial input
      case head :: Nil =>
        //last element must be leaf
        val cond = (node: AbstractTestProxy) => node.getName == head && (node.isLeaf || allowTail)
        cond :: acc
      case head :: tail =>
        val cond = (node: AbstractTestProxy) => node.getName == head && !node.isLeaf
        buildConditions(tail, cond :: acc)
    }

    val conditions = buildConditions(path.parts).reverse
    getSingleMatchingPathFromResultTree(root, conditions, allowTail)
  }

  private def getSingleMatchingPathFromResultTree(
    root: AbstractTestProxy,
    nodeConditions: List[AbstractTestProxy => Boolean],
    allowTail: Boolean
  ): Option[TestProxyNodePath] = {
    val matchingPaths = getAllMatchingPathsFromResultTree(root, nodeConditions, allowTail)

    // when `allowTail = false` we can get same common path to non-leaf node from different leaf-paths
    // in this case we ensure that they are the same

    val shouldFail =
      if (allowTail) matchingPaths.distinct.size > 1
      else matchingPaths.size > 1

    if (shouldFail) {
      val message =
        s"""More than one matching paths found in resulting tree:
           |matching paths:
           |${pathsDetails(matchingPaths)}
           |all available paths:
           |${allAvailablePathsDetails(root)}""".stripMargin
      fail(message).asInstanceOf[Nothing]
    }
    else
      matchingPaths.headOption
  }

  private def getAllMatchingPathsFromResultTree(
    node: AbstractTestProxy,
    nodeConditions: List[AbstractTestProxy => Boolean],
    allowTail: Boolean
  ): Seq[TestProxyNodePath] =
    nodeConditions match {
      case Nil =>
        if (allowTail) List(TestProxyNodePath.empty)
        else Nil
      case condHead :: condTail if condHead(node) =>
        val children = node.getChildren.asScala.toList
        if (children.isEmpty && condTail.isEmpty)
          List(TestProxyNodePath(node :: Nil))
        else {
          for {
            childNode <- children
            childPaths <- getAllMatchingPathsFromResultTree(childNode, condTail, allowTail)
          } yield node +: childPaths
        }
      case _ =>
        Nil
    }
}
