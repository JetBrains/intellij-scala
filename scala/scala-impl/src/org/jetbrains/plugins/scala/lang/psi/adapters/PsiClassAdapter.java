package org.jetbrains.plugins.scala.lang.psi.adapters;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

//This interface is required because it's impossible to implement
//overloaded method with different array return types in scala.
public interface PsiClassAdapter extends PsiClass, PsiTypeParametersOwnerAdapter {
    PsiField[] psiFields();

    PsiMethod[] psiMethods();

    PsiClass[] psiInnerClasses();

    @NotNull
    @Override
    default PsiField[] getFields() {
        return psiFields();
    }

    @NotNull
    @Override
    default PsiMethod[] getMethods() {
        return psiMethods();
    }

    @NotNull
    @Override
    default PsiClass[] getInnerClasses() {
        return psiInnerClasses();
    }
}
