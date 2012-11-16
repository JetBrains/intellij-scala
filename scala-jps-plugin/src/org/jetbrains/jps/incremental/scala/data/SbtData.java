package org.jetbrains.jps.incremental.scala.data;

import org.jetbrains.jps.incremental.scala.ConfigurationException;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.jetbrains.jps.incremental.scala.Utilities.findByName;

/**
 * @author Pavel Fatin
 */
public class SbtData {
  private File mySbtInterface;
  private File myCompilerInterfaceSources;

  private SbtData(File sbtInterface, File compilerInterfaceSources) {
    mySbtInterface = sbtInterface;
    myCompilerInterfaceSources = compilerInterfaceSources;
  }

  public File getSbtInterface() {
    return mySbtInterface;
  }

  public File getCompilerInterfaceSources() {
    return myCompilerInterfaceSources;
  }

  public static SbtData create(File home) {
    File[] zincJars = home.listFiles();

    if (zincJars == null || zincJars.length == 0) {
      throw new ConfigurationException("No SBT jars in directory: " + home.getPath());
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

    return new SbtData(sbtInterface, compilerSources);
  }
}
