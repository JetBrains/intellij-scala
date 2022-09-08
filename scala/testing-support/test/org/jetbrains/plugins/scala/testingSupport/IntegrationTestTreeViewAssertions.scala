package org.jetbrains.plugins.scala.testingSupport

import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import org.jetbrains.plugins.scala.testingSupport.IntegrationTestTreeViewAssertions.AlwaysTrue
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.junit.Assert.fail
import org.junit.ComparisonFailure

import scala.annotation.tailrec
import scala.jdk.CollectionConverters.CollectionHasAsScala

trait IntegrationTestTreeViewAssertions {

  trait TestTreeAssert extends Function1[AbstractTestProxy, Unit]

  case class TestProxyNodePath(nodes: List[AbstractTestProxy]) {
    def :+(node: AbstractTestProxy): TestProxyNodePath = TestProxyNodePath(nodes :+ node)
    def +:(node: AbstractTestProxy): TestProxyNodePath = TestProxyNodePath(node +: nodes)

    def pathString: String = mkString(" / ")
    def mkString(sep: String): String = nodes.map(_.getName).mkString(sep)
  }

  object TestProxyNodePath {
    def empty: TestProxyNodePath = TestProxyNodePath(Nil)
    def apply(nodes: AbstractTestProxy*): TestProxyNodePath = new TestProxyNodePath(nodes.toList)
  }

  case class TestNodePathWithStatus(path: TestNodePath, status: Option[Magnitude]) {
    def pathString: String =
      path.pathString + statusStr("")
    def pathStringRequireStatus: String =
      path.pathString + statusStr(fail(s"test node path doesn't have any status: $pathString").asInstanceOf[Nothing])

    private def statusStr(ifEmpty: => String): String =
      status.fold(ifEmpty)(s => s" (${s.getTitle})")
  }

  object TestNodePathWithStatus {
    def apply(path: TestNodePath, status: Magnitude): TestNodePathWithStatus =
      new TestNodePathWithStatus(path, Some(status))
    def apply(status: Magnitude, pathParts: String*): TestNodePathWithStatus =
      new TestNodePathWithStatus(new TestNodePath(pathParts.toList), Some(status))
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
    def p(s: String): TestNodePath = parse(s)
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
  )(expectedPaths: Iterable[TestNodePath]): Unit = {
    val actualPaths = allAvailablePaths(root)
    assertCollectionEquals(
      "Test tree paths don't match",
      expectedPaths.toSeq.map(_.pathString).sorted,
      actualPaths.map(_.path).map(_.pathString).sorted,
    )
  }

  protected def assertResultTreePathsEqualsUnordered2(
    root: AbstractTestProxy
  )(expectedPaths: Iterable[TestNodePathWithStatus]): Unit = {
    val actualPaths = allAvailablePaths(root)
    assertCollectionEquals(
      "Test tree paths don't match",
      expectedPaths.toSeq.map(_.pathString).sorted,
      actualPaths.map(_.pathStringRequireStatus).sorted,
    )
  }

  /**
   * TODO think of a better naming. Current name suggests that it checks whether `root` contains ONLY paths and nothing more.
   */
  protected def assertResultTreeHasExactNamedPaths(
    root: AbstractTestProxy
  )(paths: Iterable[TestNodePath]): Unit =
    paths.foreach(assertResultTreeHasExactNamedPath(root, _))

  protected def AssertResultTreeHasExactNamedPaths(
    root: AbstractTestProxy
  )(paths: Iterable[TestNodePath]): Unit =
    paths.foreach(assertResultTreeHasExactNamedPath(root, _))

  protected def assertResultTreeHasExactNamedPath(
    root: AbstractTestProxy,
    path: TestNodePath,
    allowTail: Boolean = false
  ): Unit =
    getExactNamePathFromResultTree(root, path, allowTail)

  protected def allAvailablePathsDetails(root: AbstractTestProxy, withStatus: Boolean, withTitlePrefix: Boolean): String = {
    val allPaths = allAvailablePaths(root)
    val pathStrings = allPaths.map(_.pathString)
    val details = pathsDetailsText(pathStrings)
    if (withTitlePrefix)
      s"available paths:\n$details"
    else
      details
  }

  protected def pathsDetails(paths: Seq[TestProxyNodePath], withTitlePrefix: Boolean = true): String = {
    val pathStrings = paths.map(_.pathString)
    val details = pathsDetailsText(pathStrings)
    if (withTitlePrefix)
      s"available paths:\n$details"
    else
      details
  }

  protected def pathsDetailsText(paths: Seq[String])(implicit d: DummyImplicit): String =
    paths.zipWithIndex.map { case (el, idx) => s"$idx: $el"}.mkString("\n")

  protected def allAvailablePaths(root: AbstractTestProxy): Seq[TestNodePathWithStatus] = {
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
      TestNodePathWithStatus(path, status)
    }
    paths.sortBy(_.pathString)
  }

  protected def assertResultTreeHasNotGotExactNamedPaths(
    root: AbstractTestProxy
  )(paths: Iterable[TestNodePath]): Unit =
    paths.foreach(assertResultTreeHasNotGotExactNamedPath(root, _))

  private def assertResultTreeHasNotGotExactNamedPath(
    root: AbstractTestProxy,
    path: TestNodePath,
    allowTail: Boolean = false
  ): Unit = {
    val result = getExactNamePathFromResultTreeOpt(root, path, allowTail)
    result match {
      case Some(_) =>
        fail(s"Test tree contains test with unexpected path '${path.pathString}'")
      case _       =>
    }
  }

  protected def assertResultTreeDoesNotHaveNodes(root: AbstractTestProxy, nodeNames: String*): Unit =
    if (nodeNames.contains(root.getName))
      fail(s"Test tree contains unexpected node '${root.getName}'")
    else if (!root.isLeaf) {
      val children = root.getChildren.asScala
      children.foreach(assertResultTreeDoesNotHaveNodes(_, nodeNames: _*))
    }

  protected def getExactNamePathFromResultTree(
    root: AbstractTestProxy,
    path: TestNodePath,
    allowTail: Boolean
  ): TestProxyNodePath = {
    val result = getExactNamePathFromResultTreeOpt(root, path, allowTail)
    result.getOrElse {
      // using ComparisonFailure to allow viewing results in "diff" view
      throw new ComparisonFailure(
        s"Test tree `${root.getName}` doesn't contain test with expected path `${path.pathString}`",
        pathsDetailsText(Seq(path.pathString)),
        allAvailablePathsDetails(root, withStatus = false, withTitlePrefix = false),
      )
    }
  }

  protected def getExactNamePathFromResultTreeOpt(
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
           |${allAvailablePathsDetails(root, withStatus = true, withTitlePrefix = true)}""".stripMargin
      fail(message).asInstanceOf[Nothing]
    }
    else
      matchingPaths.headOption
  }


  private def getAllMatchingPathsFromResultTree(
    node: AbstractTestProxy,
    nodeConditions: List[AbstractTestProxy => Boolean],
    allowTail: Boolean
  ): List[TestProxyNodePath] =
    nodeConditions match {
      case Nil                                    =>
        if (allowTail) List(TestProxyNodePath.empty)
        else Nil
      case condHead :: condTail if condHead(node) =>
        val children = node.getChildren.asScala.toList
        if (children.isEmpty && condTail.isEmpty)
          List(TestProxyNodePath(node))
        else {
          for {
            childNode  <- children
            childPaths <- getAllMatchingPathsFromResultTree(childNode, condTail, allowTail)
          } yield node +: childPaths
        }
      case _ =>
        Nil
    }
}

object IntegrationTestTreeViewAssertions {
  private val AlwaysTrue: AbstractTestProxy => Boolean = _ => true
}
