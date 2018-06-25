package org.jetbrains.plugins.scala.worksheet.settings;

/**
 * User: Dmitry.Naydanov
 * Date: 19.06.18.
 */
public enum WorksheetRunType {
  PLAIN, REPL, REPL_CELL;
  
  public static boolean isReplRunType(WorksheetRunType runType) {
    return REPL.equals(runType) || REPL_CELL.equals(runType);
  }
}
