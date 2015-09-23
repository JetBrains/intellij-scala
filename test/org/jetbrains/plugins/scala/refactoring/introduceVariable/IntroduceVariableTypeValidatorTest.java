package org.jetbrains.plugins.scala.refactoring.introduceVariable;

import junit.framework.Assert;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

/**
 * Created by Kate Ustyuzhanina on 8/5/15.
 */
@RunWith(AllTests.class)
public class IntroduceVariableTypeValidatorTest
        extends AbstractIntroduceVariableValidatorTestBase {

    @NonNls
    private static final String DATA_PATH = "/introduceVariable/validator/type";

    public IntroduceVariableTypeValidatorTest() {
        super(System.getProperty("path") != null ?
                        System.getProperty("path") :
                        TestUtils.getTestDataPath() + DATA_PATH
        );
    }

    @Override
    String getName(String fileText) {
        if (!(fileText.indexOf("//") == 0)) {
            Assert.assertTrue("Typename to validator should be in first comment statement.", false);
        }
        return fileText.substring(2, fileText.indexOf("\n")).replaceAll("\\W", "");
    }


    public static Test suite() {
        return new IntroduceVariableTypeValidatorTest();
    }
}
