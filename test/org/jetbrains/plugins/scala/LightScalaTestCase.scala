package org.jetbrains.plugins.scala

import java.io.File

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.{Module, ModuleType, StdModuleTypes}
import com.intellij.openapi.roots.{ContentEntry, ModifiableRootModel, OrderRootType}
import com.intellij.openapi.vfs.{JarFileSystem, VfsUtil}
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.testFramework.{IdeaTestUtil, LightProjectDescriptor}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.util.TestUtils

/**
 * @author ilyas
 */

abstract class LightScalaTestCase extends LightCodeInsightFixtureTestCase {
  override def getProjectDescriptor = LightScalaTestCase.SCALA_DESCRIPTOR


  override def setUp() {
    super.setUp()
    val syntheticClasses = myFixture.getProject.getComponent(classOf[SyntheticClasses])
    if (!syntheticClasses.isClassesRegistered) {
      syntheticClasses.registerClasses()
    }
  }
}

object LightScalaTestCase {
  val SCALA_DESCRIPTOR = new LightProjectDescriptor {
    override def getModuleType : ModuleType[T] forSome {type T <: ModuleBuilder} = StdModuleTypes.JAVA
    override def getSdk = IdeaTestUtil.getMockJdk14
    override def configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      val modifiableModel = model.getModuleLibraryTable.createLibrary("SCALA").getModifiableModel
      val scalaLib = TestUtils.getScalaLibraryPath + "!/"
      val scalaJar = JarFileSystem.getInstance.refreshAndFindFileByPath(scalaLib)
      modifiableModel.addRoot(scalaJar, OrderRootType.CLASSES)
      val srcRoot = new File(TestUtils.getScalaLibrarySrc)
      modifiableModel.addRoot(VfsUtil.getUrlForLibraryRoot(srcRoot), OrderRootType.SOURCES)
      // do not forget to commit a model!
      modifiableModel.commit
    }
  }
}