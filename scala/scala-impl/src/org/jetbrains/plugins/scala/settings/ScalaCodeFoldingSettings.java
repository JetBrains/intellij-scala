package org.jetbrains.plugins.scala.settings;

import com.intellij.openapi.application.ApplicationManager;

public abstract class ScalaCodeFoldingSettings {

  public static ScalaCodeFoldingSettings getInstance() {
    return ApplicationManager.getApplication().getService(ScalaCodeFoldingSettings.class);
  }

  public abstract boolean isCollapseFileHeaders(); // from CodeFoldingSettings
  public abstract boolean isCollapseImports(); // from CodeFoldingSettings
  public abstract boolean isCollapseLineComments(); // from JavaCodeFoldingSettings
  public abstract boolean isCollapseScalaDocComments(); // from CodeFoldingSettings

  public abstract boolean isCollapseShellComments();
  public abstract void setCollapseShellComments(boolean value);
  public abstract boolean isCollapseBlockComments();
  public abstract void setCollapseBlockComments(boolean value);
  public abstract boolean isCollapseCustomRegions();
  public abstract void setCollapseCustomRegions(boolean value);
  public abstract boolean isCollapseMethodCallBodies();
  public abstract void setCollapseMethodCallBodies(boolean value);
  public abstract boolean isCollapseTemplateBodies();
  public abstract void setCollapseTemplateBodies(boolean value);
  // def, val, var, given, type, this
  public abstract boolean isCollapseDefinitionWithAssignmentBodies();
  public abstract void setCollapseDefinitionWithAssignmentBodies(boolean value);
  public abstract boolean isCollapseTypeLambdas();
  public abstract void setCollapseTypeLambdas(boolean value);
  public abstract boolean isCollapsePackagings();
  public abstract void setCollapsePackagings(boolean value);
  public abstract boolean isCollapseMultilineStrings();
  public abstract void setCollapseMultilineStrings(boolean value);
  public abstract boolean isCollapseMultilineBlocks();
  public abstract void setCollapseMultilineBlocks(boolean value);
  public abstract boolean isFoldingForAllBlocks();
  public abstract void setFoldingForAllBlocks(boolean value);
}
