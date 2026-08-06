package org.jetbrains.plugins.scala.codeInspection.relativeImports

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.roots.ModuleRootModificationUtil
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}

class AbsoluteImportInspectionTest extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[AbsoluteImportInspection]

  override protected def description: String = ScalaInspectionBundle.message("absolute.import.detected")

  def test_package_prefix(): Unit = {
    ModuleRootModificationUtil.updateModel(myFixture.getModule, model => {
      model.getContentEntries.flatMap(_.getSourceFolders).foreach(_.setPackagePrefix("org.example"))
    })

    checkTextHasError(s"package org.example\nimport ${START}org.example.${END}_")
  }
}
