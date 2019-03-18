package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressManager}
import com.intellij.openapi.project.DumbService
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.PresentationUtil.accessModifierText
import org.jetbrains.plugins.scala.lang.psi.annotator.ScClassAnnotator
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.light.LightUtil
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameterType
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * @author Alexander.Podkhalyuzin
  */
class ScClassImpl(stub: ScTemplateDefinitionStub[ScClass],
                  nodeType: ScTemplateDefinitionElementType[ScClass],
                  node: ASTNode)
  extends ScTypeDefinitionImpl(stub, nodeType, node)
    with ScClass with ScTypeParametersOwner with ScTemplateDefinition with ScClassAnnotator {

  override def toString: String = "ScClass: " + ifReadAllowed(name)("")

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = visitor.visitClass(this)

  //do not use fakeCompanionModule, it will be used in Stubs.
  override def additionalClassJavaName: Option[String] =
    if (isCase) Some(getName() + "$") else None

  override def constructor: Option[ScPrimaryConstructor] = desugaredElement match {
    case Some(templateDefinition: ScConstructorOwner) => templateDefinition.constructor
    case _ => this.stubOrPsiChild(ScalaElementType.PRIMARY_CONSTRUCTOR)
  }

  import com.intellij.psi.scope.PsiScopeProcessor
  import com.intellij.psi.{PsiElement, ResolveState}

  override def processDeclarationsForTemplateBody(processor: PsiScopeProcessor,
                                                  state: ResolveState,
                                                  lastParent: PsiElement,
                                                  place: PsiElement): Boolean = {
    if (DumbService.getInstance(getProject).isDumb) return true

    desugaredElement match {
      case Some(td) => return td.processDeclarationsForTemplateBody(processor, state, getLastChild, place)
      case _ =>
    }

    if (!super[ScTemplateDefinition].processDeclarationsForTemplateBody(processor, state, lastParent, place)) return false

    constructor match {
      case Some(constr) if place != null && PsiTreeUtil.isContextAncestor(constr, place, false) =>
      //ignore, should be processed in ScParameters
      case _ =>
        for (p <- parameters) {
          ProgressManager.checkCanceled()
          if (processor.isInstanceOf[BaseProcessor]) {
            // don't expose class parameters to Java.
            if (!processor.execute(p, state)) return false
          }
        }
    }

    super[ScTypeParametersOwner].processDeclarations(processor, state, lastParent, place)
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    super[ScTemplateDefinition].processDeclarations(processor, state, lastParent, place)
  }

  override def isCase: Boolean = hasModifierProperty("case")

  override def getAllMethods: Array[PsiMethod] = {
    val res = new ArrayBuffer[PsiMethod]()
    val names = new mutable.HashSet[String]

    res ++= getConstructors

    TypeDefinitionMembers.getSignatures(this).foreachSignature { signature =>
      val isInterface = signature.namedElement match {
        case t: ScTypedDefinition if t.isAbstractMember => true
        case _ => false
      }
      this.processWrappersForSignature(signature, isStatic = false, isInterface = isInterface)(res += _, names += _)
    }

    ScalaPsiUtil.getCompanionModule(this) match {
      case Some(o: ScObject) =>
        def add(method: PsiMethod) {
          if (!names.contains(method.getName)) {
            res += method
          }
        }

        TypeDefinitionMembers.getSignatures(o).foreachSignature (
          this.processWrappersForSignature(_, isStatic = true, isInterface = false)(add)
        )
      case _ =>
    }
    res.toArray
  }

  override def getConstructors: Array[PsiMethod] =
    constructor.toArray
      .flatMap(_.getFunctionWrappers) ++
      secondaryConstructors
        .flatMap(_.getFunctionWrappers(isStatic = false, isInterface = false, Some(this)))

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
    val accessModifier = getModifierList.accessModifier.map(am => accessModifierText(am) + " ").getOrElse("")
    s"${accessModifier}implicit def $name$typeParametersText$parametersText : $returnType = throw new Error()"
  }

  @Cached(ModCount.getBlockModificationCount, this)
  def getSyntheticImplicitMethod: Option[ScFunction] = {
    if (hasModifierProperty("implicit")) {
      constructor match {
        case Some(_: ScPrimaryConstructor) =>
          try {
            val method = ScalaPsiElementFactory.createMethodWithContext(implicitMethodText, this.getContext, this)
            method.syntheticNavigationElement = this
            Some(method)
          } catch {
            case p: ProcessCanceledException => throw p
            case _: Exception => None
          }
        case None => None
      }
    } else None
  }

  override def psiFields: Array[PsiField] = {
    val fields = constructor match {
      case Some(constr) => constr.parameters.map { param =>
        param.`type`() match {
          case Right(tp: TypeParameterType) if tp.psiTypeParameter.findAnnotation("scala.specialized") != null =>
            val psiTypeText: String = tp.toPsiType.getCanonicalText
            val lightField = LightUtil.createLightField(s"public final $psiTypeText ${param.name};", this)
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
}
