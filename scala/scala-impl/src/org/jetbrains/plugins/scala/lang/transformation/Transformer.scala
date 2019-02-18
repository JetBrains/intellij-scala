package org.jetbrains.plugins.scala.lang.transformation

import com.intellij.openapi.editor.{Document, RangeMarker}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, _}
import org.jetbrains.plugins.scala.lang.transformation.annotations._
import org.jetbrains.plugins.scala.lang.transformation.calls._
import org.jetbrains.plugins.scala.lang.transformation.general._
import org.jetbrains.plugins.scala.lang.transformation.implicits._
import org.jetbrains.plugins.scala.lang.transformation.references._
import org.jetbrains.plugins.scala.lang.transformation.types._

import scala.collection.JavaConverters._

/**
  * @author Pavel Fatin
  */
trait Transformer {
  // TODO return updated element instead of Boolean to enable fine-grained recursion
  protected def transform(e: PsiElement): Boolean

  def isApplicableTo(e: PsiElement): Boolean

  def needsReformat(e: PsiElement): Boolean = false
}

object Transformer {
  private val RecursionDepthThreshold = 10

  type ReformatAction = (=> List[TextRange], PsiFile, Document) => Unit

  def defaultReformat(ranges: => List[TextRange], file: PsiFile, document: Document): Unit = {
    val project = file.getProject
    val documentManager = PsiDocumentManager.getInstance(project)
    documentManager.doPostponedOperationsAndUnblockDocument(document)
    CodeStyleManager.getInstance(project).reformatText(file, ranges.asJavaCollection)
  }

  // TODO rely on a single set of transformers, use different means of ordering transformer applications
  // In principle, we can rely on the SelectionDialog's set of transformers to produce the equivalent result.
  // We use this second collection to minimize number of intermediate transformations by ordering the transformers.
  private def createFullSet: Set[Transformer] = Set(
    new AppendSemicolon(),
    new ExpandFunctionType(),
    new ExpandTupleType(),
    new ExpandStringInterpolation(),
    new ExpandForComprehension(),
    new ExpandApplyCall(),
    new ExpandUpdateCall(),
    new ExpandUnaryCall(),
    new ExpandAssignmentCall(),
    new ExpandDynamicCall(),
    new CanonizeInfixCall(),
    new CanonizePostifxCall(),
    new CanonizeZeroArityCall(),
    new ExpandSetterCall(),
    new CanonizeBlockArgument(),
    new ExpandAutoTupling(),
//    new ExpandVarargArgument(),
    new ExpandTupleInstantiation(),
    new ExpandImplicitConversion(),
    new InscribeImplicitParameters(),
    new AddTypeToFunctionParameter(),
    new AddTypeToMethodDefinition(),
    new AddTypeToUnderscoreParameter(),
    new AddTypeToValueDefinition(),
    new AddTypeToVariableDefinition(),
    new AddTypeToReferencePattern(),
    new PartiallyQualifySimpleReference()
  )

  // TODO use in debugger's "evaluate expression" to simpify its code and to support many language features automatically (e.g. string interpolation)
  // TODO support fine-grained recursion with dependencies
  def applyTransformersAndReformat(e: PsiElement,
                                   file: PsiFile,
                                   range: Option[RangeMarker] = None,
                                   transformers: Traversable[Transformer] = createFullSet,
                                   reformat: ReformatAction = defaultReformat): Unit = {
    var markers = List.empty[RangeMarker]

    try {
      val document = file.getViewProvider.getDocument

      val resultIterator = Iterator.continually {
        transformers.foldLeft(false) { (hadApply, transformer) =>
          val elementIterator = range.map(elementsIn(file, _)).getOrElse(file.depthFirst())
          val hasApply = elementIterator.foldLeft(false) { (hadApply, element) =>
            val (result, marker) = applyTransformer(element, transformer, document)
            marker.foreach(markers ::= _)
            hadApply || result
          }
          hadApply || hasApply
        }
      }

      resultIterator.take(RecursionDepthThreshold).contains(false)

      if (markers.nonEmpty) {
        def ranges = markers.withFilter(_.isValid).map(_.getTextRange).filter(!_.isEmpty)
        reformat(ranges, file, document)
      }
    } finally {
      markers.foreach(_.dispose())
    }
  }

  def applyTransformerAndReformat(e: PsiElement,
                                  file: PsiFile,
                                  transformer: Transformer,
                                  reformat: ReformatAction = defaultReformat): Boolean = {
    val document = file.getViewProvider.getDocument
    val result@(_, marker) = applyTransformer(e, transformer, document)

    try {
      result match {
        case (true, Some(marker)) =>
          reformat(marker.getTextRange :: Nil, file, document)
          true
        case (result, _) =>
          result
      }
    } finally {
      marker.foreach(_.dispose())
    }
  }

  private def applyTransformer(e: PsiElement, transformer: Transformer, document: Document): (Boolean, Option[RangeMarker]) = {
    if (transformer.needsReformat(e)) {
      val marker = document.createRangeMarker(e.getTextRange)
      marker.setGreedyToLeft(true)
      marker.setGreedyToRight(true)

      if (transformer.transform(e)) true -> Some(marker)
      else false -> None
    } else {
      transformer.transform(e) -> None
    }
  }

  private def elementsIn(file: PsiElement, range: RangeMarker): Iterator[PsiElement] = {
    val first = file.findElementAt(range.getStartOffset)
    val last = file.findElementAt(range.getEndOffset)
    val parent = PsiTreeUtil.findCommonParent(first, last)
    parent.depthFirst().filter(e => contains(range, e.getTextRange))
  }

  private def contains(marker: RangeMarker, range: TextRange): Boolean =
    range.getStartOffset >= marker.getStartOffset &&
      range.getEndOffset <= marker.getEndOffset
}