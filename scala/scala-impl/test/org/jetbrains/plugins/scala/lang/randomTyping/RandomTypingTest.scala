package org.jetbrains.plugins.scala.lang.randomTyping

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.{ErrorLog, TestLoggerKt}
import com.intellij.util.lang.CompoundRuntimeException
import com.intellij.util.ui.EDT
import org.jetbrains.plugins.scala.base.EditorActionTestBase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.randomTyping.RandomTypingTest._
import org.jetbrains.plugins.scala.project.template.FileExt
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.{RandomTypingTests, ScalaVersion}
import org.junit.experimental.categories.Category

import java.io.File
import java.nio.file.Files
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.jdk.CollectionConverters.ListHasAsScala
import scala.util.Random


@Category(Array(classOf[RandomTypingTests]))
class RandomTypingTest_in_Scala3 extends RandomTypingTestBase(TestUtils.getTestDataPath + "/parser/data3") {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_0

  //def test_specific(): Unit = {
  //  typeRandomly(
  //    TestUtils.getTestDataPath + "/parser/data3/types/wildcard_question.test",
  //    1224268322
  //  )
  //}
}

@Category(Array(classOf[RandomTypingTests]))
class RandomTypingTest_in_Scala3_ImportedData extends RandomTypingTestBase(TestUtils.getTestDataPath + "/parser/scala3Import/success") {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_0

  //def test_specific(): Unit = {
  //  typeRandomly(
  //    TestUtils.getTestDataPath + /parser/scala3Import/success/,
  //    <test_seed>,
  //  )
  //}
}

@Category(Array(classOf[RandomTypingTests]))
class RandomTypingTest_in_Scala2 extends RandomTypingTestBase(TestUtils.getTestDataPath + "/parser/data") {
  override protected def supportedIn(version: ScalaVersion): Boolean = version <= ScalaVersion.Latest.Scala_2_13

  override val ignoredFiles: Set[String] = Set("xmlPattern.test")
//  def test_specific(): Unit = {
//    typeRandomly(
//      TestUtils.getTestDataPath + "/parser/data/doccomments/scaladoc/simpleMixed1.test",
//      717565430,
//    )
//  }
}

abstract class RandomTypingTestBase(testFilePath: String) extends EditorActionTestBase {
  val logging = false

  private def log(s: Any): Unit =
    if (logging) println(s)

  def ignoredFiles: Set[String] = Set.empty

  def test_all_files(): Unit = {
    val random = new Random

    val allFiles = new File(testFilePath)
      .allFiles
      // yeah this class does a lot of string indexing and slicing,
      // which doesn't work at all well with code points that do not fit into one char
      // So let's ignore those
      .filterNot(file => hasCodePointsSpanningMultipleChars(Files.readString(file.toPath)))
      .filterNot(file => ignoredFiles.contains(file.getName))
      .to(ArraySeq)
    println(s"Test ${allFiles.size} in $testFilePath:")
    for ((file, i) <- allFiles.zipWithIndex) {
      print(f"[${i + 1}%4s/${allFiles.length}] ")
      typeRandomly(file, random.nextInt(Int.MaxValue))
    }
  }

  def typeRandomly(path: String, seed: Int): Unit =
    typeRandomly(new File(path), seed)

  private val separatorRegex = raw"\n-{5,}".r

  def typeRandomly(file: File, seed: Int): Unit = {
    println(s"Testing(seed = $seed) ${file.getAbsolutePath}")
    val targetText = {
      val text = FileUtil.loadFile(file, /*convertLineSeparators*/ true)
      separatorRegex.findFirstMatchIn(text)
        .fold(text)(m => text.substring(0, m.start))
    }

    try {
      typeRandomly(targetText, new Random(seed))
      TestLoggerKt.getErrorLog.takeLoggedErrors().forEach(throw _)
    } catch {
      case e: Throwable =>
        def ignoreException(e: Throwable): Boolean = e match {
          case e: CompoundRuntimeException => e.getExceptions.asScala.forall(ignoreException)
          case e if e.getMessage == "Assertion failed: Caret model is in its update process. All requests are illegal at this point." => true
          case e if e.getMessage.startsWith("nonempty text is not covered by block") => true
          case _ => false
        }
        if (!ignoreException(e)) {
          throw new Exception(s"Exception while typing ${file.getAbsolutePath} with seed $seed", e)
        }
    }
  }

  def typeRandomly(targetText: String, random: Random): Unit = {
    assert(!hasCodePointsSpanningMultipleChars(targetText))
    val psiDocumentManager = PsiDocumentManager.getInstance(getProject)
    def commit(): Unit = {
      inWriteCommandAction {
        psiDocumentManager.commitAllDocuments()
      }(getProject)

      // process awt events... otherwise they will stack and we get a warning
      EDT.dispatchAllInvocationEvents()
    }

    val file = myFixture.configureByText("test.scala", "")
    val caretModel = getEditor.getCaretModel
    val found = mutable.Set.empty[String]

    val actionWindow = 120
    val minTextLengthRatioForRandomDeletions = 0.60
    val maxRandomDeletions = 80
    val probabilityToDoRandomDeletion = 1.0/2.0
    val maxLoopRounds = 300

    var randomDeletions = 0
    var needRescue = false
    var randomActionsLeftToRescue = 10
    var currentLoopRound = 0
    while (!file.textMatches(targetText) && currentLoopRound < maxLoopRounds) {
      currentLoopRound += 1
      val doRandomDeletion =
        file.getTextLength >= targetText.length * minTextLengthRatioForRandomDeletions &&
          randomDeletions < maxRandomDeletions &&
          random.nextDouble() < probabilityToDoRandomDeletion
      val actionOffset = if (doRandomDeletion) {
        randomDeletions += 1

        // we don't need to find loops when deleting random stuff
        found.clear()

        val interestingOffsets =
          for ((c, i) <- file.getText.zipWithIndex if "()[]{}\"':".contains(c)) yield i

        val i =
          if (interestingOffsets.isEmpty) random.nextInt(file.getTextLength)
          else interestingOffsets(random.nextInt(interestingOffsets.length))
        val start = (i - random.nextInt(5)) max 0
        val end = (i + random.nextInt(5)) min file.getTextLength
        getEditor.getSelectionModel.setSelection(start, end)
        performBackspaceAction()
        log(s"delete $start to $end")
        i
      } else {
        val actions = actionsInPrefixWindow(file.getText, targetText, actionWindow)
        val action =
          if (needRescue) {
            randomActionsLeftToRescue -= 1
            actions(random.nextInt(actions.length))
          } else actions.head
        log(action)

        action.content match {
          case Left(len) =>
            caretModel.moveToOffset(action.offset + len)
            0 to random.nextInt(len) foreach { _ => performBackspaceAction(); commit() }
          case Right(txt) =>
            caretModel.moveToOffset(action.offset)
            val cut = txt.indexWhere(c => "([{'\"".contains(c))
            val txt2 = if (cut >= 0) txt.take(cut + 1) else txt
            txt2.foreach { c => performTypingAction(c); commit() }
        }
        action.offset
      }
      commit()

      val result = file.getText

      log(
        s"""---------------------------
           |$result
           |---------------------------
           |""".stripMargin
      )

      if (found.contains(result)) {
        needRescue = true
        log("Found loop!")
        if (randomActionsLeftToRescue <= 0) {
          log("Force rescue...")
          val fixIdx = actionOffset + 15

          inWriteCommandAction {
            getEditor.getDocument.setText(targetText.take(fixIdx) + result.drop(actionOffset + 15))
            PsiDocumentManager.getInstance(getProject).commitAllDocuments()
          }(getProject)

          log(
            s"""---------------------------
               |$result
               |---------------------------
               |""".stripMargin
          )
        } else log("Try to rescue through random action...")
      } else {
        needRescue = false
        found += result
      }
    }
  }
}

object RandomTypingTest {
  case class TypingAction(offset: Int, content: Either[Int, String]) {
    override def toString: String = content match {
      case Right(s) => s"at $offset insert [${s.replace("\n", "\\n").shorten(20)}]"
      case Left(i) => s"at ${offset + i} delete $i"
    }
  }

  def actionsInPrefixWindow(current: String, target: String, window: Int): Seq[TypingAction] = {
    def prefixLen(a: String, b: String): Int =
      (a.iterator zip b.iterator).takeWhile { case (a, b) => a == b }.length

    val prefix = prefixLen(current, target)
    mergeActions(possibleActions(
      current = current.slice(prefix, prefix + window),
      target =  target.slice(prefix, prefix + window)
    )).map(a => a.copy(offset = a.offset + prefix))
  }

  /**
   * Levenshtein algo that computes edit operations to bring `current` to `target`
   * @return actions on single characters, so have to be merged to edit connected actions
   */
  def possibleActions(current: String, target: String): Seq[TypingAction] = {
    def f(prev: (Int, List[TypingAction]), cost: Int, typingAction: TypingAction): (Int, List[TypingAction]) = {
      val (prevCost, prevActions) = prev
      (prevCost + cost, typingAction :: prevActions)
    }

    val currentLen = current.length + 1
    val targetLen = target.length + 1
    val matrix = Array.fill[(Int, List[TypingAction])](currentLen, targetLen)((0, Nil))

    for(i <- 1 until currentLen)
      matrix(i)(0) = f(matrix(i - 1)(0), 1, TypingAction(i - 1, Left(1)))
    for(j <- 1 until targetLen)
      matrix(0)(j) = f(matrix(0)(j - 1), 1, TypingAction(j - 1, Right(target(j - 1).toString)))

    for(j <- 1 until targetLen) {
      for(i <- 1 until currentLen) {
        val diagPrev = matrix(i - 1)(j - 1)
        val areSame = if (current(i - 1) == target(j - 1)){
          diagPrev
        }else{
            val afterDel = f(diagPrev, 0, TypingAction(j - 1, Left(1)))
            f(afterDel, 2, TypingAction(j - 1, Right(target(j - 1).toString)))
        }

        matrix(i)(j) = Seq(
          f(matrix(i)(j - 1), 1, TypingAction(j - 1, Right(target(j - 1).toString))),
          f(matrix(i - 1)(j), 1, TypingAction(j, Left(1))),
          areSame,
        ).minBy(_._1)

        //math.min(math.min(matrix(i-1)(j)+1,matrix(i)(j-1)+1),matrix(i-1)(j-1)+cost)
      }
    }
    matrix(currentLen - 1)(targetLen - 1)._2.reverse
  }

  def mergeActions(actions: Seq[TypingAction]): List[TypingAction] =
    actions.foldLeft(List.empty[TypingAction]) {
      case (TypingAction(i, Left(iDels)) :: rest, TypingAction(j, Left(jDels))) if i + iDels == j => TypingAction(i, Left(iDels + jDels)) :: rest
      case (TypingAction(i, Right(iIns)) :: rest, TypingAction(j, Right(jIns))) if i + iIns.length == j => TypingAction(i, Right(iIns + jIns)) :: rest
      case (rest, next) => next :: rest
    }.reverse

  def hasCodePointsSpanningMultipleChars(string: String): Boolean =
    Character.codePointCount(string, 0, string.length) != string.length
}
