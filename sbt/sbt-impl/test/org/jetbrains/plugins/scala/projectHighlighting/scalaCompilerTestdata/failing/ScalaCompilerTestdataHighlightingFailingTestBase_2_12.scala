package org.jetbrains.plugins.scala.projectHighlighting.scalaCompilerTestdata.failing

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.projectHighlighting.reporter.HighlightingProgressReporter
import org.jetbrains.plugins.scala.projectHighlighting.scalaCompilerTestdata.ScalaCompilerTestdataHighlightingTest
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

import java.io.File
import scala.reflect.NameTransformer

abstract class ScalaCompilerTestdataHighlightingFailingTestBase_2_12 extends ScalaCompilerTestdataHighlightingTest {

  override protected lazy val reporter = HighlightingProgressReporter.newInstance(this.getClass.getSimpleName, filesWithProblems)

  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12

  protected def fileName = getTestName(/*lowercaseFirstLetter*/ false).stripPrefix("_")

  protected def filesWithProblems: Map[String, Set[TextRange]] = {
    import org.jetbrains.plugins.scala.util.TextRangeUtils.ImplicitConversions.tupleToTextRange
    getTestName(true) match {
      case "_t7232c" => Map("t7232c/Test.scala" -> Set())
      case "_t7364b" => Map("t7364b/UseIt_2.scala" -> Set((68, 79), (56, 64)))
      case "_t4365" => Map("t4365/a_1.scala" -> Set((535, 557)))
      case "_t5545" => Map("t5545/S_2.scala" -> Set((64, 66)), "S_1.scala" -> Set((64, 66)))
      case "_t6169" => Map("t6169/skinnable.scala" -> Set(), "t6169/t6169.scala" -> Set())
      case "_t8497" => Map("t8497/A_1.scala" -> Set())
      case "_t8781" => Map("t8781/Test_2.scala" -> Set((82, 91)))
      case _ => Map(("failed/" + NameTransformer.decode(fileName) + ".scala", Set.empty))
    }
  }

  override protected def filesToHighlight: Seq[File] = {
    val decoded = NameTransformer.decode(fileName)
    val dirPath = getTestDataDir + decoded
    val dir = new File(dirPath)
    val file = new File(dirPath + ".scala")

    if (dir.exists())
      Seq(dir)
    else if (file.exists())
      Seq(file)
    else {
      throw new RuntimeException("No file exists")
    }
  }
}




