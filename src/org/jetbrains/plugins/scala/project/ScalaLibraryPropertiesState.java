package org.jetbrains.plugins.scala.project;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.Arrays;

/**
 * @author Pavel Fatin
 */
public class ScalaLibraryPropertiesState {
  public ScalaLanguageLevel languageLevel = ScalaLanguageLevel.Default();

  @Tag("compiler-classpath")
  @AbstractCollection(surroundWithTag = false, elementTag = "root", elementValueAttribute = "url")
  public String[] compilerClasspath = new String[]{};

  @Override
  public boolean equals(Object obj) {
    ScalaLibraryPropertiesState that = (ScalaLibraryPropertiesState) obj;
    return languageLevel == that.languageLevel && Arrays.equals(compilerClasspath, that.compilerClasspath);
  }
}