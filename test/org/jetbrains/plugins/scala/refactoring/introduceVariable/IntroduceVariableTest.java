package org.jetbrains.plugins.scala.refactoring.introduceVariable;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.base.libraryLoaders.*;
import org.jetbrains.plugins.scala.debugger.ScalaSdkOwner;
import org.jetbrains.plugins.scala.debugger.ScalaVersion;
import org.jetbrains.plugins.scala.debugger.Scala_2_10$;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

/**
 * @author Alexander Podkhalyuzin
 */
@RunWith(AllTests.class)
public class IntroduceVariableTest extends AbstractIntroduceVariableTestBase implements ScalaSdkOwner {
    @NonNls
    private static final String DATA_PATH = "/introduceVariable/data";

    private CompositeLibrariesLoader myLibrariesLoader = null;

    @Override
    public ScalaVersion version() {
        return Scala_2_10$.MODULE$;
    }

    public IntroduceVariableTest() {
        super(TestUtils.getTestDataPath() + DATA_PATH);
    }

    public static Test suite() {
        return new IntroduceVariableTest();
    }

    @Override
    protected void setUp(Project project) {
        super.setUp(project);
        Module module = ModuleManager.getInstance(project).getModules()[0];

        LibraryLoader[] loaders = new LibraryLoader[2];
        loaders[0] = new ScalaLibraryLoader(false, module, project);
        loaders[1] = new JdkLoader(JdkLoader$.MODULE$.apply$default$1(), module);

        myLibrariesLoader = CompositeLibrariesLoader$.MODULE$.apply(loaders, module);
        myLibrariesLoader.init(version());
    }

    public void tearDown() throws Exception {
        if (myLibrariesLoader != null) {
            myLibrariesLoader.clean();
            myLibrariesLoader = null;
        }
    }
}
