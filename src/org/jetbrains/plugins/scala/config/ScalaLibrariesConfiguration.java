package org.jetbrains.plugins.scala.config;

import com.intellij.util.PathUtil;

/**
 * @author ilyas
 */
public class ScalaLibrariesConfiguration {
  public ScalaLibrariesConfiguration() {
  }

  public boolean takeFromSettings = false;

  public String[] myScalaCompilerJarPaths = {""};
  public String[] myScalaSdkJarPaths = {""};


  public String[] getCompilerPaths() {
    if (myScalaCompilerJarPaths == null || myScalaCompilerJarPaths.length == 0 || "".equals(myScalaCompilerJarPaths[0])) {
      myScalaCompilerJarPaths = new String[] {PathUtil.getJarPathForClass(scala.tools.nsc.Global.class)};
    }
    return myScalaCompilerJarPaths;
  }
  
  public String[] getSdkPaths() {
    if (myScalaSdkJarPaths == null || myScalaSdkJarPaths.length == 0 || "".equals(myScalaSdkJarPaths[0])) {
      myScalaSdkJarPaths = new String[] {PathUtil.getJarPathForClass(scala.Predef.class)};
    }
    return myScalaSdkJarPaths;
  }

}
