package org.jetbrains.plugins.scala.base;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.DelegatingProjectDescriptor;
import org.jetbrains.plugins.scala.base.libraryLoaders.LibraryLoader;
import org.jetbrains.plugins.scala.base.libraryLoaders.ScalaLibraryLoader;
import org.jetbrains.plugins.scala.base.libraryLoaders.SourcesLoader;
import org.jetbrains.plugins.scala.base.libraryLoaders.ThirdPartyLibraryLoader;
import org.jetbrains.plugins.scala.debugger.ScalaSdkOwner;
import org.jetbrains.plugins.scala.debugger.ScalaVersion;
import org.jetbrains.plugins.scala.debugger.Scala_2_10$;
import org.jetbrains.plugins.scala.util.TestUtils;
import scala.collection.Seq;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Alexander Podkhalyuzin
 */
public abstract class ScalaLightPlatformCodeInsightTestCaseAdapter extends LightPlatformCodeInsightTestCase implements ScalaSdkOwner {

    private static final ThirdPartyLibraryLoader[] EMPTY_LOADERS_ARRAY = new ThirdPartyLibraryLoader[0];

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

    @Override
    public ScalaVersion version() {
        return Scala_2_10$.MODULE$;
    }

    @Override
    public Project project() {
        return getProject();
    }

    @Override
    public Module module() {
        return getModule();
    }

    @Override
    public Seq<LibraryLoader> librariesLoaders() {
        return scala.collection.JavaConverters.asScalaBuffer(librariesLoadersAdapter());
    }

    private List<LibraryLoader> librariesLoadersAdapter() {
        Module module = module();

        ArrayList<LibraryLoader> result = new ArrayList<LibraryLoader>();
        result.add(new ScalaLibraryLoader(isIncludeReflectLibrary(), module));

        String path = rootPath();
        if (path != null) {
            result.add(new SourcesLoader(path, module));
        }

        result.addAll(Arrays.asList(additionalLibraries()));

        return result;
    }

    @NotNull
    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return new DelegatingProjectDescriptor(super.getProjectDescriptor()) {
            @Override
            public void setUpProject(Project project, SetupHandler handler) {
                super.setUpProject(project, handler);
                WriteAction.run(() -> {
                    afterSetUpProject();
                });
            }
        };
    }

    protected void afterSetUpProject() {
        setUpLibraries();
    }


    @Override
    protected void setUp() throws Exception {
        super.setUp();

        TestUtils.disableTimerThread();
    }

    protected boolean isIncludeReflectLibrary() {
        return false;
    }

    protected ThirdPartyLibraryLoader[] additionalLibraries() {
        return EMPTY_LOADERS_ARRAY;
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
        try {
            disposeLibraries();
        } finally {
            super.tearDown();
        }
    }
}