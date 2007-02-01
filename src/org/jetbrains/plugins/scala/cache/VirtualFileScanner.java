package org.jetbrains.plugins.scala.cache;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.util.ScalaUtils;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Set;
import java.util.HashSet;

/**
 * @author Ilya.Sergey
 */
public class VirtualFileScanner {

  /**
   * Scans recursively for scala source files
   *
   * @param virtualFile the root file
   * @return List of files
   */
  @NotNull
  public static Set<VirtualFile> scan(final VirtualFile virtualFile) {
    final Set<VirtualFile> myFiles = new HashSet<VirtualFile>();
    addScalaFiles(virtualFile, myFiles);
    return myFiles;
  }

  /**
   * Searches all scala files in directory.
   *
   * @param file     File or directory
   * @param allFiles Method adds all found files into this set
   */
  public static void addScalaFiles(@Nullable final VirtualFile file, @NotNull final Set<VirtualFile> allFiles) {
    if (file == null || ScalaUtils.isVersionControlSysDir(file)) {
      return;
    }
    if (!file.isDirectory()) {
      if (ScalaUtils.isScalaFile(file)) {
        allFiles.add(file);
      }
      return;
    }
    final VirtualFile[] children = file.getChildren();
    for (final VirtualFile child : children) {
      addScalaFiles(child, allFiles);
    }
  }
}

