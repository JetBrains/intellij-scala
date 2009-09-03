/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.config;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.Function;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.facet.FacetManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings;
import org.jetbrains.plugins.scala.util.LibrariesUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Properties;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import scala.tools.nsc.Global;

/**
 * @author ilyas
 */
public class ScalaConfigUtils {

  public static final String UNDEFINED_VERSION = "undefined";

  public static final String SCALA_LIB_PREFIX = "scala-";

  public static final String SCALA_LIB_JAR_NAME_PREFIX = "scala-library";

  public static final String SCALA_LIB_PATTERN = "scala-.*jar";

  public static final String LIBRARY_PROPERTIES_PATH = "library.properties";

  public static final String PREFED_CLASS_PATH = "scala/Predef.class";

  public static final String VERSION_PROPERTY_KEY = "version.number";

  private static final Condition<Library> SCALA_SDK_LIB_CONDITION = new Condition<Library>() {
    public boolean value(Library library) {
      return isScalaSdkLibrary(library);
    }
  };

  /**
   * Checks wheter given IDEA library contaions Scala Library classes
   */
  public static boolean isScalaSdkLibrary(Library library) {
    return library != null && checkLibrary(library, SCALA_LIB_JAR_NAME_PREFIX, PREFED_CLASS_PATH);
  }

  public static boolean checkLibrary(Library library, String jarNamePrefix, String necessaryClass) {
    boolean result = false;
    VirtualFile[] classFiles = library.getFiles(OrderRootType.CLASSES);
    for (VirtualFile file : classFiles) {
      String path = file.getPath();
      if (path != null && "jar".equals(file.getExtension())) {
        path = StringUtil.trimEnd(path, "!/");
        String name = file.getName();

        File realFile = new File(path);
        if (realFile.exists()) {
          try {
            JarFile jarFile = new JarFile(realFile);
            if (name.startsWith(jarNamePrefix)) {
              if (jarFile.getJarEntry(necessaryClass) != null) {
                jarFile.close();
                return true;
              }
            }
            jarFile.close();
          } catch (IOException e) {
            result = false;
          }
        }
      }
    }
    return result;
  }

  public static String getScalaSDKVersion(@NotNull String jarPath) {
    String jarVersion = getScalaJarVersion(jarPath, LIBRARY_PROPERTIES_PATH);
    return jarVersion != null ? jarVersion : UNDEFINED_VERSION;
  }

  /**
   * Return value of Implementation-Version attribute in jar manifest
   * <p/>
   *
   * @param jarPath  path to jar file
   * @param propPath path to properties file in jar file
   * @return value of Implementation-Version attribute, null if not found
   */
  public static String getScalaJarVersion(String jarPath, String propPath) {
    try {
      File file = new File(jarPath);
      if (!file.exists()) {
        return null;
      }
      JarFile jarFile = new JarFile(file);
      JarEntry jarEntry = jarFile.getJarEntry(propPath);
      if (jarEntry == null) {
        return null;
      }
      Properties properties = new Properties();
      properties.load(jarFile.getInputStream(jarEntry));
      String version = properties.getProperty(VERSION_PROPERTY_KEY);
      jarFile.close();
      return version;
    }
    catch (Exception e) {
      return null;
    }
  }

  public static Library[] getProjectScalaLibraries(Project project) {
    if (project == null) return new Library[0];
    final LibraryTable table = ProjectLibraryTable.getInstance(project);
    final List<Library> all = ContainerUtil.findAll(table.getLibraries(), SCALA_SDK_LIB_CONDITION);
    return all.toArray(new Library[all.size()]);
  }

  public static Library[] getAllScalaLibraries(@Nullable Project project) {
    return ArrayUtil.mergeArrays(getGlobalScalaLibraries(), getProjectScalaLibraries(project), Library.class);
  }

  public static Library[] getGlobalScalaLibraries() {
    return LibrariesUtil.getGlobalLibraries(SCALA_SDK_LIB_CONDITION);
  }

  public static String getSpecificJarForLibrary(Library library, String jarNamePrefix, String necessaryClass) {
    VirtualFile[] classFiles = library.getFiles(OrderRootType.CLASSES);
    for (VirtualFile file : classFiles) {
      String path = file.getPath();
      if (path != null && "jar".equals(file.getExtension())) {
        path = StringUtil.trimEnd(path, "!/");
        String name = file.getName();

        File realFile = new File(path);
        if (realFile.exists()) {
          try {
            JarFile jarFile = new JarFile(realFile);
            if (name.startsWith(jarNamePrefix) && jarFile.getJarEntry(necessaryClass) != null) {
              return path;
            }
            jarFile.close();
          } catch (IOException e) {
            //do nothing
          }
        }
      }
    }
    return "";
  }

  public static Library[] getScalaSdkLibrariesByModule(final Module module) {
    return LibrariesUtil.getLibrariesByCondition(module, SCALA_SDK_LIB_CONDITION);
  }

  @NotNull
  public static String getScalaSdkJarPath(Module module) {
    if (module == null) return "";
    final FacetManager manager = FacetManager.getInstance(module);
    final ScalaFacet facet = manager.getFacetByType(ScalaFacet.ID);
    if (facet == null) return "";
    final ScalaFacetConfiguration configuration = facet.getConfiguration();
    final ScalaLibrariesConfiguration libConf = configuration.getMyScalaLibrariesConfiguration();
    if (libConf.takeFromSettings) {
      return libConf.myScalaSdkJarPath;
    } else {
      Library[] libraries = getScalaSdkLibrariesByModule(module);
      if (libraries.length == 0) return "";
      final Library library = libraries[0];
      return getScalaSdkJarPathForLibrary(library);
    }
  }

  public static String getScalaSdkJarPathForLibrary(Library library) {
    return getSpecificJarForLibrary(library, SCALA_LIB_JAR_NAME_PREFIX, PREFED_CLASS_PATH);
  }

}
