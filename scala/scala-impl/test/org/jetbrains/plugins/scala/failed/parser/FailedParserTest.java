package org.jetbrains.plugins.scala.failed.parser;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import junit.framework.Test;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase;
import org.jetbrains.plugins.scala.util.TestUtils;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

/**
 * @author Nikolay.Tropin
 */
@RunWith(AllTests.class)
public abstract class FailedParserTest extends ScalaFileSetTestCase {

    public FailedParserTest() {
        super("/parser/failed");
    }

    @NotNull
    protected String transform(@NotNull String testName,
                               @NotNull String fileText,
                               @NotNull Project project) {
        PsiFile psiFile = TestUtils.createPseudoPhysicalScalaFile(project, fileText);

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

