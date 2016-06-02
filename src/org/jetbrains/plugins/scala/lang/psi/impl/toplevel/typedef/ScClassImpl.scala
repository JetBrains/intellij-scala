package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.DumbService
import com.intellij.psi._
import com.intellij.psi.impl.light.LightField
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameterType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

import scala.collection.mutable.ArrayBuffer

/**
 * @author Alexander.Podkhalyuzin
 */

class ScClassImpl private (stub: StubElement[ScTemplateDefinition], nodeType: IElementType, node: ASTNode)
  extends ScTypeDefinitionImpl(stub, nodeType, node) with ScClass with ScTypeParametersOwner with ScTemplateDefinition {
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

  def this(node: ASTNode) = {this(null, null, node)}

  def this(stub: ScTemplateDefinitionStub) = {
    this(stub, ScalaElementTypes.classDefinition, null)
  }

  override def toString: String = "ScClass: " + name

  override def getIconInner = Icons.CLASS

  override def constructor = Option(getStub) flatMap {
    _.getChildrenByType(ScalaElementTypes.PRIMARY_CONSTRUCTOR, JavaArrayFactoryUtil.ScPrimaryConstructorFactory).headOption
  } orElse {
    super.constructor
  }

  import com.intellij.psi.scope.PsiScopeProcessor
  import com.intellij.psi.{PsiElement, ResolveState}
  override def processDeclarationsForTemplateBody(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    if (DumbService.getInstance(getProject).isDumb) return true
    if (!super.processDeclarationsForTemplateBody(processor, state, lastParent, place)) return false
    super[ScTypeParametersOwner].processDeclarations(processor, state, lastParent, place)
  }

  override def getAllMethodsWithNames = {
    val methods = super.getAllMethodsWithNames
    val names = methods.map(_._2).toSet

    def createSyntheticMethod(text: String) = {
      val function = ScalaPsiElementFactory.createMethodWithContext(text, this, this)
      function.setSynthetic(this)
      function
    }

    val syntheticMethods = (isCase match {
      //for Scala this is done in ScalaOIUtil.isProductAbstractMethod, for Java we do it here
      case true => Seq(
        "def canEqual(that: Any): Boolean = ???",
        "def equals(that: Any): Boolean = ???",
        "def productArity: Int = ???",
        "def productElement(n: Int): Any = ???"
      )
      case _ => Seq.empty
    }) map {
      case text =>
        val function = createSyntheticMethod(text)
        (function, function.name)
    }

    val companionMethods = ScalaPsiUtil.getCompanionModule(this) match {
      case Some(o: ScObjectImpl) => o.getAllMethodsWithNames filter {
        case (function, name) => !names.contains(name)
      }
      case _ => Seq.empty
    }

    methods ++ syntheticMethods ++ companionMethods
  }

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
              case e: Exception =>
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
      x.parameterList.clauses.head.isImplicit) "()" else "") + x.parameterList.clauses.map{ c =>
      val start = if (c.isImplicit) "(implicit " else "("
      c.parameters.map{ p =>
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
    val returnType = name + typeParametersClause.map(clause => typeParameters.map(_.name).
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

  @Cached(synchronized = false, ModCount.getBlockModificationCount, this)
  def getSyntheticImplicitMethod: Option[ScFunction] = {
    if (hasModifierProperty("implicit")) {
      constructor match {
        case Some(x: ScPrimaryConstructor) =>
          try {
            val method = ScalaPsiElementFactory.createMethodWithContext(implicitMethodText, this.getContext, this)
            method.setSynthetic(this)
            Some(method)
          } catch {
            case e: Exception => None
          }
        case None => None
      }
    } else None
  }

  override def getFields: Array[PsiField] = {
    val fields = constructor match {
      case Some(constr) => constr.parameters.map { param =>
        param.getType(TypingContext.empty) match {
          case Success(tp: TypeParameterType, _) if tp.psiTypeParameter.findAnnotation("scala.specialized") != null =>
            val factory: PsiElementFactory = PsiElementFactory.SERVICE.getInstance(getProject)
            val psiTypeText: String = tp.toPsiType(getProject, getResolveScope).getCanonicalText
            val text = s"public final $psiTypeText ${param.name};"
            val elem = new LightField(getManager, factory.createFieldFromText(text, this), this)
            elem.setNavigationElement(param)
            Option(elem)
          case _ => None
        }
      }
      case _ => Seq.empty
    }
    super.getFields ++ fields.flatten
  }

  override def getTypeParameterList: PsiTypeParameterList = typeParametersClause.orNull
}