package org.jetbrains.plugins.scala.lang.psi.impl.toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressManager}
import com.intellij.openapi.project.DumbService
import com.intellij.psi._
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, ModTracker, cachedInUserData}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.externalLibraries.contextApplied.{ContextApplied, ContextAppliedUtil}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypeParametersOwner}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.ScalaPsiElementCreationException
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScNamedBeginImpl
import org.jetbrains.plugins.scala.lang.psi.light.ScLightField
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameterType
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.AccessModifierRenderer
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor

import javax.swing.Icon

class ScClassImpl(stub: ScTemplateDefinitionStub[ScClass],
                  nodeType: ScTemplateDefinitionElementType[ScClass],
                  node: ASTNode,
                  debugName: String)
  extends ScTypeDefinitionImpl(stub, nodeType, node, debugName)
    with ScClass
    with ScTypeParametersOwner
    with ScNamedBeginImpl
    with ContextApplied.SyntheticElementsOwner {

  override protected def targetTokenType: ScalaTokenType = ScalaTokenType.ClassKeyword

  override protected def baseIcon: Icon =
    if (this.hasAbstractModifier) Icons.ABSTRACT_CLASS
    else Icons.CLASS

  //do not use fakeCompanionModule, it will be used in Stubs.
  override def additionalClassJavaName: Option[String] =
    if (isCase) Some(getName() + "$") else None

  override def constructor: Option[ScPrimaryConstructor] =
    this.stubOrPsiChild(ScalaElementType.PRIMARY_CONSTRUCTOR)

  import com.intellij.psi.scope.PsiScopeProcessor
  import com.intellij.psi.{PsiElement, ResolveState}

  override def syntheticContextAppliedDefs: Seq[ScalaPsiElement] =
    cachedInUserData("syntheticContextAppliedDefs", this, BlockModificationTracker(this)) {
      ContextAppliedUtil.createSyntheticElementsFor(
        this,
        this,
        this.constructor.fold(Seq.empty[ScParameter])(_.parameters),
        this.typeParameters
      )
    }

  override def processDeclarationsForTemplateBody(processor: PsiScopeProcessor,
                                                  state: ResolveState,
                                                  lastParent: PsiElement,
                                                  place: PsiElement): Boolean = {
    if (DumbService.getInstance(getProject).isDumb)
      return true

    if (!super.processDeclarationsForTemplateBody(processor, state, lastParent, place))
      return false

    constructor match {
      case Some(constr) if place != null && PsiTreeUtil.isContextAncestor(constr, place, false) =>
      //ignore, should be processed in ScParameters
      case _ =>
        for (p <- parameters) {
          ProgressManager.checkCanceled()
          if (processor.is[BaseProcessor]) {
            // don't expose class parameters to Java.
            if (!processor.execute(p, state))
              return false
          }
        }
    }

    //process context-applied synthetic elements
    if (!super[SyntheticElementsOwner].processDeclarations(processor, state, lastParent, place))
      return false

    super[ScTypeParametersOwner].processDeclarations(processor, state, lastParent, place)
  }

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean =
    processDeclarationsImpl(processor, state, lastParent, place)

  override def isCase: Boolean = hasModifierProperty("case")

  override protected def addFromCompanion(companion: ScTypeDefinition): Boolean = companion.isInstanceOf[ScObject]

  override def getConstructors: Array[PsiMethod] =
    constructor.toArray
      .flatMap(_.getFunctionWrappers) ++
      secondaryConstructors
        .flatMap(_.getFunctionWrappers(isStatic = false, isAbstract = false, Some(this)))

  private def implicitMethodText: String = {
    val constr = constructor.getOrElse(return "")
    val returnType = name + typeParametersClause.map(_ => typeParameters.map(_.name).
      mkString("[", ",", "]")).getOrElse("")
    val typeParametersText = typeParametersClause.map(tp => {
      tp.typeParameters.map(tp => {
        val baseText = tp.typeParameterText
        if (tp.isContravariant) {
          val i = baseText.indexOf('-')
          baseText.substring(i + 1)
        } else if (tp.isCovariant) {
          val i = baseText.indexOf('+')
          baseText.substring(i + 1)
        } else baseText
      }).mkString("[", ", ", "]")
    }).getOrElse("")
    val parametersText = constr.parameterList.clauses.map { clause =>
      clause.parameters.map { parameter =>
        val paramText = s"${parameter.name} : ${parameter.typeElement.map(_.getText).getOrElse("Nothing")}"
        parameter.getDefaultExpression match {
          case Some(expr) => s"$paramText = ${expr.getText}"
          case _          => paramText
        }
      }.mkString(if (clause.isImplicit) "(implicit " else "(", ", ", ")")
    }.mkString
    val accessModifier = getModifierList.accessModifier.map(am => AccessModifierRenderer.simpleTextHtmlEscaped(am) + " ").getOrElse("")
    s"${accessModifier}implicit def $name$typeParametersText$parametersText : $returnType = throw new Error()"
  }

  override def getSyntheticImplicitMethod: Option[ScFunction] =
    if (!hasModifierProperty("implicit") || constructor.isEmpty) None
    else
      cachedInUserData("getSyntheticImplicitMethod", this, ModTracker.libraryAware(this)) {
        try {
          val method = ScalaPsiElementFactory.createMethodWithContext(implicitMethodText, this.getContext, this)
          method.syntheticNavigationElement = this
          Some(method)
        } catch {
          case p: ProcessCanceledException         => throw p
          case _: ScalaPsiElementCreationException => None
        }
      }

  override def psiFields: Array[PsiField] = {
    val fields = constructor match {
      case Some(constr) => constr.parameters.map { param =>
        param.`type`() match {
          case Right(tp: TypeParameterType) if tp.psiTypeParameter.findAnnotation("scala.specialized") != null =>
            val lightField = ScLightField(param.getName, tp, this, PsiModifier.PUBLIC, PsiModifier.FINAL)
            Option(lightField)
          case _ => None
        }
      }
      case _ => Seq.empty
    }
    super.psiFields ++ fields.flatten
  }

  override def getTypeParameterList: PsiTypeParameterList = typeParametersClause.orNull

  override def getInterfaces: Array[PsiClass] = {
    getSupers.filter(_.isInterface)
  }

  override protected def keywordTokenType: IElementType = ScalaTokenType.ClassKeyword

  override def namedTag: Option[ScNamedElement] = Some(this)

  override protected def endParent: Option[PsiElement] = extendsBlock.templateBody

  override def isEffectivelyFinal: Boolean = this.hasFinalModifier
}
