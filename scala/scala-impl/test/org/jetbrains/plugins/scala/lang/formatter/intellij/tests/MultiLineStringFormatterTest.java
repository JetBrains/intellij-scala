package org.jetbrains.plugins.scala.lang.formatter.intellij.tests;

import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import junit.framework.Test;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.formatter.intellij.FormatterTestSuite;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;

public class MultiLineStringFormatterTest extends TestCase {
    public static Test suite() {
        return new FormatterTestSuite("/formatter/multiLineStringData/") {
            @Override
            protected void setUp(@NotNull Project project) {
                super.setUp(project);
                CommonCodeStyleSettings settings = getCommonSettings(project);
                ScalaCodeStyleSettings scalaSettings = getScalaSettings(project);

                scalaSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = true;
                scalaSettings.MULTILINE_STRING_ALIGN_DANGLING_CLOSING_QUOTES = false;
                scalaSettings.MULTILINE_STRING_OPENING_QUOTES_ON_NEW_LINE = true;
                settings.ALIGN_MULTILINE_BINARY_OPERATION = true;
                scalaSettings.MULTILINE_STRING_MARGIN_INDENT = 3;
            }
        };
    }
}
