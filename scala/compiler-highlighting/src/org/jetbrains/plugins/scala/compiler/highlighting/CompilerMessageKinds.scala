package org.jetbrains.plugins.scala.compiler.highlighting

import org.apache.commons.lang3.{StringUtils, Strings}
import org.jetbrains.jps.incremental.scala.MessageKind

private object CompilerMessageKinds {
  def highlightInfoType(kind: MessageKind, text: String, fatalWarningsFlag: Boolean, unusedImportsFlag: Boolean): HighlightInfoType = kind match {
    case MessageKind.Error if isErrorMessageAboutWrongRef(text) =>
      HighlightInfoType.WrongRef
    case MessageKind.Error if isUnusedImportMessage(text) =>
      if (fatalWarningsFlag && unusedImportsFlag) {
        // The project has enabled fatal warnings and unused imports. We need to report an error here.
        HighlightInfoType.Error
      } else {
        // In all other cases, report an unused symbol instead of an error.
        // The else case contains the following cases:
        //   1. fatalWarnings && !unusedImports (silent unused imports added by us) => UNUSED_SYMBOL
        //   2. !fatalWarnings && unusedImports (cannot happen, because the unused import will be a warning, not an error)
        //   3. !fatalWarnings && !unusedImports (silent unused imports added by us) => UNUSED_SYMBOL
        HighlightInfoType.UnusedSymbol
      }
    case MessageKind.Error =>
      HighlightInfoType.Error
    case MessageKind.Warning if isUnusedImportMessage(text) =>
      if (fatalWarningsFlag && unusedImportsFlag) {
        // The project has enabled fatal warnings and unused imports. We need to report an error here.
        HighlightInfoType.Error
      } else if (unusedImportsFlag) {
        // The project has enabled unused imports, and fatal warnings are not enabled. Keep the warning.
        HighlightInfoType.Warning
      } else {
        // Silent unused imports added by us.
        HighlightInfoType.UnusedSymbol
      }
    case MessageKind.Warning if fatalWarningsFlag =>
      HighlightInfoType.Error
    case MessageKind.Warning =>
      HighlightInfoType.Warning
    case MessageKind.Info =>
      HighlightInfoType.WeakWarning
    case _ =>
      HighlightInfoType.Information
  }

  private def isErrorMessageAboutWrongRef(text: String): Boolean =
    Strings.CI.startsWith(text, "value") && text.contains("is not a member of") ||
      Strings.CI.startsWith(text, "not found:") ||
      Strings.CI.startsWith(text, "cannot find symbol")

  private def isUnusedImportMessage(text: String): Boolean = {
    val description = CompilerMessages.description(text)
    CompilerMessages.isUnusedImport(description)
  }
}
