package org.jetbrains.plugins.scala.util;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.plugins.scala.ScalaFileType;

/**
 * @author Ilya.Sergey
 */
public abstract class ScalaUtils {

  public static boolean isVersionControlSysDir(final VirtualFile dir) {
    if (!dir.isDirectory()) {
      return false;
    }
    final String name = dir.getName().toLowerCase();
    return ".svn".equals(name) || "_svn".equals(name) ||
            ".cvs".equals(name) || "_cvs".equals(name);
  }

  public static boolean isScalaFile(final VirtualFile file) {
    return (file != null) && !file.isDirectory() &&
            ScalaFileType.SCALA_FILE_TYPE.getDefaultExtension().equals(file.getExtension());
  }

}
