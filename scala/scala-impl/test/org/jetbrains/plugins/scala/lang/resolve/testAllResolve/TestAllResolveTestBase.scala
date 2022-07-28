package org.jetbrains.plugins.scala
package lang.resolve.testAllResolve

import _root_.org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaRecursiveElementVisitor}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.junit.experimental.categories.Category

import java.io.File
import scala.annotation.nowarn

@nowarn("msg=ScalaLightPlatformCodeInsightTestCaseAdapter")
@Category(Array(classOf[TypecheckerTests]))
abstract class TestAllResolveTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  def folderPath: String = baseRootPath + "resolve/testAllResolve/"

  protected def doTest(): Unit = {
    import _root_.org.junit.Assert._

    val filePath = folderPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    configureFromFileTextAdapter(getTestName(false) + ".scala", fileText)
    val scalaFile = getFileAdapter.asInstanceOf[ScalaFile]
    scalaFile.accept(new ScalaRecursiveElementVisitor {
      override def visitReference(ref: ScReference): Unit = {
        val resolve = ref.resolve()
        assertNotNull("Failed on reference: " + ref.getText + ". Reference Range: (" +
                ref.getTextRange.getStartOffset + ", " + ref.getTextRange.getEndOffset + ")",
          resolve)
        super.visitReference(ref)
      }
    })
  }
}