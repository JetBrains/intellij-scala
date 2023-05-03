package org.jetbrains.plugins.scala.refactoring.delete

import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.listeners.{RefactoringEventData, RefactoringEventListener}
import com.intellij.refactoring.safeDelete.SafeDeleteHandler
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{ObjectExt, StringExt}
import org.jetbrains.plugins.scala.util.Markers
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers

import scala.jdk.CollectionConverters._
import scala.util.Try

abstract class ScalaSafeDeleteTestBase extends ScalaLightCodeInsightFixtureTestCase with Markers with AssertionMatchers {
  protected def | : String = CARET

  private def wrapText(content: String): String = {
    val contentText = content.withNormalizedSeparator.trim.replace("\n", "\n  ").replace("  \n", "\n")
    val classText =
      s"""
         |class Test {
         |  $contentText
         |}
         |""".stripMargin.trim
    classText.withNormalizedSeparator.trim
  }

  def doSafeDeleteTest(text: String,
                       expectedResult: String,
                       fileType: String = "scala",
                       expectedUnsafeDeletions: Int = 0,
                       wrapTextInClass: Boolean = true): Unit = {
    configureFromFileTextWithSomeName(fileType, text.pipeIf(wrapTextInClass)(wrapText))

    val foundNotSafeToDeletes = notSafeToDeletesIn {
      val element = myFixture.getElementAtCaret
      BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() =>
        SafeDeleteHandler.invoke(getProject, Array(element), true)
      )
    }

    getFile.getText shouldBe expectedResult.pipeIf(wrapTextInClass)(wrapText)
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
