package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.{Condition, Lock, ReentrantLock}

import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiClass
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.{CompilerTester, PsiTestUtil}
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{HeavyJDKLoader, LibraryLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.debugger.{ScalaCompilerTestBase, ScalaVersion, Scala_2_12}
import org.jetbrains.plugins.scala.project._
import org.junit.Assert.{assertNotSame, fail}
import org.junit.experimental.categories.Category

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.reflect.ClassTag
import scala.util.control.NonFatal

@Category(Array(classOf[SlowTests]))
abstract class ScalaCompilerReferenceServiceFixture extends JavaCodeInsightFixtureTestCase with ScalaSdkOwner {
  override implicit val version: ScalaVersion                 = Scala_2_12
  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(HeavyJDKLoader(), ScalaSDKLoader(includeScalaReflect = true))

  private[this] val compilerIndexLock: Lock                = new ReentrantLock()
  private[this] val indexReady: Condition                  = compilerIndexLock.newCondition()
  @volatile private[this] var indexReadyPredicate: Boolean = false

  protected var compiler: CompilerTester = _

  private[this] val myLoaders = mutable.Set.empty[LibraryLoader]

  override def setUp(): Unit = {
    super.setUp()
    try {
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
      ScalaCompilerTestBase.stopAndWait()
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
    getProject.getMessageBus
      .connect(getProject)
      .subscribe(CompilerReferenceServiceStatusListener.topic, new CompilerReferenceServiceStatusListener {
        override def onIndexingPhaseFinished(success: Boolean): Unit = compilerIndexLock.locked {
          indexReadyPredicate = true
          indexReady.signal()
        }
      })

    compiler
      .rebuild
      .asScala
      .foreach(m => assertNotSame(m.getMessage, CompilerMessageCategory.ERROR, m.getCategory))

    compilerIndexLock.locked {
      indexReady.await(30, TimeUnit.SECONDS)
      if (!indexReadyPredicate) fail("Failed to updated compiler index.")
      indexReadyPredicate = false
    }
  }

  protected def findClass[T](implicit tag: ClassTag[T]): PsiClass =
    myFixture.findClass(tag.runtimeClass.getCanonicalName)
}
