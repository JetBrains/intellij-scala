package org.jetbrains.plugins.scala.lang.refactoring

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.{Attachment, Logger}
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressManager, StandardProgressIndicator}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Key, Segment, TextRange}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.editor.copy.Scala3IndentationBasedSyntaxCopyPastePreProcessor.AfterIndentOffsetAdjuster
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.dependency.{Dependency, DependencyPath}
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder.ImportPath
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

import scala.collection.mutable
import scala.jdk.CollectionConverters.SeqHasAsJava

final class Associations private(override val associations: Array[Association])
  extends AssociationsData(associations, Associations)
    with Cloneable {

  import Associations._

  override def clone(): Associations = new Associations(associations)

  override def canEqual(other: Any): Boolean = other.is[Associations]

  override def toString = s"Associations(${associations.mkString("Array(", ", ", ")")})"

  def restore(segment: Segment, adjuster: AfterIndentOffsetAdjuster)
             (filter: Seq[Binding] => Seq[Binding])
             (implicit project: Project, file: PsiFile): Unit = {
    val bindings = getBindingsForOffset(segment.getStartOffset)(file, adjuster)
    val bindingsDistinct = bindings.distinct
    val bindingsToRestore = filter(bindingsDistinct)

    if (bindingsToRestore.nonEmpty) {
      val references = bindingsToRestore.map(_.reference)
      val importPaths = bindingsToRestore.map(b => ImportPath(b.path, b.aliasName))

      val commonParent = PsiTreeUtil.findCommonParent(references.asJava)
      if (commonParent != null) {
        val importsHolder = ScImportsHolder(commonParent)(project)
        inWriteAction {
          importsHolder.addImportsForPaths(importPaths, commonParent)
        }
      }
    }
  }

  private def getBindingsForOffset(offset: Int)
                                  (implicit file: PsiFile, adjuster: AfterIndentOffsetAdjuster): Seq[Binding] = for {
    association <- associations.toSeq
    reference <- referenceFor(association, offset)

    path = association.path.asString()
    if hasNonDefaultPackage(path)
  } yield Binding(reference, path)

  @RequiresEdt
  private[scala] def restoreOnUiThread(segment: Segment, adjuster: AfterIndentOffsetAdjuster)
                                      (filter: Seq[Binding] => Seq[Binding])
                                      (implicit project: Project, file: PsiFile): Unit = {
    implicit val adjuster0: AfterIndentOffsetAdjuster = adjuster
    val bindings = {
      var result: Seq[Binding] = Seq.empty
      val title = ScalaBundle.message("processing.imports.title")
      val performUnderProgress: java.util.function.Consumer[com.intellij.openapi.progress.ProgressIndicator] = { indicator =>
        indicator.setIndeterminate(false)
        indicator.setFraction(0)
        result = getBindingsForOffsetUnderProgress(indicator)(segment.getStartOffset)
      }

      //noinspection ApiStatus
      ApplicationManagerEx.getApplicationEx.runWriteActionWithCancellableProgressInDispatchThread(
        title, project, null, performUnderProgress)

      result
    }

    val bindingsDistinct = bindings.distinct
    val bindingsToRestore = filter(bindingsDistinct)

    if (bindingsToRestore.nonEmpty) {
      val references = bindingsToRestore.map(_.reference)
      val importPaths = bindingsToRestore.map(b => ImportPath(b.path, b.aliasName))

      val commonParent = PsiTreeUtil.findCommonParent(references.asJava)
      if (commonParent != null) {
        val importsHolder = ScImportsHolder(commonParent)(project)
        inWriteAction {
          importsHolder.addImportsForPaths(importPaths, commonParent)
        }
      }
    }
  }

  private def getBindingsForOffsetUnderProgress(indicator: com.intellij.openapi.progress.ProgressIndicator)
                                               (offset: Int)
                                               (implicit file: PsiFile, adjuster: AfterIndentOffsetAdjuster): Seq[Binding] = {
    val size = associations.length
    associations.zipWithIndex.toSeq.flatMap { case (association, index) =>
      indicator.checkCanceled()
      val fraction = (index + 1).toDouble / size
      indicator.setFraction(fraction)

      val path = association.path.asString()
      if (hasNonDefaultPackage(path))
        referenceFor(association, offset).map(reference => Binding(reference, path))
      else
        None
    }
  }
}

object Associations extends AssociationsData.Companion(classOf[Associations], "ScalaReferenceData") {

  private val logger = Logger.getInstance(getClass)

  def apply(associations: Array[Association]) = new Associations(associations)

  def unapply(associations: Associations): Some[Array[Association]] =
    Some(associations.associations)

  //for testing purposes
  sealed trait BindingLike {
    def path: String
    def aliasName: Option[String]

    final def getInitAndLast: (String, String) = {
      val lastDotIdx = path.lastIndexOf('.')
      if (lastDotIdx < 0)
        ("_root_", path)
      else {
        val init = path.substring(0, lastDotIdx)
        val last = path.substring(lastDotIdx + 1)
        (init, last)
      }
    }
  }

  case class Binding(
    reference: ScReference,
    override val path: String
  ) extends BindingLike {
    override val aliasName: Option[String] = {
      val referenceText = Option(reference.nameId).map(_.getText)
      val lastNameInFqn = getInitAndLast._2
      referenceText.filter(_ != lastNameInFqn)
    }
  }

  @TestOnly
  case class MockBinding(
    path: String,
    aliasName: Option[String] = None
  ) extends BindingLike

  object Data {

    private val key: Key[Associations] = Key.create("ASSOCIATIONS")

    def apply(element: PsiElement): Associations = {
      val associations = element.getCopyableUserData(key)
      associations
    }

    def update(element: PsiElement, associations: Associations): Unit = {
      element.putCopyableUserData(key, associations)
    }
  }

  def shiftFor(element: PsiElement, offsetChange: Int): Unit = Data(element) match {
    case null =>
    case Associations(associations) =>
      associations.foreach { association =>
        association.range = association.range.shiftRight(offsetChange)
      }
  }

  def restoreFor(movedElement: PsiElement): Unit = restoreFor(movedElement, AfterIndentOffsetAdjuster.empty)
  def restoreFor(movedElement: PsiElement, adjuster: AfterIndentOffsetAdjuster): Unit = {
    val associations = Data(movedElement)
    if (associations != null) {
      try {
        associations.restore(movedElement.getTextRange, adjuster)(identity)(movedElement.getProject, movedElement.getContainingFile)
      } finally {
        Data(movedElement) = null
      }
    }
  }

  def collectAssociations(ranges: TextRange*)
                         (implicit file: ScalaFile): Associations = {
    val buffer = mutable.ArrayBuffer.empty[Association]
    var result: Associations = null
    try {
      ProgressManager.getInstance().runProcess(
        (() => {
          for {
            range <- ranges

            (path, references) <- Dependency.collect(range)
            reference <- references

          } {
            val referenceRange = reference.getTextRange.shiftRight(-range.getStartOffset)
            buffer += Association(path, referenceRange)
          }
        }): Runnable,
        new ProgressIndicator
      )
    } catch {
      case _: ProcessCanceledException =>
        logger.warn(
          s"""Time-out while collecting dependencies in ${file.name}:
             |${subText(ranges.head)}""".stripMargin
        )
      case e: Exception =>
        val attachments = ranges.zipWithIndex.map {
          case (range, index) => new Attachment(s"Selection-${index + 1}.scala", subText(range))
        }
        logger.error(e.getMessage, e, attachments: _*)
    } finally {
      result = Associations(buffer.toArray)
    }

    result
  }

  private class ProgressIndicator extends AbstractProgressIndicatorBase with StandardProgressIndicator {

    import System.currentTimeMillis

    private val timeBound = currentTimeMillis + ProgressIndicator.Timeout

    override final def isCanceled: Boolean = currentTimeMillis > timeBound || super.isCanceled

    override final def cancel(): Unit = super.cancel()

    override final def checkCanceled(): Unit = super.checkCanceled()
  }

  private object ProgressIndicator {

    private val Timeout = 3000L
  }

  private def subText(range: TextRange)
                     (implicit file: ScalaFile) =
    file.getText.substring(range.getStartOffset, range.getEndOffset)

  private def hasNonDefaultPackage(path: String) = path.lastIndexOf('.') match {
    case -1 => false
    case index => path.substring(0, index) match {
      case "scala" |
           "java.lang" |
           "scala.Predef" => false
      case _ => true
    }
  }

  private def referenceFor(association: Association, offset: Int)
                          (implicit file: PsiFile, adjuster: AfterIndentOffsetAdjuster): Option[ScReference] = {
    val Association(path, range) = association
    val shiftedRange = adjuster.adjust(range.shiftRight(offset), base = offset)

    for {
      leafElement <- Option(file.findElementAt(shiftedRange.getStartOffset))
      reference: ScReference <- Option(leafElement.getParent).filterByType[ScReference]
      if reference.getTextRange == shiftedRange
      if !hasDependenciesWithPath(reference, path)
    } yield reference
  }

  private def hasDependenciesWithPath(reference: ScReference, path: DependencyPath): Boolean = {
    val dependencies = Dependency.dependenciesFor(reference)
    dependencies.exists(_.path == path)
  }
}