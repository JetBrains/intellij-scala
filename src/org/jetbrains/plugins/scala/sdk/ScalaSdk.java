package org.jetbrains.plugins.scala.sdk;

import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.roots.OrderRootType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
//import org.jetbrains.idea.devkit.DevKitBundle;
import org.jdom.Element;

import java.util.ArrayList;
import java.io.File;

/**
 * User: Dmitry.Krasilschikov
 * Date: 28.09.2006
 * Time: 19:44:20
 */
public class ScalaSdk extends SdkType implements ApplicationComponent {
  @NonNls private static final String BIN_DIR_NAME = "bin";

  @NonNls private static final String LIB_DIR_NAME = "lib";

  @NonNls private static final String SCALA_EXE_NAME = "scala";

  public ScalaSdk() {
    super("scala sdk");
  }

  public String suggestHomePath() {
    return null;
  }

  public boolean isValidSdkHome(String path) {
    final File home = new File(path);
    final File binDir = new File(home, BIN_DIR_NAME);
    if (!binDir.exists()) return false;
    final File[] files = binDir.listFiles();
    for (final File file : files) {
      if (file.getName().startsWith("scalac")) return true;
    }
    return false;
  }

  @Nullable
  public String getVersionString(String sdkHome) {
    return null;
  }

  @Nullable
  public String suggestSdkName(String currentSdkName, String sdkHome) {
    final int i = sdkHome.lastIndexOf('/');
    return i > 0 ? sdkHome.substring(i + 1) : sdkHome;
  }

  public void setupSdkPaths(Sdk sdk) {
    //final SdkModificator sdkModificator = sdk.getSdkModificator();

  }

  public AdditionalDataConfigurable createAdditionalDataConfigurable(SdkModel sdkModel, SdkModificator sdkModificator) {
    return new ScalaSdkConfigurable();
  }

  private static String getConvertedHomePath(Sdk sdk) {
    String path = sdk.getHomePath().replace('/', File.separatorChar);
    if (!path.endsWith(File.separator)) {
      path += File.separator;
    }
    return path;
  }

  @Nullable
  public String getBinPath(Sdk sdk) {
    return getConvertedHomePath(sdk) + BIN_DIR_NAME;
  }

  @Nullable
  public String getToolsPath(Sdk sdk) {
    return null;
  }

  @Nullable
  public String getVMExecutablePath(Sdk sdk) {
    return getBinPath(sdk) + File.separatorChar + SCALA_EXE_NAME;
  }

  @Nullable
  public String getRtLibraryPath(Sdk sdk) {
    return getConvertedHomePath(sdk) + LIB_DIR_NAME + File.separatorChar + "scala-library.jar";
  }

  public void saveAdditionalData(SdkAdditionalData additionalData, Element additional) {
  }

  public SdkAdditionalData loadAdditionalData(Element additional) {
    return null;
  }

  public String getPresentableName() {
    return "Scala SDK";
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "scala sdk";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
