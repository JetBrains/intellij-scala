package org.jetbrains.plugins.scala.refactoring.introduceVariable;

import junit.framework.Test;
import org.jetbrains.annotations.NonNls;

/**
 * @author Alexander Podkhalyuzin
 */
public class ProblematicIntroduceVariableTest extends AbstractIntroduceVariableTestBase {

  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/scala/refactoring/introduceVariable/problematicData/";

  public ProblematicIntroduceVariableTest() {
    super(DATA_PATH);
  }

  public static Test suite() {
    return new ProblematicIntroduceVariableTest();
  }
}
