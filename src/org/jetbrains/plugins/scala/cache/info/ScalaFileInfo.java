package org.jetbrains.plugins.scala.cache.info;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

import com.intellij.psi.PsiClass;

/**
 * Main info about onew scala file
 * @author Ilya.Sergey
 */
public interface ScalaFileInfo extends Serializable {

  public long getFileTimestamp();

  public String getFileName();

  @NotNull
  public String getFileUrl();

  public String getFileDirectoryUrl();

  public String[] getClassNames();

  public void setClasses(PsiClass[] classes);

  public String toString();

  public boolean containsClass(String name);


}
