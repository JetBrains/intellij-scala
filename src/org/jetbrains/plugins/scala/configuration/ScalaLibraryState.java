package org.jetbrains.plugins.scala.configuration;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Pavel Fatin
 */
@Tag("scala-sdk")
public class ScalaLibraryState {
  @Tag("compiler-classpath")
  @AbstractCollection(surroundWithTag = false, elementTag = "root", elementValueAttribute = "url")
  public List<String> compilerClasspath = new ArrayList<String>();
}