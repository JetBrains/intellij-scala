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
  private ScalaMavenLibraryUtil() {
  }

  @NonNls
  private static final String SCALA_TOOLS = "http://scala-tools.org/repo-releases/";


  public static LibraryInfo createJarDownloadInfo(final String jarName, final String version, final String downloadPrefix,
                                                  final String... requiredClasses) {
    return new LibraryInfo(jarName + ".jar", version, SCALA_TOOLS + "/" + downloadPrefix + "/" + jarName + "/" + version + "/" + jarName + "-" + version + ".jar",
        SCALA_TOOLS, requiredClasses);
  }


}

