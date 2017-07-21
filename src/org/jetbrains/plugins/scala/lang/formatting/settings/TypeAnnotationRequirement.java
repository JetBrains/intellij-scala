package org.jetbrains.plugins.scala.lang.formatting.settings;

/**
 * Pavel Fatin
 */
// TODO Remove
public enum TypeAnnotationRequirement {
  Optional("Optional"),
  Preferred("Add"),
  Required("Add & Check");

  private String myDescription;

  TypeAnnotationRequirement(String description) {
    myDescription = description;
  }


  @Override
  public String toString() {
    return myDescription;
  }
}
