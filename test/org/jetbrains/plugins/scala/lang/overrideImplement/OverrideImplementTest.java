package org.jetbrains.plugins.scala.lang.overrideImplement;

import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;
import scala.None$;

/**
 * User: Alexander Podkhalyuzin
 * Date: 26.09.2008
 */
public class OverrideImplementTest extends BaseScalaFileSetTestCase {
  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/scala/lang/overrideImplement/data/";
  private static final String CARET_MARKER = "<caret>";

  private String removeMarker(String text) {
    int index = text.indexOf(CARET_MARKER);
    return text.substring(0, index) + text.substring(index + CARET_MARKER.length());
  }

  public OverrideImplementTest() {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        DATA_PATH
    );
  }

  /*
  *  File must be like:
  *  implement (or override) + " " +  methodName
  *  <typeDefinition>
  *  Use <caret> to specify caret position.
  */
  public String transform(String testName, String[] data) throws Exception {
    return (new OverrideImplementTestUtil()).transform(myProject, testName, data);
  }

  public static Test suite() {
    return new OverrideImplementTest();
  }
}
