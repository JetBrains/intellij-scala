package org.jetbrains.jps.incremental.scala;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.builders.java.ResourceRootDescriptor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.messages.ProgressMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class ResourceUpdater {
  // Modified copy of org.jetbrains.jps.incremental.resources.ResourcesBuilder.copyResource
  public static void updateResource(CompileContext context, ResourceRootDescriptor rd, Path file, Path outputRoot) throws IOException {
    final String sourceRootPath = FileUtil.toSystemIndependentName(rd.getRootFile().getAbsolutePath());
    final String relativePath = FileUtil.getRelativePath(sourceRootPath, FileUtil.toSystemIndependentName(file.toAbsolutePath().normalize().toString()), '/');
    final String prefix = rd.getPackagePrefix();

    final StringBuilder targetPath = new StringBuilder();
    targetPath.append(FileUtil.toSystemIndependentName(outputRoot.toAbsolutePath().normalize().toString()));
    if (prefix.length() > 0) {
      targetPath.append('/').append(prefix.replace('.', '/'));
    }
    targetPath.append('/').append(relativePath);

    final String outputPath = targetPath.toString();
    final Path targetFile = Paths.get(outputPath);

    if (shouldCopy(file, targetFile)) {
      context.processMessage(new ProgressMessage(JpsBundle.message("copying.resources", rd.getTarget().getModule().getName())));
      final Path targetDirectory = targetFile.getParent();
      if (!Files.exists(targetDirectory)) {
        Files.createDirectories(targetDirectory);
      }
      Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static boolean shouldCopy(Path source, Path destination) throws IOException {
    return !Files.exists(destination) || Files.getLastModifiedTime(source).compareTo(Files.getLastModifiedTime(destination)) > 0;
  }
}
