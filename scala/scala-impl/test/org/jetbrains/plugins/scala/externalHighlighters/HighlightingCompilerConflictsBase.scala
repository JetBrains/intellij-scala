package org.jetbrains.plugins.scala.externalHighlighters

import java.io.File

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.EdtTestUtil
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compilation.CompilerTestUtil.runWithErrorsFromCompiler
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener}
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase
import org.junit.Assert.{assertEquals, assertTrue}
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.Ignore
import org.junit.runner.RunWith

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Promise}

/**
 * Checks if there are no conflicts between usual compilation and
 * compilation for compiler-based highlighting.
 *
 * @see SCL-17676
 */
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_0
))
@RunWith(classOf[MultipleScalaVersionsRunner])
abstract class HighlightingCompilerConflictsBase(compileServerLanguageLevel: LanguageLevel,
                                                 buildProcessLanguageLevel: LanguageLevel)
  extends ScalaCompilerTestBase {

  override protected def useCompileServer: Boolean = true

  override protected def compileServerJdk: Sdk = SmartJDKLoader.getOrCreateJDK(compileServerLanguageLevel)

  override protected def buildProcessJdk: Sdk = SmartJDKLoader.getOrCreateJDK(buildProcessLanguageLevel)

  override def runInDispatchThread: Boolean = false

  override def setUp(): Unit = EdtTestUtil.runInEdtAndWait { () =>
    super.setUp()
  }

  def testNoConflictsBetweenJpsCompilerAndUsualCompilation(): Unit = runWithErrorsFromCompiler(getProject) {
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

    assertEquals("file was recompiled, but it shouldn't",
      targetFileTimestampBefore, targetFileTimestampAfter)
  }

  private def getTargetFileTimestamp(className: String): Long = {
    val targetFileName = s"$className.class"
    val optionResult =
      Option(CompilerModuleExtension.getInstance(getModule).getCompilerOutputPath)
        .map(_.getCanonicalPath)
        .map(new File(_))
        .flatMap { targetDir =>
          targetDir.listFiles((_, name) => name == targetFileName)
            .find(_.getName == targetFileName)
            .map(_.lastModified())
        }

    assertTrue(s"$targetFileName doesn't exist", optionResult.isDefined)
    optionResult.get
  }

  private def compileProjectWithJpsCompiler(): Unit = {
    val promise = Promise[Unit]()
    getProject.getMessageBus.connect().subscribe(CompilerEventListener.topic, new CompilerEventListener {
      override def eventReceived(event: CompilerEvent): Unit = event match {
        case CompilerEvent.CompilationFinished(_, _, _) => promise.success(())
        case _ => ()
      }
    })
    CompilerHighlightingService.get(getProject).triggerIncrementalCompilation(delayedProgressShow = false)
    Await.result(promise.future, 60.seconds)
  }
}

@Ignore
class HighlightingCompilerConflictsDifferentJdksTest extends HighlightingCompilerConflictsBase(
  compileServerLanguageLevel = LanguageLevel.JDK_1_8,
  buildProcessLanguageLevel = LanguageLevel.JDK_11,
)

class HighlightingCompilerConflictsSameJdksTest extends HighlightingCompilerConflictsBase(
  compileServerLanguageLevel = LanguageLevel.JDK_11,
  buildProcessLanguageLevel = LanguageLevel.JDK_11,
)