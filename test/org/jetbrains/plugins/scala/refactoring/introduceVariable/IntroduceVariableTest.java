package org.jetbrains.plugins.scala.refactoring.introduceVariable;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.base.ScalaLibraryLoader;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

/**
 * @author Alexander Podkhalyuzin
 */
@RunWith(AllTests.class)
public class IntroduceVariableTest extends AbstractIntroduceVariableTestBase {
  @NonNls
  private static final String DATA_PATH = "/introduceVariable/data";

  public IntroduceVariableTest() {
    super(TestUtils.getTestDataPath() + DATA_PATH);
  }

  public static Test suite() {
    return new IntroduceVariableTest();
  }

  @Override
  protected void setUp(Project project) {
    super.setUp(project);
    Module[] modules = ModuleManager.getInstance(project).getModules();
    ScalaLibraryLoader loader = ScalaLibraryLoader.withMockJdk(project, modules[0], null, false, false, false);
    loader.loadLibrary(TestUtils.DEFAULT_SCALA_SDK_VERSION);
  }
}
