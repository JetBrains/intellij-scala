package org.jetbrains.plugins.scala.internal.bundle

import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase.fail

import java.io.File
import java.nio.file.{Path, Paths}

trait ScalaBundleCoverageTestBase extends UsefulTestCase {
  protected def root: String

  protected def ignoreRoots: Seq[String]

  protected def definedModuleInfos: Seq[ScalaBundleSorting.ModuleWithBundleInfo]

  def testAllBundlesAreCovered(): Unit = {
    val coveredBundles = definedModuleInfos.map(info => normalizedAbsolutePath(info.bundleAbsolutePath))
    val allBundles = messageBundlePathsIterator.toSeq

    val nonexistent = coveredBundles.diff(allBundles)
    val notCovered = allBundles.diff(coveredBundles)

    if (nonexistent.nonEmpty || notCovered.nonEmpty) {
      val message = new StringBuilder().append("Scala message bundles coverage issue.")
      reportNonexistentBundles(nonexistent, message)
      reportNotCoveredBundles(notCovered, message)
      fail(message.result())
    }
  }

  private def messageBundlePathsIterator: Iterator[String] = for {
    file <- ScalaBundleSorting.allFilesIn(root)
    if isMessageBundleFile(file)
    absolutePath = normalizedAbsolutePath(file.toPath)
    if !ignoreRoots.exists(absolutePath.startsWith)
  } yield absolutePath

  // resolve paths like /foo/../bar/baz to /foo/bar/baz
  private def normalizedAbsolutePath(path: String): String = normalizedAbsolutePath(Paths.get(path))

  private def normalizedAbsolutePath(path: Path): String = path.toAbsolutePath.normalize().toString

  private def isMessageBundleFile(file: File): Boolean = file.isFile &&
    file.getName.endsWith("Bundle.properties") &&
    isDirectoryWithName(file.getParentFile, "messages") &&
    isDirectoryWithName(file.getParentFile.getParentFile, "resources")

  private def isDirectoryWithName(file: File, expectedName: String): Boolean =
    file != null && file.isDirectory && file.getName == expectedName

  private def reportNonexistentBundles(bundles: Seq[String], messageBuffer: StringBuilder): Unit =
    reportBundles(bundles, messageBuffer, "Nonexistent/duplicated message bundle entries:")

  private def reportNotCoveredBundles(bundles: Seq[String], messageBuffer: StringBuilder): Unit =
    reportBundles(bundles, messageBuffer, "Not covered message bundles:")

  private def reportBundles(messageBundles: Seq[String], messageBuffer: StringBuilder, header: String): Unit =
    if (messageBundles.nonEmpty) {
      messageBuffer.append(System.lineSeparator())
        .append(header)
        .append(System.lineSeparator())
      messageBundles.foreach { path =>
        messageBuffer.append("- ")
          .append(path.stripPrefix(root))
          .append(System.lineSeparator())
      }
    }
}

final class ScalaBundleCoverageTest extends ScalaBundleCoverageTestBase {
  override val root = ScalaBundleSorting.communityDir
  override val ignoreRoots = Nil
  override val definedModuleInfos = ScalaBundleSorting.allModuleInfos
}
