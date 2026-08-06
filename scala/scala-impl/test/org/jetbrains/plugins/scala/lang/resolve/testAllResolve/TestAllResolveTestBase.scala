package org.jetbrains.plugins.scala
package lang.resolve.testAllResolve

import _root_.org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{PathExt, StringExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.junit.experimental.categories.Category

import java.nio.charset.StandardCharsets
import java.nio.file.Path

@Category(Array(classOf[TypecheckerTests]))
abstract class TestAllResolveTestBase extends ScalaLightCodeInsightFixtureTestCase {
  def folderPath: Path = Path.of(getTestDataPath, "resolve", "testAllResolve")

  protected def doTest(): Unit = {
    import _root_.org.junit.Assert._

    val fileName = getTestName(false) + ".scala"
    val filePath = folderPath / fileName
    val fileText = filePath.readAllBytesToString(StandardCharsets.UTF_8).withNormalizedSeparator
    configureFromFileText(fileName, fileText)
    val scalaFile = getFile.asInstanceOf[ScalaFile]
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
