package org.jetbrains.plugins.scala.refactoring.delete

import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.listeners.{RefactoringEventListener, RefactoringEventData}
import com.intellij.refactoring.safeDelete.SafeDeleteHandler
import org.jetbrains.plugins.scala.AssertionMatchers
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.normalize
import org.jetbrains.plugins.scala.util.Markers

import scala.jdk.CollectionConverters._
import scala.util.Try

abstract class ScalaSafeDeleteTestBase extends ScalaLightCodeInsightFixtureTestAdapter with Markers with AssertionMatchers {
  private def wrapText(content: String): String = {
    val contentText = normalize(content).replace("\n", "\n  ").replace("  \n", "\n")
    val classText =
      s"""
         |class Test {
         |  $contentText
         |}
         |""".stripMargin.trim
    normalize(classText)
  }

  def doSafeDeleteTest(text: String, expectedResult: String, lang: String = "scala", expectedUnsafeDeletions: Int = 0): Unit = {
    configureFromFileText(wrapText(text), lang)

    val foundNotSafeToDeletes = notSafeToDeletesIn {
      val element = getFixture.getElementAtCaret
      BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts( () =>
        SafeDeleteHandler.invoke(getProject, Array(element), true)
      )
    }

    getFile.getText shouldBe wrapText(expectedResult)
    foundNotSafeToDeletes shouldBe expectedUnsafeDeletions
  }

  private def notSafeToDeletesIn(body: => Unit): Int = {
    var unsafeDeletions = 0
    val busConnection = getProject.getMessageBus.connect()
    try {
      busConnection.subscribe(RefactoringEventListener.REFACTORING_EVENT_TOPIC, new RefactoringEventListener {
        override def refactoringStarted(refactoringId: String, beforeData: RefactoringEventData): Unit = ()
        override def refactoringDone(refactoringId: String, afterData: RefactoringEventData): Unit = ()
        override def conflictsDetected(refactoringId: String, conflictsData: RefactoringEventData): Unit =
          if (refactoringId == "refactoring.safeDelete") {
            val conflicts = conflictsData.getUserData(RefactoringEventData.CONFLICTS_KEY).asScala.toArray
            unsafeDeletions shouldBe 0
            conflicts.length shouldBe 1
            unsafeDeletions = conflicts.head
              .split(" ")
              .flatMap(s => Try(s.toInt).toOption)
              .head
          }
        override def undoRefactoring(refactoringId: String): Unit = ()
      })

      body
    } finally busConnection.disconnect()

    unsafeDeletions
  }
}
