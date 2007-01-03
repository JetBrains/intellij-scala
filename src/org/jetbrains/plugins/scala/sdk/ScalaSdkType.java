package org.jetbrains.plugins.scala.sdk;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.SystemInfo;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaFileType;

import javax.swing.*;
import java.io.File;

/**
 * @author ven
 */
public class ScalaSdkType extends SdkType implements ApplicationComponent {
  @NonNls private static final String BIN_DIR_NAME = "bin";

  @NonNls private static final String LIB_DIR_NAME = "lib";

  @NonNls private static final String SCALA_EXE_NAME = SystemInfo.isWindows ? "scala.bat" : "scala";
  private static final String JAVA_SDK_NAME = "JAVA_SDK_NAME";

  public ScalaSdkType() {
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
    return "1.5";
  }

  @Nullable
  public String suggestSdkName(String currentSdkName, String sdkHome) {
    final int i = sdkHome.lastIndexOf('/');
    return i > 0 ? sdkHome.substring(i + 1) : sdkHome;
  }

  public void setupSdkPaths(Sdk sdk) {
    final SdkModificator sdkModificator = sdk.getSdkModificator();
    String dirPath = getLibraryDirPath(sdk);
    dirPath = dirPath.replace(File.separator, "/");
    VirtualFile libraryDir = LocalFileSystem.getInstance().findFileByPath(dirPath);
    assert libraryDir != null;
    VirtualFile[] files = libraryDir.getChildren();
    JarFileSystem jarFileSystem = JarFileSystem.getInstance();
    for (VirtualFile file : files) {
      if (file.getName().endsWith(".jar")) {
        VirtualFile inJar = jarFileSystem.findFileByPath(file.getPath() + JarFileSystem.JAR_SEPARATOR);
        if (inJar != null) {
          sdkModificator.addRoot(inJar, ProjectRootType.CLASS);
        }
      }
    }

    VirtualFile srcZip = jarFileSystem.findFileByPath(getConvertedHomePath(sdk) + "src.zip" + JarFileSystem.JAR_SEPARATOR);
    if (srcZip != null) {
      sdkModificator.addRoot(srcZip, ProjectRootType.SOURCE);
    }

    JavaSdkData data = (JavaSdkData) sdk.getSdkAdditionalData();
    if (data != null) {
      Sdk javaSdk = data.findSdk();
      if (javaSdk != null) {
        addClassesForJava(sdkModificator, javaSdk);
        addSourcesForJava(sdkModificator, javaSdk);
        addDocsForJava(sdkModificator, javaSdk);
      }
    }
    sdkModificator.commitChanges();
  }

  private void addClassesForJava(SdkModificator sdkModificator, @NotNull Sdk javaSdk) {
    addOrderEntriesForJava(OrderRootType.CLASSES, ProjectRootType.CLASS, javaSdk, sdkModificator);
  }

  private void addDocsForJava(SdkModificator sdkModificator, @NotNull Sdk javaSdk) {
    if (!addOrderEntriesForJava(OrderRootType.JAVADOC, ProjectRootType.JAVADOC, javaSdk, sdkModificator) &&
        SystemInfo.isMac){
      ProjectJdk [] jdks = ProjectJdkTable.getInstance().getAllJdks();
      for (ProjectJdk jdk : jdks) {
        if (jdk.getSdkType() instanceof JavaSdk) {
          addOrderEntriesForJava(OrderRootType.JAVADOC, ProjectRootType.JAVADOC, jdk, sdkModificator);
          break;
        }
      }
    }
  }

  private void addSourcesForJava(SdkModificator sdkModificator, @NotNull Sdk javaSdk) {
    if (!addOrderEntriesForJava(OrderRootType.SOURCES, ProjectRootType.SOURCE, javaSdk, sdkModificator)){
      if (SystemInfo.isMac) {
        ProjectJdk [] jdks = ProjectJdkTable.getInstance().getAllJdks();
        for (ProjectJdk jdk : jdks) {
          if (jdk.getSdkType() instanceof JavaSdk) {
            addOrderEntriesForJava(OrderRootType.SOURCES, ProjectRootType.SOURCE, jdk, sdkModificator);
            break;
          }
        }
      }
      else {
        final File jdkHome = new File(javaSdk.getHomePath()).getParentFile();
        @NonNls final String srcZip = "src.zip";
        final File jarFile = new File(jdkHome, srcZip);
        if (jarFile.exists()){
          JarFileSystem jarFileSystem = JarFileSystem.getInstance();
          String path = jarFile.getAbsolutePath().replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR;
          jarFileSystem.setNoCopyJarForPath(path);
          sdkModificator.addRoot(jarFileSystem.findFileByPath(path), ProjectRootType.SOURCE);
        }
      }
    }
  }

  private boolean addOrderEntriesForJava(OrderRootType orderRootType,
                                         ProjectRootType projectRootType,
                                         Sdk sdk,
                                         SdkModificator toModificator){
    boolean wasSmthAdded = false;
    final String[] entries = sdk.getRootProvider().getUrls(orderRootType);
    for (String entry : entries) {
      VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl(entry);
      toModificator.addRoot(virtualFile, projectRootType);
      wasSmthAdded = true;
    }
    return wasSmthAdded;
  }


  public AdditionalDataConfigurable createAdditionalDataConfigurable(SdkModel sdkModel, SdkModificator sdkModificator) {
    return new ScalaSdkConfigurable(sdkModel);
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
    Sdk javaSdk = ((JavaSdkData) sdk.getSdkAdditionalData()).findSdk();
    if (javaSdk == null) return null;
    return javaSdk.getSdkType().getToolsPath(javaSdk);
  }

  @Nullable
  public String getScalaCompilerPath (Sdk sdk) {
    return getLibraryDirPath(sdk) + File.separatorChar + "scala-compiler.jar";
  }

  @Nullable
  public Sdk getEncapsulatedSdk(Sdk sdk) {
    return ((JavaSdkData) sdk.getSdkAdditionalData()).findSdk();
  }

  @Nullable
  public String getVMExecutablePath(Sdk sdk) {
    Sdk javaSdk = ((JavaSdkData) sdk.getSdkAdditionalData()).findSdk();
    if (javaSdk == null) return null;
    return javaSdk.getSdkType().getVMExecutablePath(javaSdk);
  }

  @Nullable
  public String getScalaVMExecutablePath(Sdk sdk) {
    return getBinPath(sdk) + File.separatorChar + SCALA_EXE_NAME;
  }

  @Nullable
  public String getRtLibraryPath(Sdk sdk) {
    return getLibraryDirPath(sdk) + File.separatorChar + "scala-library.jar";
  }

  private String getLibraryDirPath(Sdk sdk) {
    return getConvertedHomePath(sdk) + LIB_DIR_NAME;
  }

  public void saveAdditionalData(SdkAdditionalData additionalData, Element additional) {
    if (!(additionalData instanceof JavaSdkData)) return;
    String sdkName = ((JavaSdkData) additionalData).getJavaSdkName();
    if (sdkName != null) {
      additional.setAttribute(JAVA_SDK_NAME, sdkName);
    }
  }

  public SdkAdditionalData loadAdditionalData(Element additional) {
    String name = additional.getAttributeValue(JAVA_SDK_NAME);
    if (name != null) return new JavaSdkData(name, null);
    return null;
  }

  public String getPresentableName() {
    return "Scala SDK";
  }

  public Icon getIcon() {
    return ScalaFileType.SCALA_LOGO;
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
