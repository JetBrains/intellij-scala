package org.jetbrains.plugins.hocon

import com.intellij.json.JsonFileType
import com.intellij.lang.properties.PropertiesFileType
import org.jetbrains.plugins.hocon.lang.HoconFileType

object HoconConstants {
  final val UrlQualifier = "url("
  final val FileQualifier = "file("
  final val ClasspathQualifier = "classpath("

  final val IncludeQualifiers = Set(UrlQualifier, ClasspathQualifier, FileQualifier)
  final val IntegerPattern = """-?(0|[1-9][0-9]*)""".r
  final val DecimalPartPattern = """([0-9]+)((e|E)(\+|-)?[0-9]+)?""".r
  final val ProperlyClosedQuotedString = ".*[^\\\\](\\\\\\\\)*\"".r
  final val MultilineStringEnd = "\"{3,}".r

  final val ConfExt = "." + HoconFileType.DefaultExtension
  final val JsonExt = "." + JsonFileType.DEFAULT_EXTENSION
  final val PropsExt = "." + PropertiesFileType.DEFAULT_EXTENSION
}
