package org.jetbrains.plugins.scala.lang.actions.editor.enter.multiline_string;

import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import junit.framework.Test;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.actions.editor.enter.AbstractEnterActionTestBase;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;

public class MultiLineStringAllTest extends TestCase {
    public static final String DATA_PATH = "/actions/editor/enter/multiLineStringData/indentAndMargin";

    public static Test suite() {
        return new AbstractEnterActionTestBase(DATA_PATH) {
            @Override
            protected void setSettings(@NotNull Project project) {
                super.setSettings(project);
                final CommonCodeStyleSettings settings = getCommonSettings(project);
                final ScalaCodeStyleSettings scalaSettings = getScalaSettings(project);

                scalaSettings.MULTILINE_STRING_CLOSING_QUOTES_ON_NEW_LINE = true;
                scalaSettings.MULTILINE_STRING_INSERT_MARGIN_ON_ENTER = true;
                scalaSettings.MULTILINE_STRING_MARGIN_INDENT = 3;
            }
        };
    }
}
