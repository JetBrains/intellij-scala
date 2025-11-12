package org.jetbrains.plugins.scala.lang.parser.scala3.imported

import com.intellij.psi.impl.DebugUtil.psiToString
import org.jetbrains.plugins.scala.extensions.{PathExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Ignore
import org.junit.runner.RunWith
import org.junit.runners.Suite

import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}

@Ignore("For local running only")
@RunWith(classOf[Suite])
@Suite.SuiteClasses(Array(
  classOf[Scala3ImportedParserTest_Move_Fixed_Tests_LTS],
  classOf[Scala3ImportedParserTest_Move_Fixed_Tests_Newest]
))
class Scala3ImportedParserTest_Move_Fixed_Tests

@Ignore("For local running only")
class Scala3ImportedParserTest_Move_Fixed_Tests_LTS
  extends Scala3ImportedParserTest_Move_Fixed_Tests_Base(Scala3ImportedParserTestConfig.LTS)

@Ignore("For local running only")
class Scala3ImportedParserTest_Move_Fixed_Tests_Newest
  extends Scala3ImportedParserTest_Move_Fixed_Tests_Base(Scala3ImportedParserTestConfig.Newest)

abstract class Scala3ImportedParserTest_Move_Fixed_Tests_Base(config: Scala3ImportedParserTestConfig)
  extends Scala3ImportedParserTestBase_UsedAsScript(config, runOnSucceedDirectory = false) {

  override protected def transform(testName: String, fileText: String): String = {
    val (errors, file) = findErrorElements(fileText, project)
    val interlaced = findInterlacedRanges(file, testName)

    if (errors.isEmpty && interlaced.isEmpty) {
      val rootTestDataPath = Path.of(TestUtils.getTestDataPath)
      val from = rootTestDataPath / config.failDataDirectory / s"$testName.test"
      val to = rootTestDataPath / config.successDataDirectory / s"$testName.test"

      println("Move " + from)
      println("  to " + to)
      Files.createDirectories(rootTestDataPath / config.successDataDirectory)
      Files.move(
        from,
        to,
        StandardCopyOption.REPLACE_EXISTING
      )

      val psiTreeText = psiToString(file, true).replace(": " + file.name, "")
      Files.writeString(to, psiTreeText, StandardOpenOption.APPEND)
    }
    // all files of failing test have no ast to test against, so return an empty string here
    ""
  }

  override protected def transformExpectedResult(text: String): String = {
    assert(text.isEmpty, "Expected result should be empty")
    text.trim
  }
}
