package org.jetbrains.plugins.scala.internal.bundle

import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase.fail
import org.jetbrains.plugins.scala.extensions.PathExt
import org.junit.Test

import java.nio.file.Path

trait ScalaBundleCoverageTestBase  {
  protected def root: Path

  protected def ignoreRoots: Seq[Path]

  protected def definedModuleInfos: Seq[ScalaBundleSorting.ModuleWithBundleInfo]

  @Test
  def testAllBundlesAreCovered(): Unit = {
    val coveredBundles = definedModuleInfos.map(_.bundleAbsolutePath)
    val allBundles = messageBundlePaths

    val nonexistent = coveredBundles.diff(allBundles)
    val notCovered = allBundles.diff(coveredBundles)

    if (nonexistent.nonEmpty || notCovered.nonEmpty) {
      val message = new StringBuilder().append("Scala message bundles coverage issue.")
      reportNonexistentBundles(nonexistent, message)
      reportNotCoveredBundles(notCovered, message)
      fail(message.result())
    }
  }

  private def messageBundlePaths: Seq[Path] = for {
    bundleFile <- ScalaPluginResourcesUtils.findAllBundleFiles(root)
    moduleRoot = bundleFile.getParent
    if !ignoreRoots.exists(r => moduleRoot.toString.startsWith(r.toString))
  } yield bundleFile

  private def reportNonexistentBundles(bundles: Seq[Path], messageBuffer: StringBuilder): Unit =
    reportBundles(bundles, messageBuffer, "Nonexistent/duplicated message bundle entries:")

  private def reportNotCoveredBundles(bundles: Seq[Path], messageBuffer: StringBuilder): Unit =
    reportBundles(bundles, messageBuffer, "Not covered message bundles:")

  private def reportBundles(messageBundles: Seq[Path], messageBuffer: StringBuilder, header: String): Unit =
    if (messageBundles.nonEmpty) {
      messageBuffer.append(System.lineSeparator())
        .append(header)
        .append(System.lineSeparator())
      messageBundles.foreach { path =>
        messageBuffer.append("- ")
          .append(path.toString.stripPrefix(root.toString))
          .append(System.lineSeparator())
      }
    }
}

final class ScalaBundleCoverageTest extends ScalaBundleCoverageTestBase {
  override val root: Path = ScalaBundleSorting.communityDir
  override val ignoreRoots: Seq[Path] = Seq(ScalaBundleSorting.integrationDir / "packagesearch")
  override val definedModuleInfos: Seq[ScalaBundleSorting.ModuleWithBundleInfo] = ScalaBundleSorting.allModuleInfos
}
