package org.jetbrains.plugins.scala.refactoring.introduceVariable;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.base.ScalaLibraryLoader;
import org.jetbrains.plugins.scala.util.TestUtils;
import scala.Option$;

/**
 * @author Alexander Podkhalyuzin
 */
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
    Sdk nll = null;
    ScalaLibraryLoader loader = new ScalaLibraryLoader(project, modules[0], null, false, false, Option$.MODULE$.apply(nll));
    loader.loadLibrary(TestUtils.ScalaSdkVersion._2_10);
  }
}
