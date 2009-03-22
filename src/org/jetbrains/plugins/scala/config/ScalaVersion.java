package org.jetbrains.plugins.scala.config;

import com.intellij.facet.ui.libraries.LibraryInfo;
import org.jetbrains.annotations.NonNls;
import static org.jetbrains.plugins.scala.config.util.ScalaMavenLibraryUtil.createMavenJarInfo;


/**
 * @author ilyas
 */
public enum ScalaVersion {

  Scala2_7_1("2.7.1", new LibraryInfo[]{
      createMavenJarInfo("scala-compiler", "2.7.1", "org/scala-lang", "scala.tools.nsc.CompilerRun"),
      createMavenJarInfo("scala-library", "2.7.1", "org/scala-lang", "scala.Predef"),
  }),

  Scala2_7_2("2.7.2", new LibraryInfo[]{
      createMavenJarInfo("scala-compiler", "2.7.2", "org/scala-lang", "scala.tools.nsc.CompilerRun"),
      createMavenJarInfo("scala-library", "2.7.2", "org/scala-lang", "scala.Predef"),
  }),

  Scala2_7_3("2.7.3", new LibraryInfo[]{
      createMavenJarInfo("scala-compiler", "2.7.3", "org/scala-lang", "scala.tools.nsc.CompilerRun"),
      createMavenJarInfo("scala-library", "2.7.3", "org/scala-lang", "scala.Predef"),
  });

  private final String myName;
  private final LibraryInfo[] myJars;

  private ScalaVersion(@NonNls String name, LibraryInfo[] infos) {
    myName = name;
    myJars = infos;
  }

  public LibraryInfo[] getJars() {
    return myJars;
  }

  public String toString() {
    return myName;
  }

}