package org.jetbrains.plugins.scala
package lang
package optimize


import _root_.com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import java.io.File
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.ScalaUtils

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.06.2009
 */

abstract class OptimizeImportsTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {

  def folderPath: String = baseRootPath() + "optimize/"

  protected def doTest() {
    import _root_.junit.framework.Assert._

    val filePath = folderPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    configureFromFileTextAdapter(getTestName(false) + ".scala", fileText)
    val scalaFile = getFileAdapter.asInstanceOf[ScalaFile]

    var res: String = null
    var lastPsi = TreeUtil.findLastLeaf(scalaFile.getNode).getPsi

    if (getTestName(true).startsWith("sorted")) ScalaCodeStyleSettings.getInstance(getProjectAdapter).setSortImports(true)
    ScalaUtils.runWriteActionDoNotRequestConfirmation(new ScalaImportOptimizer().processFile(scalaFile), getProjectAdapter, "Test")
    res = scalaFile.getText.substring(0, lastPsi.getTextOffset).trim//getImportStatements.map(_.getText()).mkString("\n")

    lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)
    val text = lastPsi.getText
    val output = lastPsi.getNode.getElementType match {
      case ScalaTokenTypes.tLINE_COMMENT => text.substring(2).trim
      case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tDOC_COMMENT =>
        text.substring(2, text.length - 2).trim
      case _ => assertTrue("Test result must be in last comment statement.", false)
    }
    assertEquals(output, res)
  }
}