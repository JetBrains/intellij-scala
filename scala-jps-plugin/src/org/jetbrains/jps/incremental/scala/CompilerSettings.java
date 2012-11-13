package org.jetbrains.jps.incremental.scala;

import java.io.File;
import java.util.Collection;

/**
 * @author Pavel Fatin
 */
public class CompilerSettings {
  private Collection<File> myCompilerClasspath;
  private File mySbtInterfaceFile;
  private File myCompilerInterfaceFile;
  private File myOutputDirectory;
  private Collection<File> myClasspath;

  public CompilerSettings(Collection<File> compilerClasspath,
                          File sbtInterfaceFile,
                          File compilerInterfaceFile,
                          File outputDirectory,
                          Collection<File> classpath) {
    myCompilerClasspath = compilerClasspath;
    mySbtInterfaceFile = sbtInterfaceFile;
    myCompilerInterfaceFile = compilerInterfaceFile;
    myOutputDirectory = outputDirectory;
    myClasspath = classpath;
  }

  public Collection<File> getCompilerClasspath() {
    return myCompilerClasspath;
  }

  public File getSbtInterfaceFile() {
    return mySbtInterfaceFile;
  }

  public File getCompilerInterfaceFile() {
    return myCompilerInterfaceFile;
  }

  public File getOutputDirectory() {
    return myOutputDirectory;
  }

  public Collection<File> getClasspath() {
    return myClasspath;
  }
}
