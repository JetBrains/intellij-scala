package org.jetbrains.plugins.scala.cache.info.impl;

import org.jetbrains.plugins.scala.cache.info.ScalaFileInfo;
import org.jetbrains.annotations.NotNull;
import com.intellij.psi.PsiClass;

/**
 * @author Ilya.Sergey
 */

public class ScalaFileInfoImpl implements ScalaFileInfo {

  private final String myFileName;
  private final String myFileDirectoryUrl;
  private final long myTimestamp;
  private PsiClass[] myClasses;

  public ScalaFileInfoImpl(final String fileName, final String directoryUrl, final long timestamp) {
    myFileName = fileName;
    myFileDirectoryUrl = directoryUrl;
    myTimestamp = timestamp;
  }

  public long getFileTimestamp() {
    return myTimestamp;
  }

  public String getFileName() {
    return myFileName;
  }

  @NotNull
  public String getFileUrl() {
    return myFileDirectoryUrl;
  }

  public String getFileDirectoryUrl() {
    return myFileDirectoryUrl;
  }

  public void setClasses(PsiClass[] classes) {
    myClasses = classes;
  }

  public PsiClass[] getClasses() {
    return myClasses;
  }

  public String toString() {
    return myFileName + " [" + myTimestamp + "]";
  }
}
