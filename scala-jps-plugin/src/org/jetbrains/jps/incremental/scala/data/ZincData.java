package org.jetbrains.jps.incremental.scala.data;

import org.jetbrains.jps.incremental.scala.ConfigurationException;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.jetbrains.jps.incremental.scala.Utilities.findByName;

/**
 * @author Pavel Fatin
 */
public class ZincData {
  private Collection<File> myClasspath;
  private File mySbtInterface;
  private File myCompilerSources;

  private ZincData(Collection<File> classpath, File sbtInterface, File compilerSources) {
    myClasspath = classpath;
    mySbtInterface = sbtInterface;
    myCompilerSources = compilerSources;
  }

  public Collection<File> getClasspath() {
    return myClasspath;
  }

  public File getSbtInterface() {
    return mySbtInterface;
  }

  public File getCompilerSources() {
    return myCompilerSources;
  }

  public static ZincData create(File home) {
    File[] zincJars = home.listFiles();

    if (zincJars == null || zincJars.length == 0) {
      throw new ConfigurationException("No Zinc jars in the directory: " + home.getPath());
    }

    List<File> classpath = Arrays.asList(zincJars);

    // Find a path to "sbt-interface.jar"
    File sbtInterface = findByName(classpath, "sbt-interface.jar");

    if (sbtInterface == null) {
      throw new ConfigurationException("No sbt-interface.jar found");
    }

    // Find a path to "compiler-interface-sources.jar"
    File compilerSources = findByName(classpath, "compiler-interface-sources.jar");

    if (compilerSources == null) {
      throw new ConfigurationException("No compiler-interface-sources.jar found");
    }

    return new ZincData(classpath, sbtInterface, compilerSources);
  }
}
