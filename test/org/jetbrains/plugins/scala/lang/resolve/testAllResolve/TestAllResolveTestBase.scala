package org.jetbrains.plugins.scala.lang.resolve.testAllResolve

import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.plugins.scala.base.ScalaPsiTestCase
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import java.io.File
import org.jetbrains.plugins.scala.codeInspection.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.11.2009
 */

class TestAllResolveTestBase extends ScalaPsiTestCase {
  override def rootPath: String = super.rootPath + "resolve/testAllResolve/"

  protected def doTest = {
    import _root_.junit.framework.Assert._

    val filePath = rootPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val scalaFile: ScalaFile = PsiManager.getInstance(myProject).findFile(file).asInstanceOf[ScalaFile]
    scalaFile.accept(new ScalaRecursiveElementVisitor {
      override def visitReference(ref: ScReferenceElement) = {
        val resolve = ref.resolve
        assertNotNull("Failed on reference: " + ref.getText + ". Reference Range: (" +
                ref.getTextRange.getStartOffset + ", " + ref.getTextRange.getEndOffset + ")",
          resolve)
        super.visitReference(ref)
      }
    })
  }
}