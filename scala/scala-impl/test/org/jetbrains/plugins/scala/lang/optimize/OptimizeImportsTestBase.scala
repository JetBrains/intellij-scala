package org.jetbrains.plugins.scala
package lang
package optimize


import java.io.File

import _root_.com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.editor.importOptimizer.{OptimizeImportSettings, ScalaImportOptimizer}
import org.jetbrains.plugins.scala.extensions.executeWriteActionCommand
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.06.2009
 */

abstract class OptimizeImportsTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {

  def folderPath: String = baseRootPath + "optimize/"

  protected def settings(file: PsiFile) = OptimizeImportSettings(file)

  def importOptimizer = new ScalaImportOptimizer() {
    override def settings(file: PsiFile): OptimizeImportSettings = OptimizeImportsTestBase.this.settings(file)
  }

  protected def doTest() {
    import _root_.junit.framework.Assert._

    val filePath = folderPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.refreshAndFindFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    configureFromFileTextAdapter(getTestName(false) + ".scala", fileText)
    val scalaFile = getFileAdapter.asInstanceOf[ScalaFile]

    var res: String = null
    var lastPsi = TreeUtil.findLastLeaf(scalaFile.getNode).getPsi

    if (getTestName(true).startsWith("sorted")) ScalaCodeStyleSettings.getInstance(getProjectAdapter).setSortImports(true)

    executeWriteActionCommand(
      importOptimizer.processFile(scalaFile),
      "Test",
      UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION
    )(getProjectAdapter)

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