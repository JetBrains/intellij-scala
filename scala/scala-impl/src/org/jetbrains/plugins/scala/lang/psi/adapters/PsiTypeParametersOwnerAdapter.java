package org.jetbrains.plugins.scala.lang.psi.adapters;

import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterListOwner;
import org.jetbrains.annotations.NotNull;

//This interface is required because it's impossible to implement
//overloaded method with different array return types in scala.
public interface PsiTypeParametersOwnerAdapter extends PsiTypeParameterListOwner {
    PsiTypeParameter[] psiTypeParameters();

    @NotNull
    @Override
    default PsiTypeParameter[] getTypeParameters() {
        return psiTypeParameters();
    }
}
