package org.jetbrains.plugins.scala.lang.actions.editor.enter;

import junit.framework.Test;
import org.jetbrains.annotations.NonNls;

/**
 * @author Alexander Podkhalyuzin
 */
public class ProblematicEnterActionTest extends AbstractEnterActionTest {
  @NonNls
  private static final String DATA_PATH = "./test/org/jetbrains/plugins/scala/lang/actions/editor/enter/problematicData/";

  public ProblematicEnterActionTest() {
    super(DATA_PATH);
  }

  public static Test suite() {
    return new ProblematicEnterActionTest();
  }
}
