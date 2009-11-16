package org.jetbrains.plugins.scala.config;

import com.intellij.util.PathUtil;

/**
 * @author ilyas
 */
public class ScalaLibrariesConfiguration {
  public ScalaLibrariesConfiguration() {
    if (myScalaSdkJarPath == null ||"".equals(myScalaSdkJarPath)) {
      myScalaSdkJarPath = PathUtil.getJarPathForClass(scala.Predef.class);
    }
    if (myScalaCompilerJarPath == null || "".equals(myScalaCompilerJarPath)) {
      myScalaCompilerJarPath = PathUtil.getJarPathForClass(scala.tools.nsc.Global.class);
    }
  }

  public boolean takeFromSettings = false;
  public boolean isRelativeToProjectPath = false;
  public boolean myExcludeCompilerFromModuleScope = false;
  public boolean myExcludeSdkFromModuleScope = false;

  public String myScalaCompilerJarPath = "";
  public String myScalaSdkJarPath = "";

}
