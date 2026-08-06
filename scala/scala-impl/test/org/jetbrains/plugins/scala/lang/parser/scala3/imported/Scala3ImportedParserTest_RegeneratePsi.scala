package org.jetbrains.plugins.scala.lang.parser.scala3.imported

import com.intellij.psi.impl.DebugUtil.psiToString
import org.jetbrains.plugins.scala.extensions.{PathExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Ignore
import org.junit.runner.RunWith
import org.junit.runners.Suite

import java.nio.file.{Files, Path}

@Ignore("For local running only")
@RunWith(classOf[Suite])
@Suite.SuiteClasses(Array(
  classOf[Scala3ImportedParserTest_RegeneratePsi_LTS],
  classOf[Scala3ImportedParserTest_RegeneratePsi_Newest]
))
class Scala3ImportedParserTest_RegeneratePsi

@Ignore("For local running only")
class Scala3ImportedParserTest_RegeneratePsi_LTS
  extends Scala3ImportedParserTest_RegeneratePsi_Base(Scala3ImportedParserTestConfig.LTS)

@Ignore("For local running only")
class Scala3ImportedParserTest_RegeneratePsi_Newest
  extends Scala3ImportedParserTest_RegeneratePsi_Base(Scala3ImportedParserTestConfig.Newest)

abstract class Scala3ImportedParserTest_RegeneratePsi_Base(config: Scala3ImportedParserTestConfig)
  extends Scala3ImportedParserTestBase_UsedAsScript(config, runOnSucceedDirectory = true) {

  override protected def transform(testName: String, fileText: String): String = {
    val (errors, file) = findErrorElements(fileText, project)
    val interlaced = findInterlacedRanges(file, testName)

    if (errors.isEmpty && interlaced.isEmpty) {
      val rootTestDataPath = Path.of(TestUtils.getTestDataPath)
      val path = rootTestDataPath / config.successDataDirectory / s"$testName.test"

      println("Regenerate " + path)
      val psiTreeText = psiToString(file, true).replace(": " + file.name, "")
      val content = Files.readString(path)
      val searchString = "-----\n"
      val idx = content.indexOf(searchString).ensuring(_ >= 0)
      val newContent = content.substring(0, idx + searchString.length) + psiTreeText
      Files.writeString(path, newContent)
    }

    ""
  }

  override protected def transformExpectedResult(text: String): String = ""
}
