package org.jetbrains.plugins.scala.lang.overrideImplement;

import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;
import org.jetbrains.plugins.scala.util.TestUtils;
import scala.None$;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;

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

  @Override
  protected IdeaProjectTestFixture createFixtury() {
    final IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> builder = factory.createFixtureBuilder();
    builder.addModule(JavaModuleFixtureBuilder.class).addJdk(TestUtils.getMockJdk());
    return builder.getFixture();
  }
}
