package org.jetbrains.plugins.scala.lang.refactoring.introduceField;

import org.jetbrains.plugins.scala.lang.psi.types.ScType;
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings;

/**
 * Nikolay.Tropin
 * 7/15/13
 */
public class IntroduceFieldSettings2 {
  private final ScalaApplicationSettings scalaSettings = ScalaApplicationSettings.getInstance();
  private String name;
  private ScType scType;
  private boolean isVar = scalaSettings.INTRODUCE_FIELD_IS_VAR;
  private boolean replaceAll = scalaSettings.INTRODUCE_FIELD_REPLACE_ALL;
  private ScalaApplicationSettings.VisibilityLevel visibilityLevel = scalaSettings.INTRODUCE_FIELD_VISIBILITY;
  private boolean explicitType = scalaSettings.INTRODUCE_FIELD_EXPLICIT_TYPE;
  private boolean initInDeclaration = scalaSettings.INTRODUCE_FIELD_INITIALIZE_IN_DECLARATION;

  IntroduceFieldSettings2() {
    name = null;
    scType = null;
  }

  public ScType getScType() {
    return scType;
  }

  public void setScType(ScType scType) {
    this.scType = scType;
  }

  public boolean isVar() {
    return isVar;
  }

  public void setIsVar(boolean isVar) {
    this.isVar = isVar;
  }

  public boolean isReplaceAll() {
    return replaceAll;
  }

  public void setReplaceAll(boolean replaceAll) {
    this.replaceAll = replaceAll;
  }

  public ScalaApplicationSettings.VisibilityLevel getVisibilityLevel() {
    return visibilityLevel;
  }

  public void setVisibilityLevel(ScalaApplicationSettings.VisibilityLevel visibilityLevel) {
    this.visibilityLevel = visibilityLevel;
  }

  public boolean isExplicitType() {
    return explicitType;
  }

  public void setExplicitType(boolean explicitType) {
    this.explicitType = explicitType;
  }

  public boolean isInitInDeclaration() {
    return initInDeclaration;
  }

  public void setInitInDeclaration(boolean initInDeclaration) {
    this.initInDeclaration = initInDeclaration;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

}
