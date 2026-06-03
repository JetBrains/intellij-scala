package org.jetbrains.plugins.scala.codeInspection.internal

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module
import com.intellij.openapi.module.{JavaModuleType, ModuleType}
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader, ScalaSDKLoader}
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.collection.mutable
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.chaining.scalaUtilChainingOps

abstract class ApiStatusInspectionTestBase extends JavaCodeInsightFixtureTestCase with ScalaSdkOwner {

  private[this] val myLoaders = mutable.Set.empty[LibraryLoader]

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(ScalaSDKLoader())

  override protected def setUp(): Unit = {

    def addModule(name: String): module.Module =
      PsiTestUtil.addModule(getProject, JavaModuleType.getModuleType.asInstanceOf[ModuleType[_ <: ModuleBuilder]],
        name, myFixture.getTempDirFixture.findOrCreateDir(name))

    super[JavaCodeInsightFixtureTestCase].setUp()

    val module1 = addModule("module1")
    val module2 = addModule("module2")

    ModuleRootModificationUtil.addDependency(module2, module1)

    myLoaders += IvyManagedLoader("org.jetbrains" % "annotations" % "24.0.1")
      .tap(_.init(module1, version))

    myFixture.enableInspections(classOf[ApiStatusInspection])
  }

  override protected def tearDown(): Unit = {
    for {
      module <- getProject.modules
      loader <- myLoaders
    } loader.clean(module)

    myLoaders.clear()

    super.tearDown()
  }

  protected def hasError(file: PsiFile): Boolean = {
    myFixture.openFileInEditor(file.getVirtualFile)
    myFixture.doHighlighting().asScala.exists { info =>
      info.getDescription != null &&
      info.getDescription.contains("is marked as internal")
    }
  }
}
