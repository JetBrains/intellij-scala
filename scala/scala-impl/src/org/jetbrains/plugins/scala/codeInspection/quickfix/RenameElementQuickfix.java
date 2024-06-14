package org.jetbrains.plugins.scala.codeInspection.quickfix;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.RefactoringQuickFix;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement;

import static java.util.Objects.requireNonNullElseGet;

public class RenameElementQuickfix extends LocalQuickFixOnPsiElement implements RefactoringQuickFix {
    private final @Nls String name;

    public RenameElementQuickfix(PsiElement myRef, @Nls String name) {
        super(myRef);
        this.name = name;
    }

    @Override
    public @IntentionName @NotNull String getText() {
        return name;
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
        return getText();
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
        if (!IntentionPreviewUtils.prepareElementForWrite(file) || !startElement.isValid()) return;
        doFix(startElement);
    }

    @Override
    public @NotNull RefactoringActionHandler getHandler() {
        return RefactoringActionHandlerFactory.getInstance().createRenameHandler();
    }

    @Override
    public @NotNull RefactoringActionHandler getHandler(@NotNull DataContext context) {
        return requireNonNullElseGet(
                RenameHandlerRegistry.getInstance().getRenameHandler(context),
                this::getHandler
        );
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
        PsiElement element = getElementToRefactor(previewDescriptor.getPsiElement());
        if (element instanceof ScNamedElement namedElement) {
            String what = UsageViewUtil.getType(element) + " '" + namedElement.name() + "'";
            String message = RefactoringBundle.message("rename.0.and.its.usages.preview.text", what);
            return new IntentionPreviewInfo.Html(HtmlChunk.text(message));
        }
        return IntentionPreviewInfo.EMPTY;
    }
}
