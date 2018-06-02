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
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes.{CLASS_DEFINITION, PRIMARY_CONSTRUCTOR}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.SignatureNodes
import org.jetbrains.plugins.scala.lang.psi.light.LightUtil
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameterType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{PhysicalSignature, ScTypeExt}
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * @author Alexander.Podkhalyuzin
  */

class ScClassImpl protected (stub: ScTemplateDefinitionStub, node: ASTNode)
  extends ScTypeDefinitionImpl(stub, CLASS_DEFINITION, node) with ScClass
    with ScTypeParametersOwner with ScTemplateDefinition {

  def this(node: ASTNode) =
    this(null, node)

  def this(stub: ScTemplateDefinitionStub) =
    this(stub, null)

  override def toString: String = "ScClass: " + ifReadAllowed(name)("")

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => visitor.visitClass(this)
      case _ => super.accept(visitor)
    }
  }

  override def additionalJavaNames: Array[String] = {
    //do not add all cases with fakeCompanionModule, it will be used in Stubs.
    if (isCase) fakeCompanionModule.map(_.getName).toArray
    else Array.empty
  }

  override def constructor: Option[ScPrimaryConstructor] = desugaredElement match {
    case Some(templateDefinition: ScConstructorOwner) => templateDefinition.constructor
    case _ => this.stubOrPsiChild(PRIMARY_CONSTRUCTOR)
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

  override def psiMethods: Array[PsiMethod] =
    getAllMethods.filter(_.containingClass == this)

  override def getAllMethods: Array[PsiMethod] = {
    val res = new ArrayBuffer[PsiMethod]()
    val names = new mutable.HashSet[String]

    res ++= getConstructors

    TypeDefinitionMembers.SignatureNodes.forAllSignatureNodes(this) { node =>
      val isInterface = node.info.namedElement match {
        case t: ScTypedDefinition if t.isAbstractMember => true
        case _ => false
      }
      this.processPsiMethodsForNode(node, isStatic = false, isInterface = isInterface)(res += _, names += _)
    }

    for (synthetic <- syntheticMethodsNoOverride) {
      this.processPsiMethodsForNode(new SignatureNodes.Node(new PhysicalSignature(synthetic, ScSubstitutor.empty),
        ScSubstitutor.empty),
        isStatic = false, isInterface = isInterface)(res += _, names += _)
    }


    ScalaPsiUtil.getCompanionModule(this) match {
      case Some(o: ScObject) =>
        def add(method: PsiMethod) {
          if (!names.contains(method.getName)) {
            res += method
          }
        }

        TypeDefinitionMembers.SignatureNodes.forAllSignatureNodes(o) { node =>
          this.processPsiMethodsForNode(node, isStatic = true, isInterface = false)(add)
        }

        for (synthetic <- o.syntheticMethodsNoOverride) {
          this.processPsiMethodsForNode(new SignatureNodes.Node(new PhysicalSignature(synthetic, ScSubstitutor.empty),
            ScSubstitutor.empty),
            isStatic = true, isInterface = false)(res += _, names += _)
        }
      case _ =>
    }
    res.toArray
  }

  override def getConstructors: Array[PsiMethod] =
    constructor.toArray
      .flatMap(_.getFunctionWrappers) ++
      secondaryConstructors
        .flatMap(_.getFunctionWrappers(isStatic = false, isInterface = false, Some(this)))

  override protected def syntheticMethodsNoOverrideImpl: Seq[PsiMethod] = {
    val buf = new ArrayBuffer[PsiMethod]
    if (isCase && !hasModifierProperty("abstract") && parameters.nonEmpty) {
      constructor match {
        case Some(x: ScPrimaryConstructor) =>
          val hasCopy = !TypeDefinitionMembers.getSignatures(this).forName("copy")._1.isEmpty
          val addCopy = !hasCopy && !x.parameterList.clauses.exists(_.hasRepeatedParam)
          if (addCopy) {
            try {
              val method = ScalaPsiElementFactory.createMethodWithContext(copyMethodText, this, this)
              method.setSynthetic(this)
              buf += method
            } catch {
              case p: ProcessCanceledException => throw p
              case _: Exception =>
              //do not add methods if class has wrong signature.
            }
          }
        case None =>
      }
    }
    SyntheticMembersInjector.inject(this, withOverride = false) ++: buf
  }

  private def copyMethodText: String = {
    val x = constructor.getOrElse(return "")
    val className = name
    val paramString = (if (x.parameterList.clauses.length == 1 &&
      x.parameterList.clauses.head.isImplicit) "()" else "") + x.parameterList.clauses.map { c =>
      val start = if (c.isImplicit) "(implicit " else "("
      c.parameters.map { p =>
        val paramType = p.typeElement match {
          case Some(te) => te.getText
          case None => "Any"
        }
        s"${p.name} : $paramType = $className.this.${p.name}"
      }.mkString(start, ", ", ")")
    }.mkString("")

    val returnType = name + typeParameters.map(_.name).mkString("[", ",", "]")
    "def copy" + typeParamString + paramString + " : " + returnType + " = throw new Error(\"\")"
  }

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
    val parametersText = constr.parameterList.clauses.map {
      case clause: ScParameterClause => clause.parameters.map {
        case parameter: ScParameter =>
          val paramText = s"${parameter.name} : ${parameter.typeElement.map(_.getText).getOrElse("Nothing")}"
          parameter.getDefaultExpression match {
            case Some(expr) => s"$paramText = ${expr.getText}"
            case _ => paramText
          }
      }.mkString(if (clause.isImplicit) "(implicit " else "(", ", ", ")")
    }.mkString
    getModifierList.accessModifier.map(am => am.getText + " ").getOrElse("") + "implicit def " + name +
      typeParametersText + parametersText + " : " + returnType +
      " = throw new Error(\"\")"
  }

  @Cached(ModCount.getBlockModificationCount, this)
  def getSyntheticImplicitMethod: Option[ScFunction] = {
    if (hasModifierProperty("implicit")) {
      constructor match {
        case Some(_: ScPrimaryConstructor) =>
          try {
            val method = ScalaPsiElementFactory.createMethodWithContext(implicitMethodText, this.getContext, this)
            method.setSynthetic(this)
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
