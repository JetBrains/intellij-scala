package org.jetbrains.sbt.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkVersion, ProjectJdkTable, Sdk}
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.testFramework.{HeavyPlatformTestCase, IdeaTestUtil}
import com.intellij.util.lang.JavaVersion
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.sbt.project.template.wizard.JdkSbtCompatibilityChecker
import org.jetbrains.sbt.SbtVersion
import org.junit.Assert.{assertEquals, assertFalse, assertSame, assertTrue, fail}

import scala.jdk.CollectionConverters.CollectionHasAsScala

class SbtProcessJdkGuesserTest extends HeavyPlatformTestCase {

  override def runInDispatchThread(): Boolean = false

  def testFindJdkWithSuitableVersion_SelectsLowestCompatibleJdkAndReturnsAllJdksSorted(): Unit = {
    val jdk8 = mockJdk(8)
    val jdk17 = mockJdk(17)
    val jdk21 = mockJdk(21)
    val sbtVersion = SbtVersion("1.9.0")

    withJdksInTable(jdk21, jdk8, jdk17) { jdkTable =>
      val candidate = SbtProcessJdkGuesser.findJdkWithSuitableVersion(jdkTable, sbtVersion)

      assertSdkVersion(jdk8, JavaSdkVersion.JDK_1_8)
      assertSdkVersion(jdk17, JavaSdkVersion.JDK_17)
      assertSdkVersion(jdk21, JavaSdkVersion.JDK_21)
      assertSbtJdkCompatible(jdk8, sbtVersion)
      assertSbtJdkCompatible(jdk17, sbtVersion)
      assertSbtJdkCompatible(jdk21, sbtVersion)
      assertSame(jdk8, candidate.sdk.orNull)
      assertEquals(Seq(jdk8, jdk17, jdk21), candidate.allSdkSorted)
    }
  }

  def testFindJdkWithSuitableVersion_FallsBackToJdkInDefaultRangeWhenNoSbtCompatibleJdkExists(): Unit = {
    val jdk8 = mockJdk(8)
    val jdk11 = mockJdk(11)
    val sbtVersion = SbtVersion("2.0.0-RC9")

    withJdksInTable(jdk11, jdk8) { jdkTable =>
      val candidate = SbtProcessJdkGuesser.findJdkWithSuitableVersion(jdkTable, sbtVersion)

      assertSdkVersion(jdk8, JavaSdkVersion.JDK_1_8)
      assertSdkVersion(jdk11, JavaSdkVersion.JDK_11)
      assertSbtJdkIncompatible(jdk8, sbtVersion)
      assertSbtJdkIncompatible(jdk11, sbtVersion)
      assertSame(jdk8, candidate.sdk.orNull)
      assertEquals(Seq(jdk8, jdk11), candidate.allSdkSorted)
    }
  }

  def testFindJdkWithSuitableVersion_ReturnsNoCandidateWhenJdkTableIsEmpty(): Unit = {
    val jdkTable = projectJdkTable
    assertJdkTableIsEmpty(jdkTable)

    val candidate = SbtProcessJdkGuesser.findJdkWithSuitableVersion(jdkTable, SbtVersion("1.9.0"))

    assertEquals(None, candidate.sdk)
    assertEquals(Seq.empty, candidate.allSdkSorted)
  }

  def testFindAllExistingJavaPaths_ReturnsNoPathsWhenJavaDetectorIsDisabled(): Unit = {
    disableJavaDetector()

    val paths = SbtProcessJdkGuesser.findAllExistingJavaPaths(JavaSdk.getInstance, LocalEelDescriptor.INSTANCE)

    assertFalse(Registry.is("java.detector.enabled", true))
    assertEquals(Seq.empty, paths)
  }

  def testPreconfigureJdkForSbt_DoesNotAddJdkWhenJavaDetectorIsDisabled(): Unit = {
    disableJavaDetector()
    val jdkTable = projectJdkTable
    assertJdkTableIsEmpty(jdkTable)

    ApplicationManager.getApplication.invokeAndWait { () =>
      SbtProcessJdkGuesser.preconfigureJdkForSbt(getProject, jdkTable, SbtVersion("1.9.0"))
    }

    assertFalse(Registry.is("java.detector.enabled", true))
    assertJdkTableIsEmpty(jdkTable)
  }

  private def withJdksInTable[T](jdks: Sdk*)(body: ProjectJdkTable => T): T = {
    val jdkTable = projectJdkTable
    assertJdkTableIsEmpty(jdkTable)

    inWriteAction {
      jdks.foreach(jdkTable.addJdk)
    }
    try body(jdkTable)
    finally {
      inWriteAction {
        jdks.foreach(jdkTable.removeJdk)
      }
    }
  }

  private def assertJdkTableIsEmpty(jdkTable: ProjectJdkTable): Unit = {
    val availableJdks = jdkTable.getSdksOfType(JavaSdk.getInstance).asScala.toSeq
    if (availableJdks.nonEmpty) {
      fail(s"Project-level JDK table should be empty before the test setup. Available JDKs: ${availableJdks.mkString(", ")}")
    }
  }

  private def assertSdkVersion(sdk: Sdk, expectedVersion: JavaSdkVersion): Unit =
    assertEquals(expectedVersion, JavaSdk.getInstance.getVersion(sdk))

  private def assertSbtJdkCompatible(sdk: Sdk, sbtVersion: SbtVersion): Unit =
    assertTrue(isSbtJdkCompatible(sdk, sbtVersion))

  private def assertSbtJdkIncompatible(sdk: Sdk, sbtVersion: SbtVersion): Unit =
    assertFalse(isSbtJdkCompatible(sdk, sbtVersion))

  private def isSbtJdkCompatible(sdk: Sdk, sbtVersion: SbtVersion): Boolean = {
    val javaVersion = JavaSdk.getInstance.getVersion(sdk).getMaxLanguageLevel.toJavaVersion
    JdkSbtCompatibilityChecker.isSbtAndJdkVersionCompatible(javaVersion, sbtVersion, strict = true)
  }

  private def disableJavaDetector(): Unit =
    Registry.get("java.detector.enabled").setValue(false, getTestRootDisposable)

  private def projectJdkTable: ProjectJdkTable =
    ProjectJdkTable.getInstance(getProject)

  private def mockJdk(feature: Int): Sdk =
    IdeaTestUtil.getMockJdk(JavaVersion.compose(feature))
}
