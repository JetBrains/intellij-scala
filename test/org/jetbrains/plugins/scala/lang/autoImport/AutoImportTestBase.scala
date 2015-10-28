package org.jetbrains.plugins.scala
package lang
package autoImport

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix.ClassTypeToImport
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.util.ScalaUtils

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.03.2009
 */
abstract class AutoImportTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  private val refMarker =  "/*ref*/"

  protected def folderPath = baseRootPath() + "autoImport/"

  protected override def rootPath(): String = folderPath

  protected def doTest() {
    import junit.framework.Assert._
    val filePath = folderPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.refreshAndFindFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    configureFromFileTextAdapter(getTestName(false) + ".scala", fileText)
    val scalaFile = getFileAdapter.asInstanceOf[ScalaFile]
    val offset = fileText.indexOf(refMarker)
    val refOffset = offset + refMarker.length
    assert(offset != -1, "Not specified ref marker in test case. Use /*ref*/ in scala file for this.")
    val ref: ScReferenceElement = PsiTreeUtil.
            getParentOfType(scalaFile.findElementAt(refOffset), classOf[ScReferenceElement])
    assert(ref != null, "Not specified reference at marker.")

    ref.resolve() match {
      case null =>
      case _ => assert(assertion = false, message = "Reference must be unresolved.")
    }

    val classes = ScalaImportTypeFix.getTypesToImport(ref, getProjectAdapter)
    assert(classes.length > 0, "Haven't classes to import")
    var res: String = null
    val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)
    try {
      ScalaUtils.runWriteAction(new Runnable {
        def run() {
          classes(0) match {
            case ClassTypeToImport(clazz) =>
              org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix.
                getImportHolder(ref, getProjectAdapter).addImportForClass(clazz)
            case ta =>
              org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix.
                getImportHolder(ref, getProjectAdapter).addImportForPath(ta.qualifiedName, ref)
          }
          UsefulTestCase.doPostponedFormatting(getProjectAdapter)
        }
      }, getProjectAdapter, "Test")
      res = scalaFile.getText.substring(0, lastPsi.getTextOffset).trim//getImportStatements.map(_.getText()).mkString("\n")
      assert(ref.resolve != null, "reference is unresolved after import action")
    }
    catch {
      case e: Exception =>
        println(e)
        assert(assertion = false, message = e.getMessage + "\n" + e.getStackTrace)
    }

    val text = lastPsi.getText
    val output = lastPsi.getNode.getElementType match {
      case ScalaTokenTypes.tLINE_COMMENT => text.substring(2).trim
      case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tDOC_COMMENT =>
        text.substring(2, text.length - 2).trim
      case _ =>
        assertTrue("Test result must be in last comment statement.", false)
        ""
    }
    assertEquals(output, res)
  }
}