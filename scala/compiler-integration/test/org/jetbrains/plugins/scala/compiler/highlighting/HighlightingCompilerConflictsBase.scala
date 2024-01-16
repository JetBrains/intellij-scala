package org.jetbrains.plugins.scala
package compiler.highlighting

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiManager
import com.intellij.testFramework.EdtTestUtil
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener, ScalaCompilerTestBase}
import org.jetbrains.plugins.scala.extensions.{inReadAction, inWriteAction}
import org.jetbrains.plugins.scala.util.CompilerTestUtil.runWithErrorsFromCompiler
import org.jetbrains.plugins.scala.util.ScalaUtil
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.runner.RunWith

import java.io.File
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
  TestScalaVersion.Scala_3_Latest
))
@RunWith(classOf[MultipleScalaVersionsRunner])
abstract class HighlightingCompilerConflictsBase(compileServerLanguageLevel: LanguageLevel,
                                                 buildProcessLanguageLevel: LanguageLevel)
  extends ScalaCompilerTestBase {

  override protected def useCompileServer: Boolean = true

  override val testProjectJdkVersion: LanguageLevel = compileServerLanguageLevel

  override protected lazy val compileServerJdk: Sdk = createDisposableJdk(compileServerLanguageLevel)

  override protected lazy val buildProcessJdk: Sdk = createDisposableJdk(buildProcessLanguageLevel)

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
    compileProjectWithJpsCompiler(sourceFile)
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

  private def compileProjectWithJpsCompiler(virtualFile: VirtualFile): Unit = {
    val promise = Promise[Unit]()
    val project = getProject
    project.getMessageBus.connect().subscribe(CompilerEventListener.topic, new CompilerEventListener {
      override def eventReceived(event: CompilerEvent): Unit = event match {
        case CompilerEvent.CompilationFinished(_, _, _) => promise.success(())
        case _ => ()
      }
    })

    val module = ScalaUtil.getModuleForFile(virtualFile)(project)
    val document = inReadAction(FileDocumentManager.getInstance().getDocument(virtualFile))
    val psiFile = inReadAction(PsiManager.getInstance(project).findFile(virtualFile))
    module.foreach { m =>
      CompilerHighlightingService.get(project)
        .triggerIncrementalCompilation(virtualFile, m, document, psiFile, "manual trigger from tests")
    }
    Await.result(promise.future, 60.seconds)
  }

  private def createDisposableJdk(languageLevel: LanguageLevel): Sdk = {
    val jdk = SmartJDKLoader.getOrCreateJDK(languageLevel)
    Disposer.register(getTestRootDisposable, () => {
      val table = JavaAwareProjectJdkTableImpl.getInstanceEx
      inWriteAction(table.removeJdk(jdk))
    })
    jdk
  }
}

class HighlightingCompilerConflictsDifferentJdksTest extends HighlightingCompilerConflictsBase(
  compileServerLanguageLevel = LanguageLevel.JDK_11, // CBH runs the JPS code inside the SCS which demands at least JDK 11
  buildProcessLanguageLevel = LanguageLevel.JDK_17
)

class HighlightingCompilerConflictsSameJdksTest extends HighlightingCompilerConflictsBase(
  compileServerLanguageLevel = LanguageLevel.JDK_17,
  buildProcessLanguageLevel = LanguageLevel.JDK_17
)
