package org.jetbrains.jps.incremental.scala;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.builders.java.ResourceRootDescriptor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.messages.ProgressMessage;

import java.io.File;
import java.io.IOException;

public class ResourceUpdater {
  // Modified copy of org.jetbrains.jps.incremental.resources.ResourcesBuilder.copyResource
  public static void updateResource(CompileContext context, ResourceRootDescriptor rd, File file, File outputRoot) throws IOException {
    final String sourceRootPath = FileUtil.toSystemIndependentName(rd.getRootFile().getAbsolutePath());
    final String relativePath = FileUtil.getRelativePath(sourceRootPath, FileUtil.toSystemIndependentName(file.getPath()), '/');
    final String prefix = rd.getPackagePrefix();

    final StringBuilder targetPath = new StringBuilder();
    targetPath.append(FileUtil.toSystemIndependentName(outputRoot.getPath()));
    if (prefix.length() > 0) {
      targetPath.append('/').append(prefix.replace('.', '/'));
    }
    targetPath.append('/').append(relativePath);

    final String outputPath = targetPath.toString();
    final File targetFile = new File(outputPath);

    if (file.lastModified() > targetFile.lastModified()) {
      context.processMessage(new ProgressMessage(JpsBundle.message("copying.resources", rd.getTarget().getModule().getName())));
      FileUtil.copyContent(file, targetFile);
    }
  }
}
