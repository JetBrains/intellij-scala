package org.jetbrains.plugins.scala.base;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.base.libraryLoaders.*;
import org.jetbrains.plugins.scala.util.TestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Alexander Podkhalyuzin
 */
public abstract class ScalaLightPlatformCodeInsightTestCaseAdapter extends LightPlatformCodeInsightTestCase {
    private CompositeLibrariesLoader myLibrariesLoader = null;

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
        return TestUtils.createJdk();
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
        Module module = getModule();

        ArrayList<LibraryLoader> libraryLoaders = new ArrayList<LibraryLoader>();
        libraryLoaders.add(new ScalaLibraryLoader(isIncludeReflectLibrary(), module, getProject()));

        String path = rootPath();
        if (path != null) {
            libraryLoaders.add(new SourcesLoader(path, module));
        }

        libraryLoaders.addAll(Arrays.asList(additionalLibraries(module)));

        LibraryLoader[] loaders = libraryLoaders.toArray(new LibraryLoader[libraryLoaders.size()]);
        myLibrariesLoader = CompositeLibrariesLoader$.MODULE$.apply(loaders, module);
        myLibrariesLoader.init(libVersion);

        TestUtils.disableTimerThread();
        //libLoader.clean();
    }

    protected void setUpWithoutScalaLib() throws Exception {
        super.setUp();
    }

    protected boolean isIncludeReflectLibrary() {
        return false;
    }

    protected ThirdPartyLibraryLoader[] additionalLibraries(Module module) {
        return new ThirdPartyLibraryLoader[0];
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
        configureFromFileText(fileName, StringUtil.convertLineSeparators(fileText));
    }

    @Override
    protected void tearDown() throws Exception {
        if (myLibrariesLoader != null) {
            myLibrariesLoader.clean();
            myLibrariesLoader = null;
        }

        super.tearDown();
    }
}