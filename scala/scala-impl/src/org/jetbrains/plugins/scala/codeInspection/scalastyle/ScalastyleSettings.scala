package org.jetbrains.plugins.scala.codeInspection.scalastyle

import java.util.regex.Pattern

case class ScalastyleSettings(scalastyleOrder: Boolean, groups: Option[Seq[Pattern]])
