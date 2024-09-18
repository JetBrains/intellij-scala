package org.jetbrains.plugins.scala
package base

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.{EditorTestUtil, IdeaTestUtil, IndexingTestUtil}
import com.intellij.util.lang.JavaVersion
import org.jetbrains.plugins.scala.base.libraryLoaders.{LibraryLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.extensions.inWriteAction

abstract class ScalaFixtureTestCase extends CodeInsightFixtureTestCase with ScalaSdkOwner {

  protected val CARET = EditorTestUtil.CARET_TAG

  protected val includeCompilerAsLibrary: Boolean = false

  protected final implicit def projectContext: Project = getProject

  protected lazy val jdk: Sdk = IdeaTestUtil.getMockJdk(JavaVersion.compose(17))

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(
    ScalaSDKLoader(includeScalaCompilerIntoLibraryClasspath = includeCompilerAsLibrary),
    new LibraryLoader {
      override def init(implicit module: Module, version: ScalaVersion): Unit = {
        val jdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx
        inWriteAction(jdkTable.addJdk(jdk))
        ModuleRootModificationUtil.setModuleSdk(module, jdk)
      }

      override def clean(implicit module: Module): Unit = {
        ModuleRootModificationUtil.setModuleSdk(module, null)
        val jdkTable = JavaAwareProjectJdkTableImpl.getInstanceEx
        inWriteAction(jdkTable.removeJdk(jdk))
      }
    }
  )

  override protected def setUp(): Unit = {
    super.setUp()
    setUpLibraries(myModule)
    IndexingTestUtil.waitUntilIndexesAreReady(getProject)
    Registry.get("ast.loading.filter").setValue(true, getTestRootDisposable)
  }

  override def tearDown(): Unit = {
    disposeLibraries(myModule)
    super.tearDown()
  }
}