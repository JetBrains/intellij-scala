package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.util
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.{Condition, Lock, ReentrantLock}

import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiClass
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.{CompilerTester, PsiTestUtil}
import junit.framework.TestCase._
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.base.libraryLoaders.{LibraryLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.debugger.{ScalaSdkOwner, ScalaVersion, Scala_2_12}
import org.jetbrains.plugins.scala.util.CompileServerUtil
import org.junit.experimental.categories.Category

import scala.collection.JavaConverters._
import scala.concurrent.duration.DurationInt
import scala.reflect.ClassTag
import scala.util.control.NonFatal

@Category(Array(classOf[SlowTests]))
abstract class ScalaCompilerReferenceServiceFixture extends JavaCodeInsightFixtureTestCase with ScalaSdkOwner {
  override implicit val version: ScalaVersion                 = Scala_2_12
  override implicit protected def module: Module              = myModule
  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(ScalaSDKLoader(includeScalaReflect = true))

  private[this] var compiler: CompilerTester               = _
  private[this] val compilerIndexLock: Lock                = new ReentrantLock()
  private[this] val indexReady: Condition                  = compilerIndexLock.newCondition()
  @volatile private[this] var indexReadyPredicate: Boolean = false

  protected lazy val service = ScalaCompilerReferenceService.getInstance(getProject)
  
  override def setUp(): Unit = {
    super.setUp()
    try {
      setUpLibraries()
      PsiTestUtil.addSourceRoot(myModule, myFixture.getTempDirFixture.findOrCreateDir("src"), true)
      compiler = new CompilerTester(getProject, util.Collections.singletonList(myModule))
    } catch {
      case NonFatal(e) => fail(e.getMessage)
    }
  }

  override def tearDown(): Unit =
    try {
      disposeLibraries()
      CompileServerUtil.stopAndWait(10.seconds)
      compiler.tearDown()
    } finally {
      compiler = null
      super.tearDown()
    }

  protected def buildProject(): Unit = {
    getProject.getMessageBus
      .connect(getProject)
      .subscribe(CompilerReferenceIndexingTopics.indexingStatus, new CompilerReferenceIndexingStatusListener {
        override def onIndexingFinished(failure: Option[IndexerParsingFailure]): Unit = withLock(compilerIndexLock) {
          indexReadyPredicate = true
          indexReady.signal()
        }
      })

    compiler
      .make
      .asScala
      .foreach(m => assertNotSame(m.getMessage, CompilerMessageCategory.ERROR, m.getCategory))

    withLock(compilerIndexLock) {
      while (!indexReadyPredicate) indexReady.await(10, TimeUnit.SECONDS)
      indexReadyPredicate = false
    }
  }

  protected def findClass[T](implicit tag: ClassTag[T]): PsiClass =
    myFixture.findClass(tag.runtimeClass.getCanonicalName)
}
