package org.jetbrains.plugins.scala
package lang
package autoImport

import java.io.File

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.annotator.intention.{ClassToImport, ScalaImportTypeFix}
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.extensions.executeWriteActionCommand
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.junit.Assert._

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.03.2009
 */
abstract class AutoImportTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  private val refMarker =  "/*ref*/"

  protected def folderPath = baseRootPath + "autoImport/"

  protected override def sourceRootPath: String = folderPath

  // file type to run the test with (to be able to run same tests as Worksheet files)
  protected def fileType: FileType = ScalaFileType.INSTANCE

  protected def doTest(): Unit = {
    val filePath = folderPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.refreshAndFindFileByPath(filePath.replace(File.separatorChar, '/'))
    assertNotNull("file " + filePath + " not found", file)
    var fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    val offset = fileText.indexOf(refMarker)
    fileText = fileText.replace(refMarker, "")

    configureFromFileTextAdapter(getTestName(false) + "." + fileType.getDefaultExtension, fileText)
    val scalaFile = getFileAdapter.asInstanceOf[ScalaFile]
    assertNotEquals("Not specified ref marker in test case. Use /*ref*/ in scala file for this.", offset, -1)
    val ref: ScReference = PsiTreeUtil.getParentOfType(scalaFile.findElementAt(offset), classOf[ScReference])
    assertNotNull("Not specified reference at marker.", ref)

    ref.resolve() match {
      case null =>
      case _ => fail("Reference must be unresolved.")
    }

    implicit val project: Project = getProjectAdapter
    val refPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(ref)

    val classes = ScalaImportTypeFix(ref).classes
    assertFalse("Haven't classes to import", classes.isEmpty)

    var res: String = null
    val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)
    try {
      executeWriteActionCommand("Test") {
        val holder = ScImportsHolder(ref)(project)
        classes(0) match {
          case ClassToImport(clazz) => holder.addImportForClass(clazz)
          case ta => holder.addImportForPath(ta.qualifiedName, ref)
        }
        UsefulTestCase.doPostponedFormatting(project)
      }

      res = scalaFile.getText.substring(0, lastPsi.getTextOffset).trim//getImportStatements.map(_.getText()).mkString("\n")
      assertNotNull("reference is unresolved after import action", refPointer.getElement.resolve)
    }
    catch {
      case e: Exception =>
        println(e)
        fail(e.getMessage + "\n" + e.getStackTrace)
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