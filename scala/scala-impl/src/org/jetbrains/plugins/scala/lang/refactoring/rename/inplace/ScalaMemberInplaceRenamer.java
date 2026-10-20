package org.jetbrains.plugins.scala.lang.refactoring.rename.inplace;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.lang.psi.util.ScalaPsiNamesUtil;
import org.jetbrains.plugins.scala.lang.refactoring.rename.ScalaRenameUtil;
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil;
import scala.collection.immutable.Seq;

import java.util.Collection;
import java.util.Objects;

/** See ScalaLocalInplaceRenamer for the Scala 3.9 compiler workaround. Subclasses must also use Java. */
public class ScalaMemberInplaceRenamer extends MemberInplaceRenamer {
    private static final Key<ScalaRefactoringUtil.RevertInfo> REVERT_INFO = new Key<>("RevertInfo");

    private final PsiElement substituted;
    private final Editor editor;
    private final String oldName;
    private final int substitutorOffset;

    public ScalaMemberInplaceRenamer(PsiNamedElement elementToRename, PsiElement substituted, Editor editor,
                                    String initialName, String oldName) {
        super(elementToRename, substituted, editor, initialName, oldName);
        this.substituted = substituted;
        this.editor = editor;
        this.oldName = oldName;
        substitutorOffset = substituted.getTextRange().getStartOffset();
    }

    private ScalaMemberInplaceRenamer(PsiNamedElement elementToRename, PsiElement substituted, Editor editor, String name) {
        this(elementToRename, substituted, editor, name, name);
    }

    public ScalaMemberInplaceRenamer(PsiNamedElement elementToRename, PsiElement substituted, Editor editor) {
        this(elementToRename, substituted, editor, ScalaPsiNamesUtil.scalaName(substituted));
    }

    public ScalaMemberInplaceRenamer(PsiNamedElement elementToRename, PsiNamedElement substituted, Editor editor,
                                    Seq<PsiElement> additionalToRename) {
        this(elementToRename, substituted, editor);
    }

    @Override
    protected String getCommandName() {
        return myInitialName != null ? RefactoringBundle.message("renaming.command.name", myInitialName)
                : ScalaBundle.message("rename");
    }

    @Override
    public Collection<PsiReference> collectRefs(SearchScope referencesSearchScope) {
        Collection<PsiReference> references = super.collectRefs(referencesSearchScope);
        ScalaRenameUtil.addEndMarkerReference(getVariable(), references);
        return ScalaRenameUtil.filterAliasedReferences(references);
    }

    @Override
    public int restoreCaretOffset(int offset) {
        return Math.min(Math.max(offset, myCaretRangeMarker.getStartOffset()), myCaretRangeMarker.getEndOffset());
    }

    @Override
    public boolean acceptReference(PsiReference reference) {
        return true;
    }

    @Override
    public void beforeTemplateStart() {
        super.beforeTemplateStart();
        var revertInfo = new ScalaRefactoringUtil.RevertInfo(editor.getDocument().getText(), editor.getCaretModel().getOffset());
        editor.putUserData(REVERT_INFO, revertInfo);

        PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
        int offset = TargetElementUtil.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
        TextRange range = file.findElementAt(offset).getTextRange();
        myCaretRangeMarker = myEditor.getDocument().createRangeMarker(range);
        myCaretRangeMarker.setGreedyToLeft(true);
        myCaretRangeMarker.setGreedyToRight(true);
    }

    @Override
    public void revertState() {
        if (myOldName == null) return;

        CommandProcessor.getInstance().executeCommand(myProject, () -> {
            var revertInfo = editor.getUserData(REVERT_INFO);
            var document = myEditor.getDocument();
            if (revertInfo != null) {
                WriteAction.run(() -> {
                    document.replaceString(0, document.getTextLength(), revertInfo.fileText());
                    PsiDocumentManager.getInstance(myProject).commitDocument(document);
                });
                myEditor.getCaretModel().moveToOffset(revertInfo.caretOffset());
                myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
                PsiDocumentManager.getInstance(myEditor.getProject()).commitDocument(document);
                Class<?> clazz = myElementToRename.getClass();
                PsiElement element = TargetElementUtil.findTargetElement(myEditor,
                        TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED | TargetElementUtil.ELEMENT_NAME_ACCEPTED);
                if (element == null) {
                    myElementToRename = null;
                } else if (element instanceof PsiNamedElement named && named.getClass() == clazz) {
                    myElementToRename = named;
                } else {
                    PsiElement replacement = RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, myEditor);
                    myElementToRename = replacement instanceof PsiNamedElement named && named.getClass() == clazz ? named : null;
                }
            }
            if (!myProject.isDisposed() && myProject.isOpen()) {
                PsiDocumentManager.getInstance(myProject).commitDocument(document);
            }
        }, getCommandName(), null);
    }

    @Override
    public PsiNamedElement getVariable() {
        PsiNamedElement variable = super.getVariable();
        if (variable != null) return variable;
        return myElementToRename != null && myElementToRename.isValid()
                && Objects.equals(oldName, ScalaPsiNamesUtil.scalaName(myElementToRename)) ? myElementToRename : null;
    }

    @Override
    public PsiElement getSubstituted() {
        PsiElement subst = super.getSubstituted();
        if (subst != null && subst.textMatches(substituted.getText())) return subst;
        PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
        return psiFile == null ? null
                : PsiTreeUtil.getParentOfType(psiFile.findElementAt(substitutorOffset), PsiNameIdentifierOwner.class);
    }

    @Override
    public boolean isIdentifier(String newName, Language language) {
        return LanguageNamesValidation.INSTANCE.forLanguage(language).isIdentifier(newName, myProject);
    }

    @Override
    public VariableInplaceRenamer createInplaceRenamerToRestart(PsiNamedElement variable, Editor editor, String initialName) {
        return new ScalaMemberInplaceRenamer(variable, getSubstituted(), editor, initialName, oldName);
    }

    @Override
    public boolean performInplaceRename() {
        return ScalaMemberInplaceRenamerSupport.performInplaceRename(this, editor);
    }

    @Override
    public PsiElement getNameIdentifier() {
        if (myElementToRename instanceof PsiNamedElement && !myElementToRename.isPhysical()) return null;
        if (myElementToRename instanceof PsiNameIdentifierOwner owner
                && myElementToRename.getContainingFile().getViewProvider().getAllFiles().size() > 1) {
            return owner.getNameIdentifier();
        }
        return super.getNameIdentifier();
    }

    @Override
    public boolean startsOnTheSameElement(RefactoringActionHandler handler, PsiElement element) {
        if (!(handler instanceof ScalaMemberInplaceRenameHandler)) return false;
        int caretOffset = editor.getCaretModel().getOffset();
        return myCaretRangeMarker != null && myCaretRangeMarker.isValid()
                && myCaretRangeMarker.getStartOffset() <= caretOffset && myCaretRangeMarker.getEndOffset() >= caretOffset;
    }
}
