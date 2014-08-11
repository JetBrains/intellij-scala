package org.jetbrains.plugins.scala.config;

/**
 * Pavel.Fatin, 09.08.2010
 */
public enum DebuggingInfoLevel {
  None("None", "none"),
  Source("Source file attribute", "source"),
  Line("Source and line number information", "line"),
  Vars("Source, line number and local variable information", "vars"),
  Notc("Complete, no tail call optimization", "notc");

  private String myDescription;
  private String myOption;
  
  DebuggingInfoLevel(String description, String option) {
    myDescription = description;
    myOption = option;
  }

  public String getDescription() {
    return myDescription;
  }

  public String getOption() {
    return myOption;
  }
}
