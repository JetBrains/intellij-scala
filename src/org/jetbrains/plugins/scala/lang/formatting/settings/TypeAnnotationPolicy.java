package org.jetbrains.plugins.scala.lang.formatting.settings;

/**
 * Pavel Fatin
 */
// TODO remove
public enum TypeAnnotationPolicy {
  Optional("Optional"),
  Regular("As Usual");

  private String myDescription;

  TypeAnnotationPolicy(String description) {
    myDescription = description;
  }


  @Override
  public String toString() {
    return myDescription;
  }
}
