package org.jetbrains.plugins.scala.lang.psi.uast.psi;

import com.intellij.lang.Language;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.impl.light.LightTypeElement;
import com.intellij.psi.impl.light.LightVariableBuilder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;


/**
 * {@link LightVariableBuilder} does not support annotations, this inheritor
 * implements needed methods by given annotations for both fields and local variables.
 */
public class LightVariableWithGivenAnnotationsBuilder extends LightFieldBuilder implements PsiLocalVariable {
    private final PsiAnnotation[] myAnnotations;
    private volatile LightModifierList myModifierList;

    /**
     * @param annotations Annotations array that result light field will contain
     */
    public LightVariableWithGivenAnnotationsBuilder(
            String name,
            PsiType psiType,
            PsiClass containingClass,
            PsiAnnotation[] annotations,
            String[] modifiers
    ) {
        super(name, psiType, containingClass);
        myAnnotations = annotations;
        setContainingClass(containingClass);
        setModifiers(modifiers);
    }

    //region Wraps modifier list into LightModifiersListWithGivenAnnotations
    @Override
    @NotNull
    public PsiModifierList getModifierList() {
        return myModifierList;
    }

    @Override
    public LightFieldBuilder setModifiers(String... modifiers) {
        myModifierList = new LightModifiersListWithGivenAnnotations(getManager(), getLanguage(), modifiers);
        return this;
    }

    @Override
    public boolean hasModifierProperty(@NonNls @NotNull String name) {
        return myModifierList.hasModifierProperty(name);
    }
    //endregion

    @Override
    public PsiFile getContainingFile() {
        PsiClass containingClass = getContainingClass();
        return (containingClass == null) ? null : containingClass.getContainingFile();
    }

    @NotNull
    @Override
    public PsiTypeElement getTypeElement() {
        return new LightTypeElement(getManager(), getType());
    }

    /**
     * Overrides {@link LightModifierList} which does not support annotations
     * to provide annotations given to {@link LightVariableWithGivenAnnotationsBuilder}.
     */
    private class LightModifiersListWithGivenAnnotations extends LightModifierList {
        LightModifiersListWithGivenAnnotations(PsiManager manager, final Language language, String... modifiers) {
            super(manager, language, modifiers);
        }

        @Override
        @NotNull
        public PsiAnnotation[] getAnnotations() {
            return myAnnotations;
        }

        @Override
        public PsiAnnotation findAnnotation(@NotNull String qualifiedName) {
            return Arrays.stream(myAnnotations)
                    .filter(a -> {
                        String aFqn = a.getQualifiedName();
                        return aFqn != null && aFqn.equals(qualifiedName);
                    })
                    .findFirst()
                    .orElse(null);
        }
    }
}