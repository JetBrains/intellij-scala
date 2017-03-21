package org.jetbrains.plugins.scala;

import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import com.intellij.util.ArrayFactory;
import org.jetbrains.plugins.scala.lang.psi.api.base.*;
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern;
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotations;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaredElementsHolder;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction;
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias;
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter;
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause;
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam;
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelector;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult;

/**
 * @author Alexander Podkhalyuzin
 */
public class JavaArrayFactoryUtil {
  public static ArrayFactory<ResolveResult> ResolveResultFactory = new ArrayFactory<ResolveResult>() {
    @Override
    public ResolveResult[] create(int count) {
      return new ResolveResult[count];
    }
  };

  public static ArrayFactory<ScalaResolveResult> ScalaResolveResultFactory = new ArrayFactory<ScalaResolveResult>() {
    @Override
    public ScalaResolveResult[] create(int count) {
      return new ScalaResolveResult[count];
    }
  };

  public static ArrayFactory<ScTypeElement> ScTypeElementFactory = new ArrayFactory<ScTypeElement>() {
    @Override
    public ScTypeElement[] create(int count) {
      return new ScTypeElement[count];
    }
  };

  public static ArrayFactory<ScAnnotation> ScAnnotationFactory = new ArrayFactory<ScAnnotation>() {
    @Override
    public ScAnnotation[] create(int count) {
      return new ScAnnotation[count];
    }
  };

  public static ArrayFactory<ScPrimaryConstructor> ScPrimaryConstructorFactory = new ArrayFactory<ScPrimaryConstructor>() {
    @Override
    public ScPrimaryConstructor[] create(int count) {
      return new ScPrimaryConstructor[count];
    }
  };

  public static ArrayFactory<ScExpression> ScExpressionFactory = new ArrayFactory<ScExpression>() {
    @Override
    public ScExpression[] create(int count) {
      return new ScExpression[count];
    }
  };

  public static ArrayFactory<ScDeclaredElementsHolder> ScDeclaredElementsHolderFactory = new ArrayFactory<ScDeclaredElementsHolder>() {
    @Override
    public ScDeclaredElementsHolder[] create(int count) {
      return new ScDeclaredElementsHolder[count];
    }
  };

  public static ArrayFactory<ScMember> ScMemberFactory = new ArrayFactory<ScMember>() {
    @Override
    public ScMember[] create(int count) {
      return new ScMember[count];
    }
  };

  public static ArrayFactory<ScFunction> ScFunctionFactory = new ArrayFactory<ScFunction>() {
    @Override
    public ScFunction[] create(int count) {
      return new ScFunction[count];
    }
  };

  public static ArrayFactory<ScTypeAlias> ScTypeAliasFactory = new ArrayFactory<ScTypeAlias>() {
    @Override
    public ScTypeAlias[] create(int count) {
      return new ScTypeAlias[count];
    }
  };

  public static ArrayFactory<ScTemplateBody> ScTemplateBodyFactory = new ArrayFactory<ScTemplateBody>() {
    @Override
    public ScTemplateBody[] create(int count) {
      return new ScTemplateBody[count];
    }
  };

  public static ArrayFactory<ScImportExpr> ScImportExprFactory = new ArrayFactory<ScImportExpr>() {
    @Override
    public ScImportExpr[] create(int count) {
      return new ScImportExpr[count];
    }
  };

  public static ArrayFactory<ScImportStmt> ScImportStmtFactory = new ArrayFactory<ScImportStmt>() {
    @Override
    public ScImportStmt[] create(int count) {
      return new ScImportStmt[count];
    }
  };

  public static ArrayFactory<ScImportSelector> ScImportSelectorFactory = new ArrayFactory<ScImportSelector>() {
    @Override
    public ScImportSelector[] create(int count) {
      return new ScImportSelector[count];
    }
  };

  public static ArrayFactory<ScIdList> ScIdListFactory = new ArrayFactory<ScIdList>() {
    @Override
    public ScIdList[] create(int count) {
      return new ScIdList[count];
    }
  };

  public static ArrayFactory<ScPatternList> ScPatternListFactory = new ArrayFactory<ScPatternList>() {
    @Override
    public ScPatternList[] create(int count) {
      return new ScPatternList[count];
    }
  };

  public static ArrayFactory<ScReferencePattern> ScReferencePatternFactory = new ArrayFactory<ScReferencePattern>() {
    @Override
    public ScReferencePattern[] create(int count) {
      return new ScReferencePattern[count];
    }
  };

  public static ArrayFactory<ScFieldId> ScFieldIdFactory = new ArrayFactory<ScFieldId>() {
    @Override
    public ScFieldId[] create(int count) {
      return new ScFieldId[count];
    }
  };

  public static ArrayFactory<ScAnnotations> ScAnnotationsFactory = new ArrayFactory<ScAnnotations>() {
    @Override
    public ScAnnotations[] create(int count) {
      return new ScAnnotations[count];
    }
  };

  public static ArrayFactory<PsiElement> PsiElementFactory = new ArrayFactory<PsiElement>() {
    @Override
    public PsiElement[] create(int count) {
      return new PsiElement[count];
    }
  };

  public static ArrayFactory<ScTypeParam> ScTypeParamFactory = new ArrayFactory<ScTypeParam>() {
    @Override
    public ScTypeParam[] create(int count) {
      return new ScTypeParam[count];
    }
  };

  public static ArrayFactory<ScTypeParamClause> ScTypeParamClauseFactory = new ArrayFactory<ScTypeParamClause>() {
    @Override
    public ScTypeParamClause[] create(int count) {
      return new ScTypeParamClause[count];
    }
  };

  public static ArrayFactory<ScExtendsBlock> ScExtendsBlockFactory = new ArrayFactory<ScExtendsBlock>() {
    @Override
    public ScExtendsBlock[] create(int count) {
      return new ScExtendsBlock[count];
    }
  };

  public static ArrayFactory<ScParameterClause> ScParameterClauseFactory = new ArrayFactory<ScParameterClause>() {
    @Override
    public ScParameterClause[] create(int count) {
      return new ScParameterClause[count];
    }
  };

  public static ArrayFactory<ScModifierList> ScModifierListFactory = new ArrayFactory<ScModifierList>() {
    @Override
    public ScModifierList[] create(int count) {
      return new ScModifierList[count];
    }
  };

  public static ArrayFactory<ScPackaging> ScPackagingFactory = new ArrayFactory<ScPackaging>() {
    @Override
    public ScPackaging[] create(int count) {
      return new ScPackaging[count];
    }
  };

  public static ArrayFactory<ScTypeDefinition> ScTypeDefinitionFactory = new ArrayFactory<ScTypeDefinition>() {
    @Override
    public ScTypeDefinition[] create(int count) {
      return new ScTypeDefinition[count];
    }
  };

  public static ArrayFactory<ScTemplateDefinition> ScTemplateDefinitionArrayFactory = new ArrayFactory<ScTemplateDefinition>() {
    @Override
    public ScTemplateDefinition[] create(int count) {
      return new ScTemplateDefinition[count];
    }
  };

  public static ArrayFactory<ScTemplateParents> ScTemplateParentsFactory = new ArrayFactory<ScTemplateParents>() {
    @Override
    public ScTemplateParents[] create(int count) {
      return new ScTemplateParents[count];
    }
  };

  public static ArrayFactory<ScEarlyDefinitions> ScEarlyDefinitionsFactory = new ArrayFactory<ScEarlyDefinitions>() {
    @Override
    public ScEarlyDefinitions[] create(int count) {
      return new ScEarlyDefinitions[count];
    }
  };

  public static ArrayFactory<ScParameter> ScParameterFactory = new ArrayFactory<ScParameter>() {
    @Override
    public ScParameter[] create(int count) {
      return new ScParameter[count];
    }
  };
}
