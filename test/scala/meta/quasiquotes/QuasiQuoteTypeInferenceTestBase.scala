package scala.meta.quasiquotes

import com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

import scala.meta.ScalametaUtils

abstract class QuasiQuoteTypeInferenceTestBase extends TypeInferenceTestBase with ScalametaUtils {

  override protected def setUp() = {
    super.setUp()
    addAllMetaLibraries(getModuleAdapter)
    VirtualFilePointerManager.getInstance.asInstanceOf[VirtualFilePointerManagerImpl].storePointers()
  }

  override protected def doTest(fileText: String): Unit = super.doTest("import scala.meta._\n"+fileText)

}
