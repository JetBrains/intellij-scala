/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.components;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.AbstractPsiBasedNode;
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.*;
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper;
import scala.collection.Iterator;
import scala.collection.Seq;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author ven
 */
public class ScalaDefsProjectViewProvider implements TreeStructureProvider, DumbAware {
  private static boolean hasNameOfFile(ScTypeDefinition type) {
    ScalaFile scalaFile = getFile(type);
    VirtualFile virtualFile = scalaFile == null ? null : scalaFile.getVirtualFile();
    if (virtualFile == null) {
      return true;
    } else {
      String fileName = virtualFile.getNameWithoutExtension();
      String className = type.name();
      return fileName.equals(className);
    }
  }

  private static ScalaFile getFile(ScTypeDefinition type) {
    return (ScalaFile) type.getContainingFile();
  }

  @NotNull
  public Collection<AbstractTreeNode> modify(@NotNull AbstractTreeNode parent,
                                             @NotNull Collection<AbstractTreeNode> children, ViewSettings settings) {
    List<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    
    for (AbstractTreeNode child : children) {
      Object childValue = child.getValue();

      if (childValue instanceof ScalaFile) {
        ScalaFile file = (ScalaFile) childValue;
        if (!file.isScriptFile()) {
          ScTypeDefinition[] definitions = file.typeDefinitionsArray();
          if (definitions.length == 1 && hasNameOfFile(definitions[0])) {
            result.add(new TypeDefinitionTreeNode(new ClassTreeNode(file.getProject(), definitions[0], settings)));
          } else {
            result.add(new ScalaFileTreeNode(file, settings));
          }
        } else result.add(new ScalaFileTreeNode(file, settings));
      } else if (childValue instanceof ScTemplateDefinition && child instanceof ClassTreeNode &&
          !(child instanceof TypeDefinitionTreeNode)) {
        result.add(new TypeDefinitionTreeNode((ClassTreeNode) child));
      } else result.add(child);
    }
    
    return result;
  }

  @Nullable
  public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    return null;
  }

  private static class TypeDefinitionTreeNode extends ClassTreeNode {
    public TypeDefinitionTreeNode(ClassTreeNode delegate) {
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
              result.add(new TypeDefinitionTreeNode(new ClassTreeNode(getProject(), (PsiClass) member, getSettings())));
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

  private static class ScalaFileTreeNode extends PsiFileNode {
    public ScalaFileTreeNode(ScalaFile scalaFile, ViewSettings settings) {
      super(scalaFile.getProject(), scalaFile, settings);
    }

    @Override
    public Collection<AbstractTreeNode> getChildrenImpl() {
      final ViewSettings settings = getSettings();
      final ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
      ScalaFile owner = (ScalaFile) getValue();
      if (!owner.isScriptFile()) {
        for (PsiClass aClass : owner.typeDefinitionsArray()) {
          result.add(new TypeDefinitionTreeNode(new ClassTreeNode(myProject, aClass, settings)));
        }
      }
      return result;
    }
   
    protected void updateImpl(PresentationData data) {
      super.updateImpl(data);
      data.setPresentableText(getValue().getName());
      data.setIcon(getValue().getIcon(Iconable.ICON_FLAG_READ_STATUS));
    }
  }
}