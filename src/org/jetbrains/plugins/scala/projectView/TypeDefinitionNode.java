package org.jetbrains.plugins.scala.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.AbstractPsiBasedNode;
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition;
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper;
import scala.collection.Iterator;
import scala.collection.Seq;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

// TODO Convert to Scala & Refactor
class TypeDefinitionNode extends ClassTreeNode {
  public TypeDefinitionNode(ClassTreeNode delegate) {
    super(delegate.getProject(), delegate.getValue(), delegate.getSettings());
    if (delegate.getValue() instanceof ScTemplateDefinition) {
      myName = ((ScTemplateDefinition) delegate.getValue()).name();
    }
  }

  @Override
  public String getTitle() {
    PsiClass value = getValue();
    if (value != null && value.isValid()) {
      if (value instanceof ScTemplateDefinition) {
        return ((ScTemplateDefinition) value).qualifiedName();
      } else return value.getQualifiedName();
    } else return null;
  }

  @Override
  public void updateImpl(PresentationData data) {
    final PsiClass aClass = getValue();
    if (aClass != null && aClass instanceof ScTemplateDefinition) {
      data.setPresentableText(((ScTemplateDefinition) aClass).name());
    } else super.updateImpl(data);
  }

  @Override
  public Collection<AbstractTreeNode> getChildrenImpl() {
    if (!getSettings().isShowMembers()) return super.getChildrenImpl();
    ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    PsiClass value = getValue();
    if (value != null && value.isValid()) {
      if (value instanceof ScTemplateDefinition) {
        ScTemplateDefinition definition = (ScTemplateDefinition) value;
        Seq<ScMember> members = definition.members();
        Iterator<ScMember> iterator = members.iterator();
        while (iterator.hasNext()) {
          ScMember member = iterator.next();
          if (member instanceof PsiClass) {
            result.add(new TypeDefinitionNode(new ClassTreeNode(getProject(), (PsiClass) member, getSettings())));
          } else if (member instanceof ScNamedElement) {
            result.add(new ScNamedElementTreeNode(getProject(), (ScNamedElement) member, getSettings()));
          } else if (member instanceof ScValue) {
            ScValue v = (ScValue) member;
            Iterator<ScTypedDefinition> declared = v.declaredElements().iterator();
            while (declared.hasNext()) {
              ScTypedDefinition typed = declared.next();
              result.add(new ScNamedElementTreeNode(getProject(), typed, getSettings()));
            }
          } else if (member instanceof ScVariable) {
            ScVariable v = (ScVariable) member;
            Iterator<ScTypedDefinition> declared = v.declaredElements().iterator();
            while (declared.hasNext()) {
              ScTypedDefinition typed = declared.next();
              result.add(new ScNamedElementTreeNode(getProject(), typed, getSettings()));
            }
          }
        }
      }
    }
    return result;
  }

  private class ScNamedElementTreeNode extends AbstractPsiBasedNode<ScNamedElement> {
    public ScNamedElementTreeNode(Project project, ScNamedElement function, ViewSettings settings) {
      super(project, function, settings);
    }

    @Override
    protected PsiElement extractPsiFromValue() {
      return getValue();
    }

    @Override
    protected Collection<AbstractTreeNode> getChildrenImpl() {
      return Collections.emptyList();
    }

    @Override
    protected void updateImpl(PresentationData data) {
      ScNamedElement namedElement = getValue();
      if (namedElement != null) {
        String text = namedElement.name();
        data.setPresentableText(text);
      }
    }
  }

  @Override
  public PsiClass getPsiClass() {
    PsiClass psiClass = super.getPsiClass();
    if (psiClass instanceof ScObject) {
      ScObject obj = (ScObject) psiClass;
      return new PsiClassWrapper(obj, obj.qualifiedName(), obj.name());
    } else {
      return psiClass;
    }
  }
}
