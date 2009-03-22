package org.jetbrains.plugins.scala.config.util;

import com.intellij.facet.ui.libraries.LibraryDownloadInfo;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.facet.ui.libraries.RemoteRepositoryInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.ScalaBundle;

/**
 * @author ilyas
 */
public class ScalaMavenLibraryUtil {
  @NonNls
  private static final String[] MAVEN_MIRRORS = {
      "http://scala-tools.org/repo-releases/",
    };
  private static final RemoteRepositoryInfo MAVEN = new RemoteRepositoryInfo("maven", ScalaBundle.message("maven.repository.presentable.name"), MAVEN_MIRRORS);

  private ScalaMavenLibraryUtil() {
  }

  public static LibraryInfo createMavenJarInfo(final String jarName, final String version, final String downloadingPrefix,
                                                final String... requiredClasses) {
    LibraryDownloadInfo downloadInfo = new LibraryDownloadInfo(MAVEN, downloadingPrefix + "/" + jarName + "/" + version + "/" + jarName + "-" + version + ".jar",
        jarName, ".jar");
    return new LibraryInfo(jarName + ".jar", downloadInfo, requiredClasses);
  }

}

