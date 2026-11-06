package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtNotifications.FmtVerbosity
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.ScalafmtIndents
import org.scalafmt.dynamic.ScalafmtReflectConfig

private final class ScalafmtIndentsProviderImpl extends ScalafmtIndentsProvider {
  override def indents(file: PsiFile): Option[ScalafmtIndents] =
    ScalafmtDynamicConfigService.instanceIn(file.getProject)
      .configForFile(file, FmtVerbosity.FailSilent, resolveFast = true)
      .map(ScalafmtIndentsProviderImpl.indents)
}

private[scalafmt] object ScalafmtIndentsProviderImpl {
  def indents(config: ScalafmtReflectConfig): ScalafmtIndents = {
    val default = ScalafmtIndents.Default
    ScalafmtIndents(
      config.indentMain.getOrElse(default.main),
      config.indentCallSite.getOrElse(default.callSite),
      config.indentDefnSite.getOrElse(default.defnSite),
    )
  }
}
