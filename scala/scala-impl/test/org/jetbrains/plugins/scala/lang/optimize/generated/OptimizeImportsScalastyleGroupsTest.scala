package org.jetbrains.plugins.scala.lang.optimize.generated

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.codeInspection.scalastyle.ScalastyleSettings
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.lang.optimize.OptimizeImportsTestBase

import java.nio.file.Path
import java.util.regex.Pattern

class OptimizeImportsScalastyleGroupsTest extends OptimizeImportsTestBase {
  override def folderPath: Path = super.folderPath / "scalastyle"

  override protected lazy val projectJdk: Sdk =
    SmartJDKLoader.createFilteredJdk(LanguageLevel.JDK_17, Seq("java.base", "java.compiler"))

  val groups: Seq[Pattern] = Seq("java\\..+", "scala\\..+", ".+").map(Pattern.compile)
  override def settings(file: PsiFile) =
    super.settings(file).copy(scalastyleSettings = ScalastyleSettings(scalastyleOrder = true, groups = Some(groups)))

  def testScalastyleGroups(): Unit = doTest()
}
