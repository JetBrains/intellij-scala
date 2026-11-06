package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.ScalafmtIndents

/**
 * Provides the indents of the scalafmt configuration of a file to the native formatter.
 *
 * The scalafmt configuration can only be resolved on the backend, so the implementation is registered there.
 */
@ApiStatus.Internal
trait ScalafmtIndentsProvider {
  /** @return the indents from the scalafmt configuration of `file`, if it can be resolved */
  def indents(file: PsiFile): Option[ScalafmtIndents]
}

@ApiStatus.Internal
object ScalafmtIndentsProvider {
  /** @return the indents from the scalafmt configuration of `file`, or the defaults if it's unavailable (e.g. on the client) */
  def indentsFor(file: PsiFile): ScalafmtIndents =
    Option(file.getProject.getService(classOf[ScalafmtIndentsProvider]))
      .flatMap(_.indents(file))
      .getOrElse(ScalafmtIndents.Default)
}
