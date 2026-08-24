package org.jetbrains.plugins.scala.codeInspection.scaladoc

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
abstract class ScalaDocUnclosedTagWithoutParserInspectionTestBase extends ScalaInspectionTestBase {

  override protected val classOfInspection = classOf[ScalaDocUnclosedTagWithoutParserInspection]
  override protected val description = "Unclosed Tag"

  protected final def checkClosedWikiLinkFollowedByFormattedText(useMarkdownSyntax: Boolean): Unit = {
    val syntaxTag = if (useMarkdownSyntax) " * @syntax markdown\n *\n" else ""

    checkTextHasNoErrors(
      s"""/**
        |$syntaxTag * [[scala.Predef.String]] suffix
        | *
        | * [[scala.Predef.String]] `suffix`
        | *
        | * [[scala.Predef.String]] _suffix_
        | *
        | * [[scala.Predef.String]] *suffix*
        | *
        | * [[scala.Predef.String]] **suffix**
        | */
        |class ScaladocExample
        |""".stripMargin
    )
  }

  protected final def checkMarkdownAutolinksAndImagesDoNotTriggerUnclosedTag(useMarkdownSyntax: Boolean): Unit = {
    val syntaxTag = if (useMarkdownSyntax) " * @syntax markdown\n *\n" else ""

    checkTextHasNoErrors(
      s"""/**
        |$syntaxTag * <https://example.com>
        | *
        | * [[scala.Predef.String]] <https://example.com>
        | *
        | * [[scala.Predef.String]] ![alt](image.png)
        | */
        |class ScaladocExample
        |""".stripMargin
    )
  }

  def testClosedWikiLinkFollowedByFormattedText(): Unit =
    checkClosedWikiLinkFollowedByFormattedText(useMarkdownSyntax = false)
}

class ScalaDocUnclosedTagWithoutParserInspectionTest
  extends ScalaDocUnclosedTagWithoutParserInspectionTestBase {

  def testClosedWikiLinkFollowedByFormattedText_InMarkdownSyntax(): Unit =
    checkClosedWikiLinkFollowedByFormattedText(useMarkdownSyntax = true)

  def testMarkdownAutolinksAndImagesDoNotTriggerUnclosedTag(): Unit =
    checkMarkdownAutolinksAndImagesDoNotTriggerUnclosedTag(useMarkdownSyntax = true)
}

class ScalaDocUnclosedTagWithoutParserInspectionTest_Scala3
  extends ScalaDocUnclosedTagWithoutParserInspectionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def testMarkdownAutolinksAndImagesDoNotTriggerUnclosedTag(): Unit =
    checkMarkdownAutolinksAndImagesDoNotTriggerUnclosedTag(useMarkdownSyntax = false)
}
