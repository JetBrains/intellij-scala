package org.jetbrains.plugins.scala.failed.parser;

/**
 * @author Nikolay.Tropin
 */
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.testcases.BaseScalaFileSetTestCase;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

@RunWith(AllTests.class)
public abstract class FailedParserTest extends BaseScalaFileSetTestCase {
    @NonNls
    private static final String DATA_PATH = "/parser/failed";

    FailedParserTest() {
        super(System.getProperty("path") != null ?
                System.getProperty("path") :
                TestUtils.getTestDataPath() + DATA_PATH
        );
    }

    public String transform(String testName, String[] data) throws Exception {
        String fileText = data[0];
        PsiFile psiFile = TestUtils.createPseudoPhysicalScalaFile(getProject(), fileText);

        return DebugUtil.psiToString(psiFile, false).replace(":" + psiFile.getName(), "");

    }

    public static Test suite() {
        return new ScalaFailedParserTest();
    }

    @Override
    protected boolean shouldPass() {
        return false;
    }
}

