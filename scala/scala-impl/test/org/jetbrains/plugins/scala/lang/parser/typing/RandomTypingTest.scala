package org.jetbrains.plugins.scala.lang.parser.typing

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.base.EditorActionTestBase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.typing.RandomTypingTest._
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.{NightlyTests, ScalaVersion}
import org.junit.experimental.categories.Category

import java.io.File
import scala.collection.mutable
import scala.util.Random


@Category(Array(classOf[NightlyTests]))
class RandomTypingTest extends EditorActionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_0

  def test_random_files(): Unit = {
    val amount = 100

    val testfiles = TestUtils.getTestDataPath + "/parser/scala3Import/success"
    val random = new Random

    var allFiles = new File(testfiles).listFiles()
    for (_ <- 1 to amount) {
      val i = random.nextInt(allFiles.length)
      val randomFile = allFiles(i)
      allFiles = allFiles.patch(i, Nil, 1)
      typeRandomly(randomFile, random.nextInt())
    }
  }

  //def test_specific(): Unit = {
  //  typeRandomly(
  //    new File("/home/tobi/workspace/intellij-scala/community/scala/scala-impl/testdata/parser/scala3Import/success/i2973.test"),
  //    -276617896
  //  )
  //}

  def typeRandomly(file: File, seed: Int): Unit = {
    println(s"Testing(seed = $seed}) ${file.getAbsolutePath}")
    val targetText = FileUtil.loadFile(file).replace("-----\n", "")

    try {
      typeRandomly(targetText, new Random(seed))
    } catch {
      case e: Throwable =>
        throw new Exception(s"Exception while typing ${file.getAbsolutePath} with seed $seed", e)
    }
  }

  def typeRandomly(targetText: String, random: Random): Unit = {
    def commit(): Unit =
      inWriteCommandAction {
        PsiDocumentManager.getInstance(getProject).commitAllDocuments()
      }(getProject)

    val file = getFixture.configureByText("test.scala", "")
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
        val interestingOffsets =
          for ((c, i) <- file.getText.zipWithIndex if "()[]{}\"':".contains(c)) yield i

        val i =
          if (interestingOffsets.isEmpty) random.nextInt(file.getTextLength)
          else interestingOffsets(random.nextInt(interestingOffsets.length))
        val start = (i - random.nextInt(5)) max 0
        val end = (i + random.nextInt(5)) min file.getTextLength
        getEditor.getSelectionModel.setSelection(start, end)
        performBackspaceAction()
        println(s"delete $start to $end")
        i
      } else {
        val actions = actionsInPrefixWindow(file.getText, targetText, actionWindow)
        val action =
          if (needRescue) {
            randomActionsLeftToRescue -= 1
            actions(random.nextInt(actions.length))
          } else actions.head
        println(action)

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

      println(
        s"""---------------------------
           |$result
           |---------------------------
           |""".stripMargin
      )

      if (found.contains(result)) {
        needRescue = true
        println("Found loop!")
        if (randomActionsLeftToRescue <= 0) {
          println("Force rescue...")
          val fixIdx = actionOffset + 15

          inWriteCommandAction {
            getEditor.getDocument.setText(targetText.take(fixIdx) + result.drop(actionOffset + 15))
            PsiDocumentManager.getInstance(getProject).commitAllDocuments()
          }(getProject)

          println(
            s"""---------------------------
               |$result
               |---------------------------
               |""".stripMargin
          )
        } else println("Try to rescue through random action...")
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
}
