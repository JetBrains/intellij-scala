package org.jetbrains.plugins.scala.lang.typeInference

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestFixture.extractTextForCurrentVersion
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ExpectedResultFromLastComment
import org.jetbrains.plugins.scala.util.assertions.PsiAssertions.assertNoParserErrors
import org.junit.Assert
import org.junit.Assert._

/**
 * @param scalaVersion scala version is required because some test data defines multiple expected types
 *                     for different scala versions using a special comment, like "[Scala_2_13]Int"
 * @param shouldPass this parameter exists because [[org.jetbrains.plugins.scala.base.FailableTest]] exists
 *                   we need to get rid of both
 */
final class TypeInferenceTestFixture(
  scalaVersion: ScalaVersion,
  shouldPass: Boolean = true
) {
  val START_MARKER = "/*start*/"
  val END_MARKER = "/*end*/"

  private val ExpectedPattern = """expected: (.*)""".r
  private val SimplifiedPattern = """simplified: (.*)""".r
  private val JavaTypePattern = """java type: (.*)""".r

  //Used in expected data, some types may be rendered in different ways, e.g. `A with B` or `B with A`
  private val FewVariantsMarker = "Few variants:"

  def doTest(
    scalaFile: ScalaFile,
    failOnParserErrorsInFile: Boolean = true
  ): Unit = {
    if (failOnParserErrorsInFile) {
      assertNoParserErrors(scalaFile)
    }

    val expression = findSelectedExpression(scalaFile)
    val expressionTypeResult = expression.`type`() match {
      case Right(t) if t.isUnit => expression.getTypeIgnoreBaseType
      case x => x
    }
    expressionTypeResult match {
      case Right(expressionType) =>
        val ExpectedResultFromLastComment(_, lastLineCommentText) = TestUtils.extractExpectedResultFromLastComment(scalaFile)
        val expectedTextForCurrentVersion = extractTextForCurrentVersion(lastLineCommentText, scalaVersion)

        implicit val tpc: TypePresentationContext = TypePresentationContext.emptyContext
        val expressionTypeText = expressionType.presentableText

        if (expectedTextForCurrentVersion.startsWith(FewVariantsMarker)) {
          val results = expectedTextForCurrentVersion.substring(FewVariantsMarker.length).trim.split('\n')
          if (!results.contains(expressionTypeText))
            assertEqualsFailable(results(0), expressionTypeText)
        }

        else expectedTextForCurrentVersion match {
          case ExpectedPattern(expectedExpectedTypeText) =>
            val actualExpectedTypeText = expression.expectedType().map(_.presentableText).getOrElse("<none>")
            assertEqualsFailable(expectedExpectedTypeText, actualExpectedTypeText)
          case SimplifiedPattern(expectedText) =>
            assertEqualsFailable(expectedText, TypePresentation.withoutAliases(expressionType))
          case JavaTypePattern(expectedText) =>
            assertEqualsFailable(expectedText, expression.`type`().map(_.toPsiType.getPresentableText()).getOrElse("<none>"))
          case _ =>
            assertEqualsFailable(expectedTextForCurrentVersion, expressionTypeText)
        }
      case Failure(msg) if shouldPass => fail(msg)
      case _ =>
    }
  }

  //copy of org.jetbrains.plugins.scala.base.FailableTest.assertEqualsFailable
  protected def assertEqualsFailable(expected: AnyRef, actual: AnyRef): Unit = {
    if (shouldPass) Assert.assertEquals(expected, actual)
    else Assert.assertNotEquals(expected, actual)
  }

  def findSelectedExpression(scalaFile: ScalaFile): ScExpression = {
    val fileText = scalaFile.getText

    val startMarkerOffset = fileText.indexOf(START_MARKER)
    val startOffset = startMarkerOffset + START_MARKER.length
    assert(startMarkerOffset != -1, "Not specified start marker in test case. Use /*start*/ in scala file for this.")

    val endOffset = fileText.indexOf(END_MARKER)
    assert(endOffset != -1, "Not specified end marker in test case. Use /*end*/ in scala file for this.")

    val elementAtOffset = PsiTreeUtil.getParentOfType(scalaFile.findElementAt(startOffset), classOf[ScExpression])
    val addOne = if (elementAtOffset != null) 0 else 1 //for xml tests
    val expr: ScExpression = PsiTreeUtil.findElementOfClassAtRange(scalaFile, startOffset + addOne, endOffset, classOf[ScExpression])
    assert(expr != null, "Not specified expression in range to infer type.")

    expr
  }
}

object TypeInferenceTestFixture {

  //example of a line: [Scala_2_13]2
  private val VersionPrefixRegex = """^\[Scala_([\w\d_]*)\](.*)""".r

  // formats:
  // 2_12 => 2.12.MAX_VERSION,
  // 2_12_7 => 2.12.7
  private def selectVersion(versionStr: String): Option[ScalaVersion] = {
    val versionStrWithDots = versionStr.replace('_', '.')
    val found = ScalaSdkOwner.allTestVersions.find(_.minor == versionStrWithDots)
    found.orElse(ScalaSdkOwner.allTestVersions.filter(_.major == versionStrWithDots).lastOption)
  }

  /**
   * Note, in some legacy type inference test a single test is used to define expected data for multiple scala versions
   * For example: {{{
   *   /*start*/1 + 1/*end*/
   *   /*
   *   Int
   *   [Scala_2_13]2
   *   */
   * }}}
   *
   */
  private def extractTextForCurrentVersion(text: String, version: ScalaVersion): String = {
    val lines = text.split('\n')
    val ((lastVer, lastText), resultListWithoutLast) = lines
      .foldLeft(((Option.empty[ScalaVersion], ""), Seq.empty[(Option[ScalaVersion], String)])) {
        case (((curver, curtext), result), line) =>
          val foundVersion = line match {
            case VersionPrefixRegex(versionStr, tail) => selectVersion(versionStr.trim).map((_, tail))
            case _                                    => None
          }
          foundVersion match {
            case Some((v, lineTail)) => (Some(v), lineTail) -> (result :+ (curver -> curtext))
            case None                => (curver, if (curtext.isEmpty) line else curtext + "\n" + line) -> result
          }
      }

    val resultList = resultListWithoutLast :+ (lastVer -> lastText)
    if (resultList.length == 1) {
      resultList.head._2
    } else {
      val resultsWithVersions = resultList
        .flatMap {
          case (Some(v), text) => Some(v -> text)
          case (None, text) => Some(ScalaSdkOwner.allTestVersions.head -> text)
        }

      assert(resultsWithVersions.map(_._1).sliding(2).forall { case Seq(a, b) => a < b})

      resultsWithVersions.zip(resultsWithVersions.tail)
        .find { case ((v1, _), (v2, _)) => v1 <= version && version < v2 }
        .map(_._1._2)
        .getOrElse(resultsWithVersions.last._2)
    }
  }
}
