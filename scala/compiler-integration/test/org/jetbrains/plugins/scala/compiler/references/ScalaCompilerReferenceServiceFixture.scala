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
import org.jetbrains.plugins.scala.extensions.LockExtensions
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.util.{CompilerTestUtil, RevertableChange}
import org.junit.Assert.{assertNotSame, fail}
import org.junit.experimental.categories.Category

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.{Condition, Lock, ReentrantLock}
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.util.control.NonFatal

@Category(Array(classOf[SlowTests]))
abstract class ScalaCompilerReferenceServiceFixture extends JavaCodeInsightFixtureTestCase with ScalaSdkOwner {
  override protected def supportedIn(version: ScalaVersion): Boolean = version  >= LatestScalaVersions.Scala_2_12

  override protected def librariesLoaders: Seq[LibraryLoader] =
    Seq(
      HeavyJDKLoader(),
      ScalaSDKLoader(includeScalaReflectIntoCompilerClasspath = true),
    )

  private[this] val compilerIndexLock: Lock                = new ReentrantLock()
  private[this] val indexReady: Condition                  = compilerIndexLock.newCondition()
  @volatile private[this] var indexReadyPredicate: Boolean = false

  protected var compiler: CompilerTester = _

  private[this] val myLoaders = mutable.Set.empty[LibraryLoader]

  private val compilerConfig: RevertableChange = CompilerTestUtil.withEnabledCompileServer(false)

  protected def incrementalityType: IncrementalityType = IncrementalityType.SBT

  override def setUp(): Unit = {
    super.setUp()

    ScalaCompilerConfiguration.instanceIn(getProject).incrementalityType = incrementalityType

    try {
      compilerConfig.applyChange()
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
      loader.init(module, version)
      myLoaders += loader
    }

  override protected def disposeLibraries(implicit module: Module): Unit = {
    for {
      module <- getProject.modules
      loader <- myLoaders
    } loader.clean(module)

    myLoaders.clear()
  }

  protected def buildProject(): Unit = {
    val messageBus = getProject.getMessageBus
    val messageBusConnection = messageBus.connect(getProject.unloadAwareDisposable)
    messageBusConnection.subscribe(
      CompilerReferenceServiceStatusListener.topic,
      new CompilerReferenceServiceStatusListener {
        override def onIndexingPhaseFinished(success: Boolean): Unit = compilerIndexLock.withLock {
          indexReadyPredicate = true
          indexReady.signalAll()
        }
      })

    val compilerMessages: mutable.Seq[CompilerMessage] = compiler.rebuild.asScala
    compilerMessages.foreach { message =>
      assertNotSame(message.getMessage, CompilerMessageCategory.ERROR, message.getCategory)
    }

    compilerIndexLock.withLock {
      //onIndexingPhaseFinished can be called in the same thread in com.intellij.testFramework.CompilerTester.rebuild
      if (!indexReadyPredicate) {
        val timeout = !indexReady.await(30, TimeUnit.SECONDS)
        if (timeout) {
          fail("Failed to updated compiler index: timeout reached")
        }
      }

      if (!indexReadyPredicate) {
        fail("Failed to updated compiler index: indexReadyPredicate is still false")
      }

      indexReadyPredicate = false
    }
  }

  protected def findClass[T](implicit tag: ClassTag[T]): PsiClass =
    myFixture.findClass(tag.runtimeClass.getCanonicalName)
}
