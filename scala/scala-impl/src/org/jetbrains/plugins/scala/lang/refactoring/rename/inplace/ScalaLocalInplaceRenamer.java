package org.jetbrains.plugins.scala.lang.refactoring.rename.inplace;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.psi.util.ScalaPsiNamesUtil;
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator$;
import org.jetbrains.plugins.scala.lang.refactoring.rename.ScalaRenameUtil;

import java.util.Collection;
import java.util.List;

import static com.intellij.refactoring.util.TextOccurrencesUtil.processUsagesInStringsAndComments;

/**
 * Implement this inheritance in Java until https://github.com/scala/scala3/issues/27208 is fixed in Scala 3.9.
 * Scala's incremental API extraction crashes on VariableInplaceRenamer's private sealed interface.
 */
public class ScalaLocalInplaceRenamer extends VariableInplaceRenamer {
    private final PsiNamedElement elementToRename;
    private final RangeMarker elementRange;

    public ScalaLocalInplaceRenamer(PsiNamedElement elementToRename, Editor editor, Project project,
                                   String initialName, String oldName) {
        super(elementToRename, editor, project, initialName, oldName);
        this.elementToRename = elementToRename;
        elementRange = editor.getDocument().createRangeMarker(elementToRename.getTextRange());
    }

    public ScalaLocalInplaceRenamer(@NotNull PsiNamedElement elementToRename, Editor editor) {
        this(elementToRename, editor, elementToRename.getProject(),
                ScalaPsiNamesUtil.scalaName(elementToRename), ScalaPsiNamesUtil.scalaName(elementToRename));
    }

    @Override
    public void collectAdditionalElementsToRename(List<? super Pair<PsiElement, TextRange>> stringUsages) {
        String stringToSearch = ScalaPsiNamesUtil.scalaName(elementToRename);
        if (stringToSearch == null) return;

        PsiFile file = currentFile();
        LocalSearchScope localScope = file == null ? LocalSearchScope.EMPTY : new LocalSearchScope(file);
        processUsagesInStringsAndComments(elementToRename, localScope, stringToSearch, true,
                (psiElement, textRange) -> {
                    stringUsages.add(Pair.create(psiElement, textRange));
                    return true;
                });
    }

    @Override
    public Collection<PsiReference> collectRefs(SearchScope referencesSearchScope) {
        Collection<PsiReference> references = super.collectRefs(referencesSearchScope);
        ScalaRenameUtil.addEndMarkerReference(getVariable(), references);
        return references;
    }

    @Override
    public boolean isIdentifier(String newName, Language language) {
        return ScalaNamesValidator$.MODULE$.isIdentifier(newName);
    }

    @Override
    public boolean startsOnTheSameElement(RefactoringActionHandler handler, PsiElement element) {
        return handler instanceof ScalaLocalInplaceRenameHandler && ScalaRenameUtil.sameElement(elementRange, element);
    }

    @Override
    public PsiElement checkLocalScope() {
        PsiFile file = currentFile();
        return file == null ? super.checkLocalScope() : file;
    }

    private PsiFile currentFile() {
        return PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    }
}
