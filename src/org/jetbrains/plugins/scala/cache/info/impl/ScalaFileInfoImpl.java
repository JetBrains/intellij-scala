package org.jetbrains.plugins.scala.cache.info.impl;

import org.jetbrains.plugins.scala.cache.info.ScalaFileInfo;
import org.jetbrains.plugins.scala.cache.VirtualFileScanner;
import org.jetbrains.annotations.NotNull;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.util.io.fs.FileSystem;

import java.util.ArrayList;

/**
 * @author Ilya.Sergey
 */

public class ScalaFileInfoImpl implements ScalaFileInfo {

  private final String myFileName;
  private final String myFileDirectoryUrl;
  private final long myTimestamp;
  private transient PsiClass[] myClasses;
  private String[] myClassNames;

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
    return myFileDirectoryUrl + '/' + myFileName;
  }

  public String getFileDirectoryUrl() {
    return myFileDirectoryUrl;
  }


  private void setClassNames() {
    String classNames[] = new String[myClasses.length];
    for (int i = 0; i < myClasses.length; i++) {
      classNames[i] = myClasses[i].getQualifiedName();
    }
    myClassNames = classNames;
  }


  // TODO:  FIX ME!!!
  public PsiClass getClassByName(String name) {
    PsiJavaFile javaPsi = ((PsiJavaFile) VirtualFileScanner.getFileByUrl(getFileUrl()));
    if (javaPsi != null) {
      PsiClass[] classes = javaPsi.getClasses();
      for (PsiClass clazz : classes) {
        if (clazz.getQualifiedName().equals(name)) {
          return clazz;
        }
        return null;
      }
    }
    return null;
  }


  // TODO:  FIX ME!!!
  public PsiClass[] getClassesByName(String name){
    PsiJavaFile javaPsi = ((PsiJavaFile) VirtualFileScanner.getFileByUrl(getFileUrl()));
    ArrayList acc = new ArrayList<PsiClass>();
    if (javaPsi != null) {
      PsiClass[] classes = javaPsi.getClasses();
      for (PsiClass clazz : classes) {
        if (clazz.getQualifiedName().equals(name)) {
          acc.add(clazz);
        }
      }
      return (PsiClass[])acc.toArray();
    }
    return new PsiClass[0];
  }


  public boolean containsClass(String name) {
    for (String _name: myClassNames){
      if (_name.equals(name)) {
        return true;
      }
    }
    return false;
  }

  public String[] getClassNames() {
    return myClassNames;
  }

  public void setClasses(PsiClass[] classes) {
    myClasses = classes;
    setClassNames();
  }

  public String toString() {
    return myFileName + " [" + myTimestamp + "]";
  }
}
