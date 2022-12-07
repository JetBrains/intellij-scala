package org.jetbrains.plugins.scala.lang.resolve2

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.junit.Assert.{assertNotNull, assertTrue, fail}

import scala.collection.mutable.ArrayBuffer
import scala.util.matching.Regex

abstract class ResolveTestBaseWithAlternativeExpectedData extends ResolveTestBase {

  //Example: ## file: this, offset: 27, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass ||| file: this, offset: 89, type: org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject, name: MyClassWithCompanion ##
  private val ExpectedResolvedTargetParamsRegex = """##\s*(.+)\s*##""".r

  override protected def configureReferences(): Seq[ReferenceWithExpectedResolveResult] = {
    val result = new ArrayBuffer[ReferenceWithExpectedResolveResult]

    val commentsMatches: Iterator[Regex.Match] =
      ExpectedResolvedTargetParamsRegex.findAllIn(getFile.getText).matchData

    for (commentMatch <- commentsMatches) {
      val expectedTargetParameterGroupsText = commentMatch.group(1)
      val reference = findSingleRootReferenceAtTheSameLine(getFile, commentMatch.start)

      assertNotNull("No reference found at offset " + commentMatch.end, reference)

      val targetInfosBuffer = new ArrayBuffer[TargetInfo]

      val expectedTargetParameterGroups = ParametersGroupSeparatorRegex.split(expectedTargetParameterGroupsText).map(_.trim)
      for (parametersText <- expectedTargetParameterGroups) {
        val parameters = parseParameters(parametersText)
        assertKnown(parameters)
        targetInfosBuffer += parameters
      }
      val targetInfos = targetInfosBuffer.toSeq
      val expectedResolveResult = ExpectedResolveResult(shouldBeResolved = targetInfos.forall(_.shouldBeResolved), targetInfos)
      result += ReferenceWithExpectedResolveResult(reference, expectedResolveResult)
    }

    assertTrue("At least one reference is expected", result.nonEmpty)
    assertTrue("Every resolved reference must have at least one expected target", result.forall { x =>
      !x.expectedResolveResult.shouldBeResolved || x.expectedResolveResult.targets.nonEmpty
    })

    result.toSeq
  }

  private def findSingleRootReferenceAtTheSameLine(file: PsiFile, caretOffset: Int): ScReference = {
    val document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile)
    val lineNumber = document.getLineNumber(caretOffset)
    val lineStartOffset = document.getLineStartOffset(lineNumber)
    val lineEndOffset = document.getLineEndOffset(lineNumber)


    val elementsAtLine = file
      .depthFirst()
      .filter { el =>
        val range = el.getTextRange
        val startOffset = range.getStartOffset
        lineStartOffset <= startOffset && startOffset < lineEndOffset
      }
      .toSeq
    //looking for root references (reference which is not part of another reference)
    //e.g. here: `/** [[object.method1.method2]] */ looking for `object.method1.method2`, not `object.method1`
    val rootReferences = elementsAtLine
      .filterByType[ScReference]
      .filter(e => !e.getParent.is[ScReference])
    rootReferences match {
      case Seq(singleRef) =>
        singleRef
      case Seq() =>
        fail(
          s"""Cannot find any references at line $lineNumber
             |Line text: ${document.getText.substring(lineStartOffset, lineEndOffset)}
             |Document text:
             |${document.getText}""".stripMargin
        ).asInstanceOf[Nothing]
      case manyRefs =>
        fail(
          s"""Found more then one reference at line $lineNumber:
             |All references:
             |${manyRefs.map(_.getText).mkString("\n")}
             |Document text:
             |${document.getText}""".stripMargin
        ).asInstanceOf[Nothing]
    }
  }
}
