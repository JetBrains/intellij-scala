package org.jetbrains.plugins.scala.project;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.projectRoots.ui.PathEditor;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MyPathEditor extends PathEditor {
  public MyPathEditor(FileChooserDescriptor descriptor) {
    super(descriptor);
  }

  public String[] getPaths() {
    return virtualFilesToPaths(getRoots());
  }

  public void setPaths(String[] paths) {
    resetPath(pathsToVirtualFiles(paths));
  }

  private static List<VirtualFile> pathsToVirtualFiles(String[] urls) {
    List<VirtualFile> result = new ArrayList<VirtualFile>(urls.length);
    for (String url : urls) {
      String path = VfsUtil.urlToPath(url);
      VirtualFile file = VfsUtil.findFileByIoFile(new File(path), true);
      result.add(file == null ? new AbsentLocalFile(url, path) : file);
    }
    return result;
  }

  private static String[] virtualFilesToPaths(VirtualFile[] files) {
    String[] result = new String[files.length];
    int i = 0;
    for (VirtualFile file : files) {
      result[i] = file.getUrl();
      i++;
    }
    return result;
  }
}
