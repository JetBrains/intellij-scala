package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.psi._
import com.intellij.psi.util.{PsiTreeUtil, PsiModificationTracker}
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * @author Alexander.Podkhalyuzin
 */

class ScClassImpl extends ScTypeDefinitionImpl with ScClass with ScTypeParametersOwner with ScTemplateDefinition {
  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def additionalJavaNames: Array[String] = {
    fakeCompanionModule match {
      case Some(m) => Array(m.getName)
      case _ => Array.empty
    }
  }

  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTemplateDefinitionStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ScClass: " + name

  override def getIconInner = Icons.CLASS

  def constructor: Option[ScPrimaryConstructor] = {
    val stub = getStub
    if (stub != null) {
      val array =
        stub.getChildrenByType(ScalaElementTypes.PRIMARY_CONSTRUCTOR, JavaArrayFactoryUtil.ScPrimaryConstructorFactory)
      return array.headOption
    }
    findChild(classOf[ScPrimaryConstructor])
  }

  def parameters = constructor match {
    case Some(c) => c.effectiveParameterClauses.flatMap(_.unsafeClassParameters)
    case None => Seq.empty
  }

  override def members = constructor match {
    case Some(c) => super.members ++ Seq(c)
    case _ => super.members
  }

  import com.intellij.psi.scope.PsiScopeProcessor
  import com.intellij.psi.{PsiElement, ResolveState}
  override def processDeclarationsForTemplateBody(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    if (DumbServiceImpl.getInstance(getProject).isDumb) return true
    if (!super[ScTemplateDefinition].processDeclarationsForTemplateBody(processor, state, lastParent, place)) return false

    constructor match {
      case Some(constr) if place != null && PsiTreeUtil.isContextAncestor(constr, place, false) =>
        //ignore, should be processed in ScParameters
      case _ =>
        for (p <- parameters) {
          ProgressManager.checkCanceled()
          if (processor.isInstanceOf[BaseProcessor]) { // don't expose class parameters to Java.
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

  override def getMethods: Array[PsiMethod] = {
    getAllMethods.filter(_.containingClass == this)
  }

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
      case _ =>
    }
    res.toArray
  }

  override def getConstructors: Array[PsiMethod] = {
    val buffer = new ArrayBuffer[PsiMethod]
    buffer ++= functions.filter(_.isConstructor).flatMap(_.getFunctionWrappers(isStatic = false, isInterface = false, Some(this)))
    constructor match {
      case Some(x) => buffer ++= x.getFunctionWrappers
      case _ =>
    }
    buffer.toArray
  }

  override def syntheticMethodsNoOverride: scala.Seq[PsiMethod] = {
    CachesUtil.get(this, CachesUtil.SYNTHETIC_MEMBERS_KEY,
      new CachesUtil.MyProvider[ScClassImpl, Seq[PsiMethod]](this, clazz => clazz.innerSyntheticMembers)
        (PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT))
  }

  private def innerSyntheticMembers: Seq[PsiMethod] = {
    val res = new ArrayBuffer[PsiMethod]
    res ++= super.syntheticMethodsNoOverride
    res ++= syntheticMembersImpl
    res.toSeq
  }

  private def syntheticMembersImpl: Seq[PsiMethod] = {
    val buf = new ArrayBuffer[PsiMethod]
    if (isCase && !hasModifierProperty("abstract") && parameters.length > 0) {
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
    SyntheticMembersInjector.inject(this) ++: buf.toSeq
  }

  private def copyMethodText: String = {
    val x = constructor.getOrElse(return "")
    val paramString = (if (x.parameterList.clauses.length == 1 &&
      x.parameterList.clauses.apply(0).isImplicit) "()" else "") + x.parameterList.clauses.map{ c =>
      val start = if (c.isImplicit) "(implicit " else "("
      c.parameters.map{ p =>
        val paramType = p.typeElement match {
          case Some(te) => te.getText
          case None => "Any"
        }
        p.name + " : " + paramType + " = this." + p.name
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

  @volatile
  private var syntheticImplicitMethod: Option[ScFunction] = null
  @volatile
  private var syntheticImplicitMethodModificationCount: Long = 0

  def getSyntheticImplicitMethod: Option[ScFunction] = {
    val count = getManager.getModificationTracker.getOutOfCodeBlockModificationCount
    if (count == syntheticImplicitMethodModificationCount && syntheticImplicitMethod != null) {
      return syntheticImplicitMethod
    }
    val res: Option[ScFunction] =
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
    syntheticImplicitMethod = res
    syntheticImplicitMethodModificationCount = count
    res
  }

  override def getTypeParameterList: PsiTypeParameterList = typeParametersClause.orNull

  override def getInterfaces: Array[PsiClass] = {
    getSupers.filter(_.isInterface)
  }
}