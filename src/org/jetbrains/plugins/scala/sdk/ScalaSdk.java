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
    @NonNls
    private static final String BIN_DIR_NAME = "bin";
    @NonNls
    private static final String LIB_DIR_NAME = "lib";

    public ScalaSdk() {
        super("scala sdk");
    }

    public String suggestHomePath() {
        return null;
    }

    public boolean isValidSdkHome(String path) {
        return true;
    }

    @Nullable
    public String getVersionString(String sdkHome) {
        return null;
    }

    public String suggestSdkName(String currentSdkName, String sdkHome) {
        return null;
    }

    public void setupSdkPaths(Sdk sdk) {
        //final SdkModificator sdkModificator = sdk.getSdkModificator();

    }

    public AdditionalDataConfigurable createAdditionalDataConfigurable(SdkModel sdkModel, SdkModificator sdkModificator) {
        AdditionalDataConfigurable additionalDataConfigurable = new ScalaSdkConfigurable();
        return additionalDataConfigurable;
    }

    @Nullable
    public String getBinPath(Sdk sdk) {
        return "foo";
    }

    @Nullable
    public String getToolsPath(Sdk sdk) {
        return "bar";
    }

    @Nullable
    public String getVMExecutablePath(Sdk sdk) {
        return "foo";
    }

    @Nullable
    public String getRtLibraryPath(Sdk sdk) {
        return "foo";
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

    private static VirtualFile[] getScalaLibrary(String home) {
        ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
        return result.toArray(new VirtualFile[result.size()]);
    }

    public void initComponent() {
    }

    public void disposeComponent() {
    }
}
