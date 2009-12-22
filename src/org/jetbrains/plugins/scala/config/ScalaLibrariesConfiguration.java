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

  @Deprecated //todo: for few releases, after should be deleted (22.12.2009)
  public String myScalaCompilerJarPath = ""; //for compatibility
  @Deprecated //todo: for few releases, after should be deleted (22.12.2009)
  public String myScalaSdkJarPath = ""; //for compatibility
  
  public String[] getCompilerPaths() {
    if (myScalaCompilerJarPath != null && !myScalaCompilerJarPath.equals("")) {
      myScalaCompilerJarPaths = new String[] {myScalaCompilerJarPath};
      myScalaSdkJarPath = "";
    }
    else if (myScalaCompilerJarPaths == null || myScalaCompilerJarPaths.length == 0 || "".equals(myScalaCompilerJarPaths[0])) {
      myScalaCompilerJarPaths = new String[] {PathUtil.getJarPathForClass(scala.tools.nsc.Global.class)};
    }
    return myScalaCompilerJarPaths;
  }
  
  public String[] getSdkPaths() {
    if (myScalaSdkJarPath != null && !myScalaSdkJarPath.equals("")) {
      myScalaSdkJarPaths = new String[] {myScalaSdkJarPath};
      myScalaSdkJarPath = "";
    }
    else if (myScalaSdkJarPaths == null || myScalaSdkJarPaths.length == 0 || "".equals(myScalaSdkJarPaths[0])) {
      myScalaSdkJarPaths = new String[] {PathUtil.getJarPathForClass(scala.Predef.class)};
    }
    return myScalaSdkJarPaths;
  }

}
