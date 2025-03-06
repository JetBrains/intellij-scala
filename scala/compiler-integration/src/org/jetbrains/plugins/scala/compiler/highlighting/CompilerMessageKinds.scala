package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import org.apache.commons.lang3.StringUtils
import org.jetbrains.jps.incremental.scala.MessageKind

private object CompilerMessageKinds {
  def highlightInfoType(kind: MessageKind, text: String, fatalWarningsFlag: Boolean, unusedImportsFlag: Boolean): HighlightInfoType = kind match {
    case MessageKind.Error if isErrorMessageAboutWrongRef(text) =>
      HighlightInfoType.WRONG_REF
    case MessageKind.Error if isUnusedImportMessage(text) =>
      if (fatalWarningsFlag && unusedImportsFlag) {
        // The project has enabled fatal warnings and unused imports. We need to report an error here.
        HighlightInfoType.ERROR
      } else {
        // In all other cases, report an unused symbol instead of an error.
        // The else case contains the following cases:
        //   1. fatalWarnings && !unusedImports (silent unused imports added by us) => UNUSED_SYMBOL
        //   2. !fatalWarnings && unusedImports (cannot happen, because the unused import will be a warning, not an error)
        //   3. !fatalWarnings && !unusedImports (silent unused imports added by us) => UNUSED_SYMBOL
        HighlightInfoType.UNUSED_SYMBOL
      }
    case MessageKind.Error =>
      HighlightInfoType.ERROR
    case MessageKind.Warning if isUnusedImportMessage(text) =>
      if (fatalWarningsFlag && unusedImportsFlag) {
        // The project has enabled fatal warnings and unused imports. We need to report an error here.
        HighlightInfoType.ERROR
      } else if (unusedImportsFlag) {
        // The project has enabled unused imports, and fatal warnings are not enabled. Keep the warning.
        HighlightInfoType.WARNING
      } else {
        // Silent unused imports added by us.
        HighlightInfoType.UNUSED_SYMBOL
      }
    case MessageKind.Warning if fatalWarningsFlag =>
      HighlightInfoType.ERROR
    case MessageKind.Warning =>
      HighlightInfoType.WARNING
    case MessageKind.Info =>
      HighlightInfoType.WEAK_WARNING
    case _ =>
      HighlightInfoType.INFORMATION
  }

  private def isErrorMessageAboutWrongRef(text: String): Boolean =
    StringUtils.startsWithIgnoreCase(text, "value") && text.contains("is not a member of") ||
      StringUtils.startsWithIgnoreCase(text, "not found:") ||
      StringUtils.startsWithIgnoreCase(text, "cannot find symbol")

  private def isUnusedImportMessage(text: String): Boolean = {
    val description = CompilerMessages.description(text)
    CompilerMessages.isUnusedImport(description)
  }
}
