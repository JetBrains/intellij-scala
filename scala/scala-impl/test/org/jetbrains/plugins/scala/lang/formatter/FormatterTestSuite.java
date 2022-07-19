package org.jetbrains.plugins.scala.lang.formatter;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase;

import java.util.Collections;

public abstract class FormatterTestSuite extends ScalaFileSetTestCase {

    public FormatterTestSuite(@NotNull @NonNls String path) {
        super(path);
    }

    @Override
    protected void setSettings(@NotNull Project project) {
        super.setSettings(project);
        getScalaSettings(project).USE_SCALADOC2_FORMATTING = true;
    }

    @NotNull
    @Override
    protected String transform(@NotNull String testName,
                               @NotNull String fileText,
                               @NotNull Project project) {
        final PsiFile psiFile = createLightFile(fileText, project);
        Runnable runnable = () -> ApplicationManager.getApplication().runWriteAction(() -> {
                    try {
                        CodeStyleManager.getInstance(project)
                                .reformatText(psiFile, Collections.singletonList(psiFile.getTextRange()));
                    } catch (IncorrectOperationException e) {
                        e.printStackTrace();
                    }
                }
        );
        CommandProcessor.getInstance().executeCommand(project, runnable, null, null);
        return psiFile.getText();
    }
}
