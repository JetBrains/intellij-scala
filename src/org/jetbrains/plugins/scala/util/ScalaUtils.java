package org.jetbrains.plugins.scala.util;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Ilya.Sergey
 */
public abstract class ScalaUtils {
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
  public static VirtualFile getModuleRoot(@NotNull final Module module) {
    final VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    if (roots.length > 0) {
      return roots[0];
    }
    return (module.getModuleFile().getParent());
  }

  /**
   * @param module Module to get content root
   * @return VirtualFile array corresponding to content roots of current module
   */
  @NotNull
  public static VirtualFile[] getModuleRoots(@NotNull final Module module) {
    final VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    return roots;
  }

  /**
   * @param module Module to get content root
   * @return VirtualFile corresponding to content root
   */
  @NotNull
  public static String[] getModuleRootUrls(@NotNull final Module module) {
    VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    if (roots.length == 0) {
      roots = new VirtualFile[]{(module.getModuleFile().getParent())};
    }
    String[] urls = new String[roots.length];
    int i = 0;
    for (VirtualFile root : roots) {
      urls[i++] = root.getUrl();
    }
    return urls;
  }

}
