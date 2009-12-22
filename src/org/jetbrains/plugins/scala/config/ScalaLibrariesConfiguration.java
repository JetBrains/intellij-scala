package org.jetbrains.plugins.scala.config;

import com.intellij.util.PathUtil;

/**
 * @author ilyas
 */
public class ScalaLibrariesConfiguration {
  public ScalaLibrariesConfiguration() {
    if (myScalaSdkJarPaths == null ||"".equals(myScalaSdkJarPaths[0])) {
      myScalaSdkJarPaths = new String[] {PathUtil.getJarPathForClass(scala.Predef.class)};
    }
    if (myScalaCompilerJarPaths == null || "".equals(myScalaCompilerJarPaths[0])) {
      myScalaCompilerJarPaths = new String[] {PathUtil.getJarPathForClass(scala.tools.nsc.Global.class)};
    }
  }

  public boolean takeFromSettings = false;

  public String[] myScalaCompilerJarPaths = {""};
  public String[] myScalaSdkJarPaths = {""};

}
