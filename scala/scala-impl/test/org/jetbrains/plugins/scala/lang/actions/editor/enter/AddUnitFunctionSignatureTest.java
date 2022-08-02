package org.jetbrains.plugins.scala.lang.actions.editor.enter;

import com.intellij.openapi.project.Project;
import junit.framework.Test;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

public class AddUnitFunctionSignatureTest extends TestCase {
    public static Test suite() {
        return new AbstractEnterActionTestBase("/actions/editor/enter/addunit") {
            @Override
            protected void setSettings(@NotNull Project project) {
                super.setSettings(project);

                getScalaSettings(project).TYPE_ANNOTATION_UNIT_TYPE = true;
                getScalaSettings(project).ENFORCE_FUNCTIONAL_SYNTAX_FOR_UNIT = true;
            }
        };
    }
}
