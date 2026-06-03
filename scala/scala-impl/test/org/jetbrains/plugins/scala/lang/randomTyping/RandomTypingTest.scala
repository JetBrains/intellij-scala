package org.jetbrains.plugins.scala.lang.randomTyping

import com.intellij.testFramework.TestLoggerKt
import com.intellij.util.lang.CompoundRuntimeException
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.scala3.imported.Scala3ImportedParserTestConfig
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.{RandomTypingTests, ScalaVersion}
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.nio.file.Path
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters.ListHasAsScala
import scala.util.Random
import scala.util.chaining.scalaUtilChainingOps

@Category(Array(classOf[RandomTypingTests]))
class RandomTypingTest_in_Scala3 extends RandomTypingFileTestBase(TestUtils.getTestDataPath + "/parser/data3") {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_0

  //def test_specific(): Unit = {
  //  typeRandomly(
  //    Path.of(TestUtils.getTestDataPath) / "parser/data3/types/wildcard_question.test",
  //    1224268322
  //  )
  //}
}

@Category(Array(classOf[RandomTypingTests]))
class RandomTypingTest_in_Scala3_ImportedData extends RandomTypingFileTestBase(TestUtils.getTestDataPath + "/" + Scala3ImportedParserTestConfig.Newest.successDataDirectory) {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == Scala3ImportedParserTestConfig.Newest.scalaTargetVersion

  //def test_specific(): Unit = {
  //  typeRandomly(
  //    Path.of(TestUtils.getTestDataPath) / "parser/scala3Import/newest/success/...",
  //    2038114909,
  //  )
  //}
}

@Category(Array(classOf[RandomTypingTests]))
class RandomTypingTest_in_Scala2 extends RandomTypingFileTestBase(TestUtils.getTestDataPath + "/parser/data") {
  override protected def supportedIn(version: ScalaVersion): Boolean = version <= ScalaVersion.Latest.Scala_2_13

  override val ignoredFiles: Set[String] = Set("xmlPattern.test")
//  def test_specific(): Unit = {
//    typeRandomly(
//      Path.of(TestUtils.getTestDataPath) / "parser/data/...",
//      717565430,
//    )
//  }
}

@RunWith(classOf[JUnit4])
abstract class RandomTypingFileTestBase(testFilePath: String) extends RandomTypingTestBase {
  val timeoutInMs = 40 * 60 * 1000

  def ignoredFiles: Set[String] = Set.empty

  @Test
  def test_all_files(): Unit = {
    val random = new Random

    val allFiles = Path.of(testFilePath)
      .allFiles()
      // yeah this class does a lot of string indexing and slicing,
      // which doesn't work at all well with code points that do not fit into one char
      // So let's ignore those
      .filterNot(file => hasCodePointsSpanningMultipleChars(file.readAllBytesToString()))
      .filterNot(file => ignoredFiles.contains(file.getFileName.toString))
      .to(ArraySeq)
      .pipe(random.shuffle(_))
    println(s"Test ${allFiles.size} in $testFilePath:")
    val now = System.currentTimeMillis()
    for ((file, i) <- allFiles.iterator.zipWithIndex.takeWhile(_ => System.currentTimeMillis() - now < timeoutInMs)) {
      print(f"[${i + 1}%4s/${allFiles.length}] ")
      typeRandomly(file, random.nextInt(Int.MaxValue))
    }
  }

  private val separatorRegex = raw"\n-{5,}".r

  def typeRandomly(file: Path, seed: Int): Unit = {
    val targetText = {
      val text = file.readAllBytesToString().withNormalizedSeparator
      separatorRegex.findFirstMatchIn(text)
        .fold(text)(m => text.substring(0, m.start))
    }

    typeRandomly(targetText, seed, file.toAbsolutePath.toString)
  }
}
