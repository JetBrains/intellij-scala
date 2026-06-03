package org.jetbrains.plugins.scala.settings.annotations;

public enum Entity {
  Value(false),
  Variable(false),
  Method(false),
  Parameter(true),
  UnderscoreParameter(true);

  private final boolean myIsParameter;

  Entity(boolean isParameter) {
    myIsParameter = isParameter;
  }

  public boolean isParameter() {
    return myIsParameter;
  }
}
