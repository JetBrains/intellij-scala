package org.jetbrains.plugins.scala.lang.actions.editor.enter;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.project.Project;
import junit.framework.Test;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class EnterActionTest extends TestCase {
    @NonNls
    private static final String DATA_PATH = "/actions/editor/enter/data";

    public static Test suite() {
        return new AbstractEnterActionTestBase(DATA_PATH) {
            @Override
            protected void setUp(@NotNull Project project) {
                super.setUp(project);
                CodeInsightSettings.getInstance().JAVADOC_STUB_ON_ENTER = false; //No, we don't need it.
            }

            @Override
            protected void setSettings(@NotNull Project project) {
                super.setSettings(project);
                getScalaSettings(project).USE_SCALADOC2_FORMATTING = true;
            }

            @Override
            public void tearDown(@NotNull Project project) {
                CodeInsightSettings.getInstance().JAVADOC_STUB_ON_ENTER = true;
                super.tearDown(project);
            }
        };
    }
}
