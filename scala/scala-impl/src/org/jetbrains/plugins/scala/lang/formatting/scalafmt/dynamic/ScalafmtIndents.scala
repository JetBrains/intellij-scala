package org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic

import org.scalafmt.dynamic.ScalafmtReflectConfig

/** short version of `org.scalafmt.config.Indents` (3.0.0) */
case class ScalafmtIndents(
  main: Int,
  callSite: Int,
  defnSite: Int,
)

object ScalafmtIndents {
  /** used as a fallback in various cases */
  val Default: ScalafmtIndents = ScalafmtIndents(
    main = 2,
    callSite = 2,
    defnSite = 4
  )

  def apply(config: ScalafmtReflectConfig): ScalafmtIndents =
    new ScalafmtIndents(
      config.indentMain.getOrElse(Default.main),
      config.indentCallSite.getOrElse(Default.callSite),
      config.indentDefnSite.getOrElse(Default.defnSite),
    )

}
