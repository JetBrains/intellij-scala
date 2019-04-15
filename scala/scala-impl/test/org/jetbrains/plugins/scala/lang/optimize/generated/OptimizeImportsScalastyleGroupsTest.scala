package org.jetbrains.plugins.scala.lang.optimize.generated

import java.util.regex.Pattern

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalastyleSettings
import org.jetbrains.plugins.scala.lang.optimize.OptimizeImportsTestBase

/**
  * Nikolay.Tropin
  * 13-Feb-17
  */
class OptimizeImportsScalastyleGroupsTest extends OptimizeImportsTestBase {
  override def folderPath: String = super.folderPath + "scalastyle/"

  val groups: Seq[Pattern] = Seq("java\\..+", "scala\\..+", ".+").map(Pattern.compile)
  override def settings(file: PsiFile) =
    super.settings(file).copy(scalastyleSettings = ScalastyleSettings(scalastyleOrder = true, groups = Some(groups)))

  def testScalastyleGroups(): Unit = doTest()
}
