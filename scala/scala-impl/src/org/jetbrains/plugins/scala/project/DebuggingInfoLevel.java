package org.jetbrains.plugins.scala.project;

/**
 * @author Pavel Fatin
 */
public enum DebuggingInfoLevel implements Named {
  None("none"),
  Source("source"),
  Line("line"),
  Vars("vars"),
  Notailcalls("notailcalls");

  private final String myOption;
  
  DebuggingInfoLevel(String option) {
    myOption = option;
  }

  public String getName() {
    return getDescription(this);
  }

  public String getOption() {
    return myOption;
  }

  private static String getDescription(DebuggingInfoLevel level) {
    return DebuggingInfoLevelDescription.get(level);
  }
}
