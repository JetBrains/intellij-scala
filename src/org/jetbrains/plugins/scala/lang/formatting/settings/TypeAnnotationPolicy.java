package org.jetbrains.plugins.scala.lang.formatting.settings;

/**
 * Pavel Fatin
 */
public enum TypeAnnotationPolicy {
  Optional("Optional"),
  Regular("As Usual");

  private String myDescription;

  private TypeAnnotationPolicy(String description) {
    myDescription = description;
  }


  @Override
  public String toString() {
    return myDescription;
  }
}
