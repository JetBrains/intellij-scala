package org.jetbrains.plugins.scala.lang.parameterInfo;

import com.intellij.lang.ASTNode;
import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithTabActionSupport;
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;

//WARNING: should be implemented in Java until ParameterInfoHandler has type parameter
//<ParameterOwner extends Object & PsiElement>
//scalac generate wrong bridge methods in presence of such parameter types
public abstract class ScalaParameterInfoHandler<ParameterOwner extends PsiElement, ParameterType, ActualParameterType extends PsiElement>
        implements ParameterInfoHandlerWithTabActionSupport<ParameterOwner, ParameterType, ActualParameterType> {

    @Nullable
    protected abstract ParameterOwner findCall(ParameterInfoContext context);

    @Nullable
    @Override
    public ParameterOwner findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
        return findCall(context);
    }

    @Nullable
    @Override
    public ParameterOwner findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context) {
        return findCall(context);
    }

    @Override
    public void showParameterInfo(@NotNull ParameterOwner element, @NotNull CreateParameterInfoContext context) {
        context.showHint(element, element.getTextRange().getStartOffset(), this);
    }

    @Override
    public void updateParameterInfo(@NotNull ParameterOwner a, @NotNull UpdateParameterInfoContext context) {
        if (!context.getParameterOwner().equals(a)) {
            context.removeHint();
        }
        int offset = context.getOffset();
        ASTNode child = a.getNode().getFirstChildNode();
        int i = 0;
        while (child != null && child.getStartOffset() < offset) {
            if (child.getElementType() == ScalaTokenTypes.tCOMMA) i = i + 1;
            child = child.getTreeNext();
        }
        context.setCurrentParameter(i);
    }
}
