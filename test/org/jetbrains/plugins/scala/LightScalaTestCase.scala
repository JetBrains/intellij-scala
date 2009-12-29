package org.jetbrains.plugins.scala

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import lang.psi.impl.toplevel.synthetic.SyntheticClasses
import util.TestUtils
import com.intellij.openapi.roots.{OrderRootType, ContentEntry, ModifiableRootModel}
import com.intellij.openapi.module.{ModuleType, Module, StdModuleTypes}
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.vfs.{VfsUtil, JarFileSystem}
import java.io.File

/**
 * @author ilyas
 */

class LightScalaTestCase extends LightCodeInsightFixtureTestCase {
  override def getProjectDescriptor = LightScalaTestCase.SCALA_DESCRIPTOR


  override def setUp {
    super.setUp
    myFixture.getProject.getComponent(classOf[SyntheticClasses]).registerClasses
  }
}

object LightScalaTestCase {
  val SCALA_DESCRIPTOR = new LightProjectDescriptor {
    def getModuleType : ModuleType[T] forSome {type T <: ModuleBuilder} = StdModuleTypes.JAVA
    def getSdk = JavaSdkImpl.getMockJdk15("java 1.5")
    def configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      val modifiableModel = model.getModuleLibraryTable.createLibrary("SCALA").getModifiableModel
      val scalaLib = TestUtils.getMockScalaLib + "!/"
      val scalaJar = JarFileSystem.getInstance.refreshAndFindFileByPath(scalaLib)
      modifiableModel.addRoot(scalaJar, OrderRootType.CLASSES)
      val srcRoot = new File(TestUtils.getMockScalaSrc)
      modifiableModel.addRoot(VfsUtil.getUrlForLibraryRoot(srcRoot), OrderRootType.SOURCES)
      // do not forget to commit a model!
      modifiableModel.commit
    }
  }
}