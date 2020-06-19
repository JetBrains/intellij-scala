package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.EdtTestUtil
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener}
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.util.matchers.HamcrestMatchers.lessThan
import org.junit.Assert.{assertThat, assertTrue}
import org.hamcrest.CoreMatchers.equalTo
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Promise}

/**
 * Checks if there are no conflicts between usual compilation and
 * compilation for compiler-based highlighting.
 * @see SCL-17676
 */
@RunWith(classOf[MultipleScalaVersionsRunner])
abstract class JpsCompilerConflictsBase(compileServerLanguageLevel: LanguageLevel,
                                        buildProcessLanguageLevel: LanguageLevel)
  extends ScalaCompilerTestBase {

  override protected def useCompileServer: Boolean = true
  override protected def compileServerJdk: Sdk = SmartJDKLoader.getOrCreateJDK(compileServerLanguageLevel)
  override protected def buildProcessJdk: Sdk = SmartJDKLoader.getOrCreateJDK(buildProcessLanguageLevel)

  override def runInDispatchThread: Boolean = false

  // TODO remove commented code
//  override protected def supportedIn(version: ScalaVersion): Boolean =
//    version == LatestScalaVersions.Scala_2_13

  override def setUp(): Unit = EdtTestUtil.runInEdtAndWait { () =>
    super.setUp()
  }

  def assertion(timestampBefore: Long, timestampAfter: Long): Unit

  def testNoConflictsBetweenJpsCompilerAndUsualCompilation(): Unit = {
    val className = "MyClass"
    val sourceFile = addFileToProjectSources(
      s"$className.scala",
      s"class $className"
    )
    compiler.make().assertNoProblems(allowWarnings = true)

    compiler.touch(sourceFile)
    compileProjectWithJpsCompiler()
    val targetFileTimestampBefore = getTargetFileTimestamp(className)

    compiler.make().assertNoProblems(allowWarnings = true)
    val targetFileTimestampAfter = getTargetFileTimestamp(className)

    assertion(targetFileTimestampBefore, targetFileTimestampAfter)
  }

  private def getTargetFileTimestamp(className: String): Long = {
    val targetFileName = s"$className.class"
    val optionResult = Option(CompilerModuleExtension.getInstance(getModule).getCompilerOutputPath)
      .flatMap { targetDir =>
        VfsUtil.markDirtyAndRefresh(false, true, true, targetDir)
        targetDir.getChildren
          .find(_.getName == targetFileName)
          .map(_.getTimeStamp)
      }
    assertTrue(s"$targetFileName doesn't exist", optionResult.isDefined)
    optionResult.get
  }

  private def compileProjectWithJpsCompiler(): Unit = {
    val promise = Promise[Unit]
    getProject.getMessageBus.connect().subscribe(CompilerEventListener.topic, new CompilerEventListener {
      override def eventReceived(event: CompilerEvent): Unit = event match {
        case CompilerEvent.CompilationFinished(_, _) => promise.success(())
        case _ => ()
      }
    })
    JpsCompiler.get(getProject).rescheduleCompilation(
      testScopeOnly = false,
      delayedProgressShow = false,
      forceCompileModule = None
    )
    Await.result(promise.future, 60.seconds)
  }
}

/**
 * Demonstrates the bug that fixed in [[SetSameJdkToBuildProcessAsInCompileServer]].
 * But the fix works only in production. It doesn't work in tests.
 */
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13
))
class JpsCompilerDifferentJdksTest extends JpsCompilerConflictsBase(
  compileServerLanguageLevel = LanguageLevel.JDK_1_8,
  buildProcessLanguageLevel = LanguageLevel.JDK_11,
) {

  // TODO it's better to write the valid test-case (assertion should check equalTo instead lessThan).
  // TODO why current test fails for dotty?

  override def assertion(timestampBefore: Long, timestampAfter: Long): Unit =
    assertThat(timestampBefore, lessThan(timestampAfter))
}

/**
 * The correct behaviour.
 */
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_0
))
class JpsCompilerSameJdksTest extends JpsCompilerConflictsBase(
  compileServerLanguageLevel = LanguageLevel.JDK_11,
  buildProcessLanguageLevel = LanguageLevel.JDK_11,
) {
  override def assertion(timestampBefore: Long, timestampAfter: Long): Unit =
    assertThat(timestampBefore, equalTo(timestampAfter))
}