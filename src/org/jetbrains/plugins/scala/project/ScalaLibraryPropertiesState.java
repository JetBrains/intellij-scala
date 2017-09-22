package org.jetbrains.plugins.scala.project;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.Arrays;

/**
 * @author Pavel Fatin
 */
public class ScalaLibraryPropertiesState {
  // We have to rely on the Java's enumeration for serialization
  private final PlatformProxy platform;

  // We have to rely on the Java's enumeration for serialization
  private final ScalaLanguageLevelProxy languageLevel;

  public ScalaLibraryPropertiesState() {
    this.platform = PlatformProxy.Scala;
    this.languageLevel = ScalaLanguageLevel.Default().proxy();
    this.compilerClasspath = new String[0];
  }

  public ScalaLibraryPropertiesState(Platform platform, ScalaLanguageLevel languageLevel, String[] compilerClasspath) {
    this.platform = platform.proxy();
    this.languageLevel = languageLevel.proxy();
    this.compilerClasspath = compilerClasspath;
  }

  public Platform getPlatform() {
    return Platform.from(platform);
  }

  public ScalaLanguageLevel getLanguageLevel() {
    return ScalaLanguageLevel.from(languageLevel);
  }

  @Tag("compiler-classpath")
  @AbstractCollection(surroundWithTag = false, elementTag = "root", elementValueAttribute = "url")
  public final String[] compilerClasspath;

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    ScalaLibraryPropertiesState that = (ScalaLibraryPropertiesState) obj;
    return languageLevel == that.languageLevel &&
            Arrays.equals(compilerClasspath, that.compilerClasspath);
  }
}