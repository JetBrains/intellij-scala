package org.jetbrains.plugins.scala
package compiler.references

import com.intellij.openapi.compiler.{CompilerMessage, CompilerMessageCategory}
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiClass
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.{CompilerTester, PsiTestUtil}
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{HeavyJDKLoader, LibraryLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.*
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.ui.AwaitTestUtils
import org.jetbrains.plugins.scala.util.{CompilerTestUtil, RevertableChange}
import org.junit.Assert.{assertNotSame, fail}

import scala.collection.mutable
import scala.compiletime.uninitialized
import scala.concurrent.Promise
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag
import scala.util.control.NonFatal

abstract class ScalaCompilerReferenceServiceFixture extends JavaCodeInsightFixtureTestCase with ScalaSdkOwner {
  override protected def supportedIn(version: ScalaVersion): Boolean = version  >= LatestScalaVersions.Scala_2_12

  override protected def librariesLoaders: Seq[LibraryLoader] =
    Seq(
      HeavyJDKLoader(),
      ScalaSDKLoader(includeScalaReflectIntoCompilerClasspath = true),
    )

  private val IndexReadyTimeout: FiniteDuration = 60.seconds

  protected var compiler: CompilerTester = uninitialized

  private val myLoaders = mutable.Set.empty[LibraryLoader]

  private val compilerConfig: RevertableChange = CompilerTestUtil.withEnabledCompileServer(false)

  protected def incrementalityType: IncrementalityType = IncrementalityType.SBT

  override def setUp(): Unit = {
    super.setUp()

    ScalaCompilerConfiguration.instanceIn(getProject).incrementalityType = incrementalityType

    try {
      compilerConfig.applyChange()
      // In production, ScalaCompilerReferenceService is created by an async post-startup activity.
      // If the first build starts before the service exists, it misses the `buildStarted` event,
      // `activeIndexingPhases` stays 0 and `onIndexingPhaseFinished` is never published.
      // Instantiate the service before any compilation can be triggered.
      ScalaCompilerReferenceService(getProject)
      setUpLibrariesFor(getModule)
      PsiTestUtil.addSourceRoot(getModule, myFixture.getTempDirFixture.findOrCreateDir("src"), true)
      val project = getProject
      compiler = new CompilerTester(project, project.modules.asJava, null)
    } catch {
      case NonFatal(e) => fail(e.getMessage)
    }
  }

  override def tearDown(): Unit =
    try {
      disposeLibraries(getModule)
      compiler.tearDown()
      compilerConfig.revertChange()
    } finally {
      compiler = null
      super.tearDown()
    }

  def setUpLibrariesFor(modules: Module*): Unit =
    for {
      module <- modules
      loader <- librariesLoaders
    } {
      loader.init(using module, version)
      myLoaders += loader
    }

  override protected def disposeLibraries(module: Module): Unit = {
    for {
      module <- getProject.modules
      loader <- myLoaders
    } loader.clean(using module)

    myLoaders.clear()
  }

  protected def buildProject(): Unit =
    compileAndWaitUntilIndexReady(compiler.rebuild())

  protected def buildModule(module: Module): Unit =
    compileAndWaitUntilIndexReady(compiler.compileModule(module))

  /**
   * Runs the given compilation, asserts that it produced no error messages and waits until the
   * compiler reference index has been updated for the finished compilation.
   *
   * The listener is subscribed BEFORE the compilation is triggered, because `onIndexingPhaseFinished`
   * may be delivered synchronously on the EDT while `CompilerTester` pumps events waiting for the
   * build to finish.
   *
   * The event is published from a job scheduled on a `BackgroundTaskQueue`
   * (see [[indices.CompilerReferenceIndexerScheduler]]), which needs EDT turns to start its tasks.
   * Test methods run on the EDT, so we must keep dispatching events while waiting instead of
   * blocking the thread, otherwise the queue is starved and the wait always times out.
   */
  private def compileAndWaitUntilIndexReady(doCompile: => java.util.List[CompilerMessage]): Unit = {
    val indexReady = Promise[Unit]()

    val connection = getProject.getMessageBus.connect()
    try {
      connection.subscribe(
        CompilerReferenceServiceStatusListener.topic,
        new CompilerReferenceServiceStatusListener {
          override def onIndexingPhaseFinished(success: Boolean): Unit =
            if (success) indexReady.trySuccess(())
            else indexReady.tryFailure(new AssertionError(
              "Compiler index update finished unsuccessfully (the index was invalidated or corrupted)"))
        })

      val compilerMessages = doCompile.asScala
      compilerMessages.foreach { message =>
        assertNotSame(message.getMessage, CompilerMessageCategory.ERROR, message.getCategory)
      }

      AwaitTestUtils.waitFutureDispatchingAllEdtEvents(indexReady.future, IndexReadyTimeout)
    } finally {
      connection.disconnect()
    }
  }

  protected def findClass[T](implicit tag: ClassTag[T]): PsiClass =
    myFixture.findClass(tag.runtimeClass.getCanonicalName)
}
