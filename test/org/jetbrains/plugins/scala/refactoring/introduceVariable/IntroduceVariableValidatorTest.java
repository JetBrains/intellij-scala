package org.jetbrains.plugins.scala.refactoring.introduceVariable;

import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.07.2008
 */


@RunWith(AllTests.class)
public class IntroduceVariableValidatorTest extends AbstractIntroduceVariableValidatorTestBase {

  @NonNls
  private static final String DATA_PATH = "/introduceVariable/validator/data";

  public IntroduceVariableValidatorTest() {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        TestUtils.getTestDataPath() + DATA_PATH
    );
  }

  @Override
  String getName(String fileText){
      return "value";
  }

  public static Test suite() {
    return new IntroduceVariableValidatorTest();
  }

}
