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

package org.jetbrains.plugins.scala.util;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiClass;
import com.intellij.facet.FacetManager;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaFileType;

import java.io.File;
import java.io.FilenameFilter;
import java.util.regex.Pattern;

/**
 * @author Ilya.Sergey
 */
@SuppressWarnings({"AbstractClassNeverImplemented"})
public abstract class ScalaUtils {
  /**
   * This is name for type parameter like: .isInstanceOf[T]
   * This name should be unique, nobody can't use such name (it means that it has small probability).
   * In presentable text should be replace for T. So this string only for internal usage.
   */
  public static String typeParameter = "TypeParameterForSynthteticFunction";

  /**
   * @param dir
   * @return true if current file is VCS auxiliary directory
   */
  public static boolean isVersionControlSysDir(final VirtualFile dir) {
    if (!dir.isDirectory()) {
      return false;
    }
    final String name = dir.getName().toLowerCase();
    return ".svn".equals(name) || "_svn".equals(name) ||
            ".cvs".equals(name) || "_cvs".equals(name);
  }

  public static final String PLUGIN_MODULE_ID = "PLUGIN_MODULE";

  /**
   * @param file
   * @return true if current file is true scala file
   */
  public static boolean isScalaFile(final VirtualFile file) {
    return (file != null) && !file.isDirectory() &&
            ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension().equals(file.getExtension());
  }

  /**
   * @param module Module to get content root
   * @return VirtualFile corresponding to content root
   */
  @NotNull
  public static VirtualFile getModuleRoot(final Module module) {
    final VirtualFile[] roots = ModuleRootManager.getInstance(module).getSourceRoots();
    if (roots.length > 0) {
      return roots[0];
    }
    return module.getModuleFile().getParent();
  }

  /**
   * @param module Module to get content root
   * @return VirtualFile array corresponding to content roots of current module
   */
  @NotNull
  public static VirtualFile[] getModuleRoots(final Module module) {
    final VirtualFile[] roots = ModuleRootManager.getInstance(module).getSourceRoots();
    return roots;
  }

  /**
   * @param module Module to get content root
   * @return VirtualFile corresponding to content root
   */
  @NotNull
  public static String[] getModuleRootUrls(final Module module) {
    VirtualFile[] roots = ModuleRootManager.getInstance(module).getSourceRoots();
    if (roots.length == 0) {
      VirtualFile file = module.getModuleFile();
      roots = file != null ? new VirtualFile[]{(file.getParent())} : VirtualFile.EMPTY_ARRAY;
    }
    String[] urls = new String[roots.length];
    int i = 0;
    for (VirtualFile root : roots) {
      urls[i++] = root.getUrl();
    }
    return urls;
  }

  /**
   * @param file
   * @return true if current file is true scala file
   */

  public static boolean isScalaFileOrDirectory(final @NotNull VirtualFile file) {
    return isScalaFile(file) || file.isDirectory();
  }

  public static File[] getFilesInDirectoryByPattern(String dirPath, final String patternString) {
    File distDir = new File(dirPath);
    final Pattern pattern = Pattern.compile(patternString);
    File[] files = distDir.listFiles(new FilenameFilter() {
      public boolean accept(File dir, String name) {
        return pattern.matcher(name).matches();
      }
    });
    return files != null ? files : new File[0];
  }

  public static void runWriteAction(final Runnable runnable, Project project, String name) {
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(runnable);
      }
    }, name, null);
  }

  public static void runReadAction(final Runnable runnable, Project project, String name) {
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runReadAction(runnable);
      }
    }, name, null);
  }

  public static boolean isSuitableModule(Module module) {
    if (module == null) return false;
    ModuleType moduleType = module.getModuleType();
    return moduleType instanceof JavaModuleType || moduleType.getId().equals(PLUGIN_MODULE_ID);
  }

  public static boolean isUnderTestSources(PsiClass c) {
    ProjectRootManager rm = ProjectRootManager.getInstance(c.getProject());
    VirtualFile f = c.getContainingFile().getVirtualFile();
    if (f == null) return false;
    return rm.getFileIndex().isInTestSourceContent(f);
  }

  public static String getPluginVersion() {
    String jarPathForClass = PathUtil.getJarPathForClass(ScalaUtils.class);
    throw new UnsupportedOperationException("Getting Plugin version is unsupported");
  }
}
