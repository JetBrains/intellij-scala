package org.jetbrains.plugins.scala.textAnalysis.grazie

import com.intellij.grazie.GrazieConfig.State.Processing
import com.intellij.grazie.GrazieTestBase
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.{EdtTestUtil, LightProjectDescriptor}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{LibraryLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.TestUtils

import java.nio.file.Path

abstract class GrazieTest_WithScalaSdkAndJdk
  extends GrazieTestBase
    with ScalaSdkOwner  {

  // Setup Scala SDK
  override def librariesLoaders: Seq[LibraryLoader] = Seq(ScalaSDKLoader())

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2

  // Set up JDK (some test data refers to definitions from JDK, like "java.lant.System")
  // NOTE: in a similar test com.intellij.grazie.ide.language.JavaSupportTest it uses LightJavaCodeInsightFixtureTestCase.JAVA_LATEST.
  // However, if we use it here, the JDK definitions are not visible for some reason.
  // But JAVA_LATEST_WITH_LATEST_JDK works fine
  override def getProjectDescriptor: LightProjectDescriptor =
    LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK

  override def getTestDataPath: String =
    Path.of(TestUtils.getTestDataPath + "/../../integration/textAnalysis/testData").toCanonicalPath.toString

  override def setUp(): Unit = {
    super.setUp()

    setUpLibraries(getModule)

    // Make sure that Grazie doesn't access Cloud/Server in unit tests.
    // This makes tests more reproducible in different environments (e.g. local and TeamCity).
    // This should be the default value, but for some reason it's not yet (even though Peter Gromov confirmed that it should).
    GrazieProcessingTestUtils.setProcessingMode(Processing.Local)
  }

  override def tearDown(): Unit = {
    EdtTestUtil.runInEdtAndWait { () =>
      disposeLibraries(getModule)
    }

    super.tearDown()
  }
}
