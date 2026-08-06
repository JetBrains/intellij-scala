package org.jetbrains.plugins.scala.testingSupport

import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.junit.Assert.{assertEquals, fail}

import scala.jdk.CollectionConverters.CollectionHasAsScala

trait IntegrationTestTreeViewAssertions {

  case class TestProxyNodePath(nodes: List[AbstractTestProxy]) {
    def :+(node: AbstractTestProxy): TestProxyNodePath = TestProxyNodePath(nodes :+ node)
    def +:(node: AbstractTestProxy): TestProxyNodePath = TestProxyNodePath(node +: nodes)

    def pathString: String = mkString(" / ")
    def mkString(sep: String): String = nodes.map(_.getName).mkString(sep)
  }

  object TestProxyNodePath {
    def empty: TestProxyNodePath = TestProxyNodePath(Nil)
  }

  case class TestNodePathWithStatus(status: Option[Magnitude], path: TestNodePath) {
    def :+(part: String): TestNodePathWithStatus = TestNodePathWithStatus(status, path :+ part)

    def pathString: String =
      path.pathString + statusStr("")
    def pathStringRequireStatus: String =
      path.pathString + statusStr(fail(s"test node path doesn't have any status: $pathString").asInstanceOf[Nothing])

    private def statusStr(ifEmpty: => String): String =
      status.fold(ifEmpty)(s => s" (${s.getTitle})")
  }

  object TestNodePathWithStatus {
    def apply(status: Magnitude, pathParts: Seq[String]): TestNodePathWithStatus =
      new TestNodePathWithStatus(Some(status), new TestNodePath(pathParts.toList))
    def apply(status: Magnitude, pathParts: String*)(implicit d: DummyImplicit): TestNodePathWithStatus =
      new TestNodePathWithStatus(Some(status), new TestNodePath(pathParts.toList))
    def apply(status: Magnitude, path: TestNodePath): TestNodePathWithStatus =
      new TestNodePathWithStatus(Some(status), path)
  }

  case class TestNodePath(parts: List[String]) {
    def :+(part: String): TestNodePath = TestNodePath(parts :+ part)

    def drop(n: Int): TestNodePath = TestNodePath(parts.drop(n))
    def reverse: TestNodePath = TestNodePath(parts.reverse)

    def pathString: String = mkString(TestNodePath.PartsSeparator)
    def mkString(sep: String): String = parts.mkString(sep)
  }

  object TestNodePath {
    val PartsSeparator = " / "
    def apply(parts: String*): TestNodePath = new TestNodePath(parts.toList)
    def parse(partsConcat: String): TestNodePath = TestNodePath(partsConcat.split(PartsSeparator).toList)
    def empty: TestNodePath = TestNodePath(Nil)
  }

  protected def nodeMagnitude(node: AbstractTestProxy): Option[Magnitude] =
    node match {
      case smTest: SMTestProxy => Some(smTest.getMagnitudeInfo)
      case _                   => None
    }

  protected def assertResultTreePathsEqualsUnordered(
    root: AbstractTestProxy
  )(expectedPaths: Iterable[TestNodePathWithStatus]): Unit = {
    val actualPaths = allAvailablePaths(root)
    assertCollectionEquals(
      "Test tree paths don't match",
      expectedPaths.toSeq.map(_.pathString).sorted,
      actualPaths.map(_.pathStringRequireStatus).sorted,
    )
  }

  protected final def assertResultTreeHasSinglePath(
    root: AbstractTestProxy,
    path: TestNodePathWithStatus
  ): Unit =
    assertResultTreePathsEqualsUnordered(root)(Seq(path))

  protected def assertResultTreeStatus(root: AbstractTestProxy, expectedMagnitude: Magnitude): Unit = {
    val values = TestStateInfo.Magnitude.values()
    val actualMagnitude = values(root.getMagnitude)
    assertEquals(s"Node status is wrong: ${root.getName}", expectedMagnitude, actualMagnitude)
  }

  protected def allAvailablePathsDetails(root: AbstractTestProxy): String = {
    val allPaths = allAvailablePaths(root)
    pathsDetails(allPaths)
  }

  protected def pathsDetails(paths: Seq[TestProxyNodePath]): String = {
    val pathStrings = paths.map(_.pathString)
    pathsDetailsText(pathStrings)
  }

  protected def pathsDetails(paths: Seq[TestNodePathWithStatus])(implicit d: DummyImplicit): String = {
    val pathStrings = paths.map(_.pathString)
    val details = pathsDetailsText(pathStrings)
    details
  }

  protected def pathsDetailsText(paths: Seq[String])(implicit d: DummyImplicit): String =
    paths.zipWithIndex.map { case (el, idx) => s"$idx: $el"}.mkString("\n")

  private def allAvailablePaths(root: AbstractTestProxy): Seq[TestNodePathWithStatus] = {
    def inner(node: AbstractTestProxy, curPath: List[AbstractTestProxy]): Seq[List[AbstractTestProxy]] = {
      val path = node +: curPath
      if (node.isLeaf)
        path :: Nil
      else {
        val children = node.getChildren.asScala
        children.flatMap(inner(_, path)).toSeq
      }
    }

    val paths = inner(root, Nil).map(_.reverse).map { proxyNodes =>
      val path = TestNodePath(proxyNodes.map(_.getName))
      val status = nodeMagnitude(proxyNodes.last)
      TestNodePathWithStatus(status, path)
    }
    paths.sortBy(_.pathString)
  }
}
