package org.jetbrains.plugins.scala.base;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.util.TestUtils;

import java.io.IOException;

/**
 * @author Alexander Podkhalyuzin
 */
public abstract class ScalaLightPlatformCodeInsightTestCaseAdapter extends LightPlatformCodeInsightTestCase {
  private String JDK_HOME = TestUtils.getDefaultJdk();
  private ScalaLibraryLoader myLibraryLoader = null;

  protected String rootPath() {
    return null;
  }

  protected final String baseRootPath() {
    return TestUtils.getTestDataPath() + "/";
  }

  protected VirtualFile getSourceRootAdapter() {
    return getSourceRoot();
  }

  @Override
  protected Sdk getProjectJDK() {
    return JavaSdk.getInstance().createJdk("java sdk", JDK_HOME, false);
  }

  protected TestUtils.ScalaSdkVersion getDefaultScalaSDKVersion() {
    return TestUtils.DEFAULT_SCALA_SDK_VERSION;
  }

  @Override
  protected void setUp() throws Exception {
    setUp(getDefaultScalaSDKVersion());
  }

  protected void setUp(TestUtils.ScalaSdkVersion libVersion) throws Exception {
    super.setUp();
    myLibraryLoader = new ScalaLibraryLoader(getProject(), getModule(), rootPath(),
        isIncludeScalazLibrary(), isIncludeReflectLibrary(), isIncludeSprayLibrary(), ScalaLibraryLoader.getSdkNone());
    myLibraryLoader.loadLibrary(libVersion);
    //libLoader.clean();
  }

  protected void setUpWithoutScalaLib() throws Exception {
    super.setUp();
  }

  protected boolean isIncludeReflectLibrary() {
      return false;
  }

  protected boolean isIncludeScalazLibrary() {
    return false;
  }

  protected boolean isIncludeSprayLibrary() {
    return false;
  }

  protected VirtualFile getVFileAdapter() {
    return getVFile();
  }

  protected Editor getEditorAdapter() {
    return getEditor();
  }

  protected Project getProjectAdapter() {
    return getProject();
  }

  protected Module getModuleAdapter() {
    return getModule();
  }

  protected PsiFile getFileAdapter() {
    return getFile();
  }

  protected PsiManager getPsiManagerAdapter() {
    return getPsiManager();
  }

  protected DataContext getCurrentEditorDataContextAdapter() {
    return getCurrentEditorDataContext();
  }

  protected void executeActionAdapter(String actionId) {
    executeAction(actionId);
  }

  protected void configureFromFileTextAdapter(@NonNls final String fileName,
                                              @NonNls final String fileText) throws IOException {
    configureFromFileText(fileName, fileText);
  }

  @Override
  protected void tearDown() throws Exception {
    if (myLibraryLoader != null) {
      myLibraryLoader.clean();
      myLibraryLoader = null;
    }

    super.tearDown();
  }
}