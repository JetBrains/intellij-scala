package org.jetbrains.plugins.scala.lang.psi.uast.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

//TODO: rename anonimous -> anonymous
public abstract class PsiClassAnonimousWrapper implements PsiAnonymousClass {

    abstract protected PsiClass getDelegate();

    @Override
    @Nullable
    public String getQualifiedName() {
        return getDelegate().getQualifiedName();
    }

    @Override
    public boolean isInterface() {
        return getDelegate().isInterface();
    }

    @Override
    public boolean isAnnotationType() {
        return getDelegate().isAnnotationType();
    }

    @Override
    public boolean isEnum() {
        return getDelegate().isEnum();
    }

    @Override
    public @Nullable PsiReferenceList getExtendsList() {
        return getDelegate().getExtendsList();
    }

    @Override
    public @Nullable PsiReferenceList getImplementsList() {
        return getDelegate().getImplementsList();
    }

    @NotNull
    @Override
    public PsiClassType[] getExtendsListTypes() {
        return getDelegate().getExtendsListTypes();
    }

    @NotNull
    @Override
    public PsiClassType[] getImplementsListTypes() {
        return getDelegate().getImplementsListTypes();
    }

    @Override
    @Nullable
    public PsiClass getSuperClass() {
        return getDelegate().getSuperClass();
    }

    @NotNull
    @Override
    public PsiClass[] getInterfaces() {
        return getDelegate().getInterfaces();
    }

    @NotNull
    @Override
    public PsiClass[] getSupers() {
        return getDelegate().getSupers();
    }

    @NotNull
    @Override
    public PsiClassType[] getSuperTypes() {
        return getDelegate().getSuperTypes();
    }

    @NotNull
    @Override
    public PsiField[] getFields() {
        return getDelegate().getFields();
    }

    @NotNull
    @Override
    public PsiMethod[] getMethods() {
        return getDelegate().getMethods();
    }

    @NotNull
    @Override
    public PsiMethod[] getConstructors() {
        return getDelegate().getConstructors();
    }

    @NotNull
    @Override
    public PsiClass[] getInnerClasses() {
        return getDelegate().getInnerClasses();
    }

    @NotNull
    @Override
    public PsiClassInitializer[] getInitializers() {
        return getDelegate().getInitializers();
    }

    @NotNull
    @Override
    public PsiField[] getAllFields() {
        return getDelegate().getAllFields();
    }

    @NotNull
    @Override
    public PsiMethod[] getAllMethods() {
        return getDelegate().getAllMethods();
    }

    @NotNull
    @Override
    public PsiClass[] getAllInnerClasses() {
        return getDelegate().getAllInnerClasses();
    }

    @Override
    public @Nullable PsiField findFieldByName(@NonNls String name, boolean checkBases) {
        return getDelegate().findFieldByName(name, checkBases);
    }

    @Override
    public @Nullable PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
        return getDelegate().findMethodBySignature(patternMethod, checkBases);
    }

    @NotNull
    @Override
    public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
        return getDelegate().findMethodsBySignature(patternMethod, checkBases);
    }

    @NotNull
    @Override
    public PsiMethod[] findMethodsByName(@NonNls String name, boolean checkBases) {
        return getDelegate().findMethodsByName(name, checkBases);
    }

    @Override
    public @NotNull List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NonNls String name, boolean checkBases) {
        return getDelegate().findMethodsAndTheirSubstitutorsByName(name, checkBases);
    }

    @Override
    public @NotNull List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
        return getDelegate().getAllMethodsAndTheirSubstitutors();
    }

    @Override
    @Nullable
    public PsiClass findInnerClassByName(@NonNls String name, boolean checkBases) {
        return getDelegate().findInnerClassByName(name, checkBases);
    }

    @Override
    public @Nullable PsiElement getLBrace() {
        return getDelegate().getLBrace();
    }

    @Override
    public @Nullable PsiElement getRBrace() {
        return getDelegate().getRBrace();
    }

    @Override
    public @Nullable PsiIdentifier getNameIdentifier() {
        return getDelegate().getNameIdentifier();
    }

    @Override
    public PsiElement getScope() {
        return getDelegate().getScope();
    }

    @Override
    public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
        return getDelegate().isInheritor(baseClass, checkDeep);
    }

    @Override
    public boolean isInheritorDeep(PsiClass baseClass, @Nullable PsiClass classToByPass) {
        return getDelegate().isInheritorDeep(baseClass, classToByPass);
    }

    @Override
    @Nullable
    public PsiClass getContainingClass() {
        return getDelegate().getContainingClass();
    }

    @Override
    public @NotNull Collection<HierarchicalMethodSignature> getVisibleSignatures() {
        return getDelegate().getVisibleSignatures();
    }

    @Override
    public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
        return getDelegate().setName(name);
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    @Nullable
    public String getName() {
        return getDelegate().getName();
    }

    @Override
    @Contract(pure = true)
    public @NotNull Project getProject() throws PsiInvalidElementAccessException {
        return getDelegate().getProject();
    }

    @Override
    @Contract(pure = true)
    public @NotNull Language getLanguage() {
        return getDelegate().getLanguage();
    }

    @Override
    @Contract(pure = true)
    public PsiManager getManager() {
        return getDelegate().getManager();
    }

    @NotNull
    @Override
    @Contract(pure = true)
    public PsiElement[] getChildren() {
        return getDelegate().getChildren();
    }

    @Override
    @Contract(pure = true)
    public PsiElement getParent() {
        return getDelegate().getParent();
    }

    @Override
    @Contract(pure = true)
    public PsiElement getFirstChild() {
        return getDelegate().getFirstChild();
    }

    @Override
    @Contract(pure = true)
    public PsiElement getLastChild() {
        return getDelegate().getLastChild();
    }

    @Override
    @Contract(pure = true)
    public PsiElement getNextSibling() {
        return getDelegate().getNextSibling();
    }

    @Override
    @Contract(pure = true)
    public PsiElement getPrevSibling() {
        return getDelegate().getPrevSibling();
    }

    @Override
    @Contract(pure = true)
    public PsiFile getContainingFile() throws PsiInvalidElementAccessException {
        return getDelegate().getContainingFile();
    }

    @Override
    @Contract(pure = true)
    public TextRange getTextRange() {
        return getDelegate().getTextRange();
    }

    @Override
    @Contract(pure = true)
    public int getStartOffsetInParent() {
        return getDelegate().getStartOffsetInParent();
    }

    @Override
    @Contract(pure = true)
    public int getTextLength() {
        return getDelegate().getTextLength();
    }

    @Override
    @Contract(pure = true)
    public @Nullable PsiElement findElementAt(int offset) {
        return getDelegate().findElementAt(offset);
    }

    @Override
    @Contract(pure = true)
    public @Nullable PsiReference findReferenceAt(int offset) {
        return getDelegate().findReferenceAt(offset);
    }

    @Override
    @Contract(pure = true)
    public int getTextOffset() {
        return getDelegate().getTextOffset();
    }

    @Override
    @Contract(pure = true)
    public String getText() {
        return getDelegate().getText();
    }

    @NotNull
    @Override
    @Contract(pure = true)
    public char[] textToCharArray() {
        return getDelegate().textToCharArray();
    }

    @NotNull
    @Override
    @Contract(pure = true)
    public PsiElement getNavigationElement() {
        return getDelegate().getNavigationElement();
    }

    @Override
    @Contract(pure = true)
    public PsiElement getOriginalElement() {
        return getDelegate().getOriginalElement();
    }

    @Override
    @Contract(pure = true)
    public boolean textMatches(@NotNull @NonNls CharSequence text) {
        return getDelegate().textMatches(text);
    }

    @Override
    @Contract(pure = true)
    public boolean textMatches(@NotNull PsiElement element) {
        return getDelegate().textMatches(element);
    }

    @Override
    @Contract(pure = true)
    public boolean textContains(char c) {
        return getDelegate().textContains(c);
    }

    @Override
    public void accept(@NotNull PsiElementVisitor visitor) {
        getDelegate().accept(visitor);
    }

    @Override
    public void acceptChildren(@NotNull PsiElementVisitor visitor) {
        getDelegate().acceptChildren(visitor);
    }

    @Override
    public PsiElement copy() {
        return getDelegate().copy();
    }

    @Override
    public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
        return getDelegate().add(element);
    }

    @Override
    public PsiElement addBefore(@NotNull PsiElement element, @Nullable PsiElement anchor) throws IncorrectOperationException {
        return getDelegate().addBefore(element, anchor);
    }

    @Override
    public PsiElement addAfter(@NotNull PsiElement element, @Nullable PsiElement anchor) throws IncorrectOperationException {
        return getDelegate().addAfter(element, anchor);
    }

    @Override
    @Deprecated
    public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
        getDelegate().checkAdd(element);
    }

    @Override
    public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
        return getDelegate().addRange(first, last);
    }

    @Override
    public PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor) throws IncorrectOperationException {
        return getDelegate().addRangeBefore(first, last, anchor);
    }

    @Override
    public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException {
        return getDelegate().addRangeAfter(first, last, anchor);
    }

    @Override
    public void delete() throws IncorrectOperationException {
        getDelegate().delete();
    }

    @Override
    @Deprecated
    public void checkDelete() throws IncorrectOperationException {
        getDelegate().checkDelete();
    }

    @Override
    public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
        getDelegate().deleteChildRange(first, last);
    }

    @Override
    public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
        return getDelegate().replace(newElement);
    }

    @Override
    @Contract(pure = true)
    public boolean isValid() {
        return getDelegate().isValid();
    }

    @Override
    @Contract(pure = true)
    public boolean isWritable() {
        return getDelegate().isWritable();
    }

    @Override
    @Contract(pure = true)
    public @Nullable PsiReference getReference() {
        return getDelegate().getReference();
    }

    @NotNull
    @Override
    @Contract(pure = true)
    public PsiReference[] getReferences() {
        return getDelegate().getReferences();
    }

    @Override
    @Contract(pure = true)
    public <T> T getCopyableUserData(Key<T> key) {
        return getDelegate().getCopyableUserData(key);
    }

    @Override
    public <T> void putCopyableUserData(Key<T> key, @Nullable T value) {
        getDelegate().putCopyableUserData(key, value);
    }

    @Override
    public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, @Nullable PsiElement lastParent, @NotNull PsiElement place) {
        return getDelegate().processDeclarations(processor, state, lastParent, place);
    }

    @Override
    @Contract(pure = true)
    public @Nullable PsiElement getContext() {
        return getDelegate().getContext();
    }

    @Override
    @Contract(pure = true)
    public boolean isPhysical() {
        return getDelegate().isPhysical();
    }

    @Override
    @Contract(pure = true)
    public @NotNull GlobalSearchScope getResolveScope() {
        return getDelegate().getResolveScope();
    }

    @Override
    @Contract(pure = true)
    public @NotNull SearchScope getUseScope() {
        return getDelegate().getUseScope();
    }

    @Override
    @Contract(pure = true)
    public ASTNode getNode() {
        return getDelegate().getNode();
    }

    @Override
    @Contract(pure = true)
    public boolean isEquivalentTo(PsiElement another) {
        return getDelegate().isEquivalentTo(another);
    }

    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
        return getDelegate().getUserData(key);
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
        getDelegate().putUserData(key, value);
    }

    @Override
    public Icon getIcon(int flags) {
        return getDelegate().getIcon(flags);
    }

    @Override
    public @Nullable PsiModifierList getModifierList() {
        return getDelegate().getModifierList();
    }

    @Override
    public boolean hasModifierProperty(@NonNls @NotNull String name) {
        return getDelegate().hasModifierProperty(name);
    }


    @Override
    public boolean isDeprecated() {
        return getDelegate().isDeprecated();
    }

    @Override
    public @Nullable ItemPresentation getPresentation() {
        return getDelegate().getPresentation();
    }

    @Override
    public void navigate(boolean requestFocus) {
        getDelegate().navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
        return getDelegate().canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
        return getDelegate().canNavigateToSource();
    }

    @Override
    public @Nullable PsiDocComment getDocComment() {
        return getDelegate().getDocComment();
    }

    @Override
    public boolean hasTypeParameters() {
        return getDelegate().hasTypeParameters();
    }

    @Override
    public @Nullable PsiTypeParameterList getTypeParameterList() {
        return getDelegate().getTypeParameterList();
    }

    @NotNull
    @Override
    public PsiTypeParameter[] getTypeParameters() {
        return getDelegate().getTypeParameters();
    }

}
