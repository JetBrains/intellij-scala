package org.jetbrains.plugins.scala.project;

/**
 * @author Pavel Fatin
 */
public enum DebuggingInfoLevel implements Named {
  None("None", "none"),
  Source("Source file attribute", "source"),
  Line("Source and line number information", "line"),
  Vars("Source, line number and local variable information", "vars"),
  Notailcalls("Complete, no tail call optimization", "notailcalls");

  private String myDescription;
  private String myOption;
  
  DebuggingInfoLevel(String description, String option) {
    myDescription = description;
    myOption = option;
  }

  public String getName() {
    return myDescription;
  }

  public String getOption() {
    return myOption;
  }
}
