package org.jetbrains.plugins.scala
package lang
package autoImport

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import com.intellij.psi.{PsiClass, PsiPackage, SmartPointerManager}
import org.jetbrains.plugins.scala.autoImport.quickFix.ScalaImportTypeFix
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.junit.Assert._

import java.io.File
import scala.annotation.nowarn

@nowarn("msg=ScalaLightPlatformCodeInsightTestCaseAdapter")
abstract class AutoImportTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter with ScalaFiles {
  private val refMarker = "/*ref*/" // todo to be replaced with <caret>

  protected def folderPath = baseRootPath + "autoImport/"

  protected override def sourceRootPath: String = folderPath

  // todo configureBy* should be called instead
  protected def doTest(): Unit = {
    val filePath = folderPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.refreshAndFindFileByPath(filePath.replace(File.separatorChar, '/'))
    assertNotNull("file " + filePath + " not found", file)
    var fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    val offset = fileText.indexOf(refMarker)
    fileText = fileText.replace(refMarker, "")

    configureFromFileTextAdapter(getTestName(false) + "." + fileType.getDefaultExtension, fileText)
    val scalaFile = getFileAdapter.asInstanceOf[ScalaFile]
    assertNotEquals(s"Not specified ref marker in test case. Use $refMarker in scala file for this.", offset, -1)
    val ref = getParentOfType(scalaFile.findElementAt(offset), classOf[ScReference])
    assertNotNull("Not specified reference at marker.", ref)

    ref.resolve() match {
      case null =>
      case _ => fail("Reference must be unresolved.")
    }

    implicit val project: Project = getProjectAdapter
    val refPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(ref)

    val fix = ScalaImportTypeFix(ref)
    val classes = fix.elements
    assertFalse("Element to import not found", classes.isEmpty)
    checkNoResultsIfExcluded(ref, classes.map(_.qualifiedName))

    classes.map(_.element).foreach {
      case _: PsiClass | _: ScTypeAlias | _: PsiPackage =>
      case element => fail(s"Class, alias or package is expected, found: $element")
    }

    var res: String = null
    val lastPsi = scalaFile.findElementAt(scalaFile.getTextLength - 1)
    try {
      val action = fix.createAddImportAction(getEditorAdapter)
      action.addImportTestOnly(classes.head)

      res = scalaFile.getText.substring(0, lastPsi.getTextOffset).trim//getImportStatements.map(_.getText()).mkString("\n")
      assertNotNull("reference is unresolved after import action", refPointer.getElement.resolve)
    }
    catch {
      case e: Exception =>
        println(e)
        fail(e.getMessage + "\n" + e.getStackTrace.mkString("Array(", ", ", ")"))
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

  private def checkNoResultsIfExcluded(ref: ScReference, excludedNames: Seq[String]): Unit = {
    ImportElementFixTestBase.withExcluded(getProject, excludedNames) {
      val newFix = ScalaImportTypeFix(ref)
      val foundElements = newFix.elements
      assertTrue(
        s"$excludedNames excluded, but something was found",
        foundElements.isEmpty
      )
    }
  }
}