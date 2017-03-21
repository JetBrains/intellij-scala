package org.jetbrains.plugins.scala.refactoring.introduceVariable;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.scala.lang.actions.ActionTestBase;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaValidator;
import org.jetbrains.plugins.scala.util.TestUtils;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Kate Ustyuzhanina
 */

public abstract class AbstractIntroduceVariableValidatorTestBase extends ActionTestBase {

    protected static final String ALL_MARKER = "<all>";

    protected Editor myEditor;
    protected FileEditorManager fileEditorManager;
    protected PsiFile myFile;

    public AbstractIntroduceVariableValidatorTestBase(String path) {
        super(path);
    }

    public String transform(String testName, String[] data) throws Exception {
        setSettings();
        String fileText = data[0];
        final PsiFile psiFile = TestUtils.createPseudoPhysicalScalaFile(getProject(), fileText);
        return processFile(psiFile);
    }

    protected String removeAllMarker(String text) {
        int index = text.indexOf(ALL_MARKER);
        myOffset = index - 1;
        return text.substring(0, index) + text.substring(index + ALL_MARKER.length());
    }

    private String processFile(final PsiFile file) throws IncorrectOperationException, InvalidDataException, IOException {
        StringBuilder result = new StringBuilder();
        boolean replaceAllOccurences;

        String fileText = file.getText();
        int startOffset = fileText.indexOf(TestUtils.BEGIN_MARKER);
        if (startOffset < 0) {
            startOffset = fileText.indexOf(ALL_MARKER);
            replaceAllOccurences = true;
            fileText = removeAllMarker(fileText);
        } else {
            replaceAllOccurences = false;
            fileText = TestUtils.removeBeginMarker(fileText);
        }

        int endOffset = fileText.indexOf(TestUtils.END_MARKER);
        fileText = TestUtils.removeEndMarker(fileText);
        myFile = TestUtils.createPseudoPhysicalScalaFile(getProject(), fileText);
        fileEditorManager = FileEditorManager.getInstance(getProject());
        myEditor = fileEditorManager.openTextEditor(new OpenFileDescriptor(getProject(), myFile.getVirtualFile(), 0), false);

        try {
            String typeName = getName(fileText);
            ScalaValidator validator = IntroduceVariableTestUtil.getValidator(getProject(), myEditor,
                    (ScalaFile) myFile, startOffset, endOffset);

            Set<String> set = new HashSet<String>();
            // TODO: to be rewritten in scala
//            set.addAll(validator.isOKImpl(typeName, replaceAllOccurences).values());
            for (String s : set) result.append(s).append("\n");
        } finally {
            fileEditorManager.closeFile(myFile.getVirtualFile());
            myEditor = null;
        }
        return result.toString();
    }

    abstract String  getName(String fileText);
}
