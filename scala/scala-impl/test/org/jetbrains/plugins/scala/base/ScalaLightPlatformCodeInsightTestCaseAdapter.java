package org.jetbrains.plugins.scala.base;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.base.DelegatingProjectDescriptor;
import org.jetbrains.plugins.scala.base.libraryLoaders.*;
import org.jetbrains.plugins.scala.base.ScalaSdkOwner;
import org.jetbrains.plugins.scala.debugger.ScalaVersion;
import org.jetbrains.plugins.scala.debugger.Scala_2_10$;
import org.jetbrains.plugins.scala.util.TestUtils;
import scala.collection.Seq;
import scala.collection.immutable.Vector$;
import scala.collection.mutable.Buffer;

import java.io.IOException;
import java.util.ArrayList;

/**
 * @author Alexander Podkhalyuzin
 * @deprecated use {@link org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter} instead
 */
@Deprecated
public abstract class ScalaLightPlatformCodeInsightTestCaseAdapter extends LightPlatformCodeInsightTestCase implements ScalaSdkOwner {

    private static final ThirdPartyLibraryLoader[] EMPTY_LOADERS_ARRAY = new ThirdPartyLibraryLoader[0];

    protected String sourceRootPath() {
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
        return SmartJDKLoader$.MODULE$.getOrCreateJDK(JavaSdkVersion.JDK_1_8);
    }

    @Override
    public ScalaVersion version() {
        return Scala_2_10$.MODULE$;
    }

    @Override
    public Seq<LibraryLoader> librariesLoaders() {
        ArrayList<LibraryLoader> back = new ArrayList<>();

        ScalaSDKLoader scalaLoader = new ScalaSDKLoader(isIncludeReflectLibrary());
        back.add(scalaLoader);

        String path = sourceRootPath();
        if (path != null) {
            back.add(new SourcesLoader(path));
        }

        Buffer<LibraryLoader> result = scala.collection.JavaConverters.asScalaBuffer(back);
        Seq addLibs = additionalLibraries();
        //noinspection unchecked (because variance)
        result.$plus$plus$eq(addLibs);

        return result;
    }

    @NotNull
    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
        return new ScalaLightProjectDescriptor(){
            @Override
            public void tuneModule(Module module) {
                afterSetUpProject(module);
            }

            @Nullable
            @Override
            public Sdk getSdk() {
                return SmartJDKLoader$.MODULE$.getOrCreateJDK(JavaSdkVersion.JDK_1_8);
            }
        };
    }

    protected void afterSetUpProject(Module module) {
        Registry.get("ast.loading.filter").setValue(true, getTestRootDisposable());
        setUpLibraries(module);
    }


    @Override
    protected void setUp() throws Exception {
        super.setUp();

        TestUtils.disableTimerThread();
    }

    protected boolean isIncludeReflectLibrary() {
        return false;
    }

    protected Seq<LibraryLoader> additionalLibraries() {
        return Vector$.MODULE$.empty();
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
            disposeLibraries(getModule());
            Sdk[] allJdks = ProjectJdkTable.getInstance().getAllJdks();
            WriteAction.run(() -> {
                for (Sdk jdk : allJdks) {
                    ProjectJdkTable.getInstance().removeJdk(jdk);
                }
            });
        } finally {
            super.tearDown();
        }
    }
}