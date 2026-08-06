package org.jetbrains.plugins.scala.lang.parser.parsing.builder

import com.intellij.lang.{PsiBuilder, WhitespacesAndCommentsBinder}
import com.intellij.psi.tree.IElementType

trait ErrorTrackingMarkerParent {
  def reportErrors(errorCount: Int): Unit
  def dropFromStack(): Int
}

class ErrorTrackingMarker(inner: PsiBuilder.Marker, builder: ScalaPsiBuilderImpl)
  extends PsiBuilder.Marker with ErrorTrackingMarkerParent {
  var errors = 0
  var droppedFromStack = false

  private var wasFinishedWithError = false

  override def reportErrors(errorCount: Int): Unit =
    errors += errorCount

  override def precede(): PsiBuilder.Marker = {
    val marker = new ErrorTrackingMarker(inner.precede(), builder)
    builder.pushPrecedeErrorTrackingMarker(marker, this)
    marker
  }

  override def doneBefore(`type`: IElementType, before: PsiBuilder.Marker): Unit = {
    inner.doneBefore(`type`, before)
    builder.finishErrorTrackingMarker(this)
  }

  override def doneBefore(`type`: IElementType, before: PsiBuilder.Marker, errorMessage: String): Unit = {
    inner.doneBefore(`type`, before, errorMessage)
    builder.finishErrorTrackingMarker(this)
  }

  override def error(message: String): Unit = {
    wasFinishedWithError = true
    inner.error(message)
    // We do not finish this tracker here because it can still be dropped afterward
    //builder.finishErrorTrackingMarker(this)
  }

  override def errorBefore(message: String, before: PsiBuilder.Marker): Unit = {
    errors += 1
    inner.errorBefore(message, before)
    builder.finishErrorTrackingMarker(this)
  }

  override def drop(): Unit = {
    inner.drop()
    builder.finishErrorTrackingMarker(this, isDrop = true)
  }

  override def rollbackTo(): Unit = {
    // don't report any errors
    inner.rollbackTo()
    builder.finishErrorTrackingMarker(this, isRollback = true)
  }

  override def done(`type`: IElementType): Unit = {
    inner.done(`type`)
    builder.finishErrorTrackingMarker(this)
  }

  override def collapse(`type`: IElementType): Unit = {
    // don't report any errors here
    inner.collapse(`type`)
    builder.finishErrorTrackingMarker(this)
  }

  override def setCustomEdgeTokenBinders(left: WhitespacesAndCommentsBinder, right: WhitespacesAndCommentsBinder): Unit =
    inner.setCustomEdgeTokenBinders(left, right)

  override def dropFromStack(): Int = {
    val additionalErrors = if (wasFinishedWithError) 1 else 0
    droppedFromStack = true
    errors + additionalErrors
  }
}
