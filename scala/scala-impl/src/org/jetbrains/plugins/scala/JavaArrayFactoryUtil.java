package org.jetbrains.plugins.scala;

import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayFactory;
import org.jetbrains.plugins.scala.lang.psi.api.base.*;
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern;
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern;
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement;
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression;
import org.jetbrains.plugins.scala.lang.psi.api.statements.*;
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
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateDerives;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition;
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult;

/**
 * @author Alexander Podkhalyuzin
 */
public class JavaArrayFactoryUtil {

  public static ArrayFactory<ScalaResolveResult> ScalaResolveResultFactory = ScalaResolveResult[]::new;

  public static ArrayFactory<ScTypeElement> ScTypeElementFactory = ScTypeElement[]::new;

  public static ArrayFactory<ScAnnotation> ScAnnotationFactory = ScAnnotation[]::new;

  public static ArrayFactory<ScPrimaryConstructor> ScPrimaryConstructorFactory = ScPrimaryConstructor[]::new;

  public static ArrayFactory<ScExpression> ScExpressionFactory = ScExpression[]::new;

  public static ArrayFactory<ScDeclaredElementsHolder> ScDeclaredElementsHolderFactory = ScDeclaredElementsHolder[]::new;

  public static ArrayFactory<ScMember> ScMemberFactory = ScMember[]::new;

  public static ArrayFactory<ScFunction> ScFunctionFactory = ScFunction[]::new;

  public static ArrayFactory<ScValueOrVariable> ScPropertyFactory = ScValueOrVariable[]::new;

  public static ArrayFactory<ScFunctionDefinition> ScFunctionDefinitionFactory = ScFunctionDefinition[]::new;

  public static ArrayFactory<ScTypeAlias> ScTypeAliasFactory = ScTypeAlias[]::new;

  public static ArrayFactory<ScEnumCases> ScEnumCasesFactory = ScEnumCases[]::new;

  public static ArrayFactory<ScTemplateBody> ScTemplateBodyFactory = ScTemplateBody[]::new;

  public static ArrayFactory<ScImportExpr> ScImportExprFactory = ScImportExpr[]::new;

  public static ArrayFactory<ScImportStmt> ScImportStmtFactory = ScImportStmt[]::new;

  public static ArrayFactory<ScImportSelector> ScImportSelectorFactory = ScImportSelector[]::new;

  public static ArrayFactory<ScIdList> ScIdListFactory = ScIdList[]::new;

  public static ArrayFactory<ScPatternList> ScPatternListFactory = ScPatternList[]::new;

  public static ArrayFactory<ScReferencePattern> ScReferencePatternFactory = ScReferencePattern[]::new;

  public static ArrayFactory<ScFieldId> ScFieldIdFactory = ScFieldId[]::new;

  public static ArrayFactory<ScAnnotations> ScAnnotationsFactory = ScAnnotations[]::new;

  public static ArrayFactory<PsiElement> PsiElementFactory = PsiElement[]::new;

  public static ArrayFactory<ScTypeParam> ScTypeParamFactory = ScTypeParam[]::new;

  public static ArrayFactory<ScTypeParamClause> ScTypeParamClauseFactory = ScTypeParamClause[]::new;

  public static ArrayFactory<ScExtendsBlock> ScExtendsBlockFactory = ScExtendsBlock[]::new;

  public static ArrayFactory<ScParameterClause> ScParameterClauseFactory = ScParameterClause[]::new;

  public static ArrayFactory<ScModifierList> ScModifierListFactory = ScModifierList[]::new;

  public static ArrayFactory<ScPackaging> ScPackagingFactory = ScPackaging[]::new;

  public static ArrayFactory<ScTypeDefinition> ScTypeDefinitionFactory = ScTypeDefinition[]::new;

  public static ArrayFactory<ScTemplateDefinition> ScTemplateDefinitionArrayFactory = ScTemplateDefinition[]::new;

  public static ArrayFactory<ScTemplateParents> ScTemplateParentsFactory = ScTemplateParents[]::new;

  public static ArrayFactory<ScTemplateDerives> ScTemplateDerivesFactory = ScTemplateDerives[]::new;

  public static ArrayFactory<ScEarlyDefinitions> ScEarlyDefinitionsFactory = ScEarlyDefinitions[]::new;

  public static ArrayFactory<ScParameter> ScParameterFactory = ScParameter[]::new;

  public static ArrayFactory<ScBindingPattern> ScBindingPatternFactory = ScBindingPattern[]::new;

}
