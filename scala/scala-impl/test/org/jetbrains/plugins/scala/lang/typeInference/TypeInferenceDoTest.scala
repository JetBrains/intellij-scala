package org.jetbrains.plugins.scala
package lang
package typeInference

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.base.{FailableTest, ScalaSdkOwner}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext
import org.jetbrains.plugins.scala.lang.psi.types.api.TypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.junit.Assert._
import org.junit.experimental.categories.Category

/**
  * Created by Svyatoslav Ilinskiy on 01.07.16.
  */
@Category(Array(classOf[TypecheckerTests]))
trait TypeInferenceDoTest extends FailableTest with ScalaSdkOwner {
  protected val START = "/*start*/"
  protected val END = "/*end*/"
  private val fewVariantsMarker = "Few variants:"
  private val ExpectedPattern = """expected: (.*)""".r
  private val SimplifiedPattern = """simplified: (.*)""".r
  private val JavaTypePattern = """java type: (.*)""".r

  def configureFromFileText(fileName: String, fileText: Option[String]): ScalaFile

  protected def doTest(fileText: String): Unit = doTest(Some(fileText))

  protected def doTest(fileText: Option[String], fileName: String = "dummy.scala"): Unit = {
    val scalaFile: ScalaFile = configureFromFileText(fileName, fileText)
    val expr: ScExpression = findExpression(scalaFile)
    implicit val tpc: TypePresentationContext = TypePresentationContext.emptyContext
    val typez = expr.`type`() match {
      case Right(t) if t.isUnit => expr.getTypeIgnoreBaseType
      case x => x
    }
    typez match {
      case Right(ttypez) =>
        val res = ttypez.presentableText
        val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)
        val text = lastPsi.getText
        val output = lastPsi.getNode.getElementType match {
          case ScalaTokenTypes.tLINE_COMMENT => text.substring(2).trim
          case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tDOC_COMMENT =>
            val resText = extractTextForCurrentVersion(text.substring(2, text.length - 2).trim, version)

            if (resText.startsWith(fewVariantsMarker)) {
              val results = resText.substring(fewVariantsMarker.length).trim.split('\n')
              if (!results.contains(res)) assertEqualsFailable(results(0), res)
              return
            } else resText
          case _ =>
            throw new AssertionError("Test result must be in last comment statement.")
        }
        output match {
          case ExpectedPattern(expectedExpectedTypeText) =>
            val actualExpectedTypeText = expr.expectedType().map(_.presentableText).getOrElse("<none>")
            assertEqualsFailable(expectedExpectedTypeText, actualExpectedTypeText)
          case SimplifiedPattern(expectedText) =>
            assertEqualsFailable(expectedText, TypePresentation.withoutAliases(ttypez))
          case JavaTypePattern(expectedText) =>
            assertEqualsFailable(expectedText, expr.`type`().map(_.toPsiType.getPresentableText()).getOrElse("<none>"))
          case _ => assertEqualsFailable(output, res)
        }
      case Failure(msg) if shouldPass => fail(msg)
      case _ =>
    }
  }

  def findExpression(scalaFile: ScalaFile): ScExpression = {
    val fileText = scalaFile.getText
    val offset = fileText.indexOf(START)
    val startOffset = offset + START.length
    assert(offset != -1, "Not specified start marker in test case. Use /*start*/ in scala file for this.")
    val endOffset = fileText.indexOf(END)
    assert(endOffset != -1, "Not specified end marker in test case. Use /*end*/ in scala file for this.")

    val addOne = if (PsiTreeUtil.getParentOfType(scalaFile.findElementAt(startOffset), classOf[ScExpression]) != null) 0 else 1 //for xml tests
    val expr: ScExpression = PsiTreeUtil.findElementOfClassAtRange(scalaFile, startOffset + addOne, endOffset, classOf[ScExpression])
    assert(expr != null, "Not specified expression in range to infer type.")
    expr
  }

  private val VersionPrefixRegex = """^\[Scala_([\w\d_]*)\](.*)""".r

  // formats:
  // 2_12 => 2.12.MAX_VERSION,
  // 2_12_7 => 2.12.7
  private def selectVersion(versionStr: String): Option[ScalaVersion] = {
    val versionStrWithDots = versionStr.replace('_', '.')
    ScalaSdkOwner.allTestVersions.find(_.minor == versionStrWithDots)
      .orElse(ScalaSdkOwner.allTestVersions.filter(_.major == versionStrWithDots).lastOption)
  }

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
