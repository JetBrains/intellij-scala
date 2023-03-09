package org.jetbrains.plugins.scala.lang.surroundWith;

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.lang.Language;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaLanguage;
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase;
import org.jetbrains.plugins.scala.lang.surroundWith.descriptors.ScalaSurroundDescriptors$;
import scala.Tuple4;

import static junit.framework.TestCase.assertNotNull;

class ScalaSurroundWithFileSetTestCase extends ScalaFileSetTestCase {
    private final Language language;

    ScalaSurroundWithFileSetTestCase(String path, Language lang) {
        super(path);
        language = lang;
    }

    ScalaSurroundWithFileSetTestCase(String path) {
        this(path, ScalaLanguage.INSTANCE);
    }

    @Override
    protected @NotNull Language getLanguage() {
        return language;
    }

    private void doSurround(final Project project, final PsiFile file,
                            Surrounder surrounder, int startSelection, int endSelection) {
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        try {
            Editor editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(project, file.getVirtualFile(), 0), false);
            assertNotNull(editor);
            editor.getSelectionModel().setSelection(startSelection, endSelection);
            SurroundWithHandler.invoke(project, editor, file, surrounder);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            fileEditorManager.closeFile(file.getVirtualFile());
        }
    }

    @Override
    @NotNull
    protected String transform(@NotNull String testName,
                               @NotNull String fileText,
                               @NotNull Project project) {
        Tuple4<String, Integer, Integer, Integer> res = SurroundWithTestUtil.prepareFile(fileText);
        final PsiFile psiFile = createLightFile(res._1(), project);
        final Surrounder[] surrounder = ScalaSurroundDescriptors$.MODULE$.getSurroundDescriptors()[0].getSurrounders();

        WriteCommandAction.runWriteCommandAction(project, () ->
                doSurround(project, psiFile, surrounder[res._4()], res._2(), res._3())
        );

        return psiFile.getText();
    }
}
