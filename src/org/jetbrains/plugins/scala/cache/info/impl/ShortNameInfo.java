package org.jetbrains.plugins.scala.cache.info.impl;

import java.util.Set;
import java.util.Collection;
import java.util.HashSet;
import java.util.Arrays;
import java.io.Serializable;

/**
 * Stores info about all files, which contain classes with this name
 * 
 * @author Ilya.Sergey
 */
public class ShortNameInfo implements Serializable {
  private String className;
  private String[] fileUrls;

  public ShortNameInfo(String name, Collection<String> urls){
    setClassName(name);
    setFileUrls(new HashSet<String>());
    for (String url: urls){
      getFileUrls().add(url);
    }
  }

  public String getClassName() {
    return className;
  }

  private void setClassName(String className) {
    this.className = className;
  }

  public Set<String> getFileUrls() {
    Set<String> set = new HashSet<String>();
    for (String url: fileUrls){
      set.add(url);
    }
    return set;
  }

  private void setFileUrls(Set<String> fileUrls) {
    this.fileUrls = fileUrls.toArray(new String[0]);
  }
}
