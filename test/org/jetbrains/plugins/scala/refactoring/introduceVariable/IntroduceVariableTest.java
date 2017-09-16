package org.jetbrains.plugins.scala.refactoring.introduceVariable;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.SlowTests;
import org.jetbrains.plugins.scala.base.libraryLoaders.JdkLoader;
import org.jetbrains.plugins.scala.base.libraryLoaders.JdkLoader$;
import org.jetbrains.plugins.scala.base.libraryLoaders.LibraryLoader;
import org.jetbrains.plugins.scala.base.libraryLoaders.ScalaLibraryLoader;
import org.jetbrains.plugins.scala.debugger.ScalaSdkOwner;
import org.jetbrains.plugins.scala.debugger.ScalaVersion;
import org.jetbrains.plugins.scala.debugger.Scala_2_10$;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;
import scala.collection.JavaConverters;
import scala.collection.Seq;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Podkhalyuzin
 */
@RunWith(AllTests.class)
@Category(SlowTests.class)
public class IntroduceVariableTest extends AbstractIntroduceVariableTestBase implements ScalaSdkOwner {
    @NonNls
    private static final String DATA_PATH = "/introduceVariable/data";

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
    public Project project() {
        return getProject();
    }

    @Override
    public Module module() {
        return ModuleManager.getInstance(project()).getModules()[0];
    }

    @Override
    public Seq<LibraryLoader> librariesLoaders() {
        return JavaConverters.asScalaBuffer(libraryLoadersAdapter());
    }

    private List<LibraryLoader> libraryLoadersAdapter() {
        Module module = module();

        List<LibraryLoader> result = new ArrayList<LibraryLoader>();
        result.add(new ScalaLibraryLoader(false, module));
        result.add(new JdkLoader(JdkLoader$.MODULE$.apply$default$1(), module));
        return result;
    }

    @Override
    public void setUpLibraries() {
        for (LibraryLoader libraryLoader : libraryLoadersAdapter()) {
            libraryLoader.init(version());
        }
    }

    @Override
    public void tearDownLibraries() {
        for (LibraryLoader libraryLoader : libraryLoadersAdapter()) {
            libraryLoader.clean();
        }
    }

    @Override
    protected void setUp(Project project) {
        super.setUp(project);
        setUpLibraries();
    }

    public void tearDown() throws Exception {
        tearDownLibraries();
    }
}
