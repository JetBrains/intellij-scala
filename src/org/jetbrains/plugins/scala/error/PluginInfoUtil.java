package org.jetbrains.plugins.scala.error;

import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;

public class PluginInfoUtil {// The same as in manifest
  @NonNls
  public static final String REVISION = "Revision";
  @NonNls
  public static final String BUILD = "Build";
  @NonNls
  public static final String SCALA_KEY = "SCL";
  @NonNls
  public static final String JIRA = "http://www.jetbrains.net/jira";
  @NonNls
  public static final String JIRA_BROWSE = JIRA + "/browse/";
  @NonNls
  public static final String JIRA_BROWSE_SCALA_URL = JIRA_BROWSE + "/" + SCALA_KEY;
  @NonNls
  static final String JIRA_RPC = JIRA + "/rpc/xmlrpc";
  @NonNls
  static final String JIRA_LOGIN_COMMAND = "jira1.login";
  @NonNls
  public static final String JIRA_REGISTER_URL = "http://www.jetbrains.net/jira/secure/Signup!default.jspa";

  @NonNls
  private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";

  @Nullable
  public static Manifest getManifest() {
    final String jarPath = PathUtil.getJarPathForClass(PluginInfoUtil.class);
    if (jarPath == null || !jarPath.endsWith(".jar")) {
      return null;
    }
    final ZipFile jarFile;
    try {
      jarFile = new ZipFile(jarPath);
      final InputStream inputStream = jarFile.getInputStream(jarFile.getEntry(MANIFEST_PATH));
      return new Manifest(inputStream);
    } catch (final IOException e) {
      return null;
    }
  }

  @Nullable
  public static String getRevision(@Nullable final Manifest manifest) {
    return manifest == null ? null : manifest.getMainAttributes().getValue(REVISION);
  }

  @Nullable
  public static String getBuild(@Nullable final Manifest manifest) {
    return manifest == null ? null : manifest.getMainAttributes().getValue(BUILD);
  }
}
