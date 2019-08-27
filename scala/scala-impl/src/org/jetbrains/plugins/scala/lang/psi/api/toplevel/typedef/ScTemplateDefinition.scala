package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Key
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.isLineTerminator
import org.jetbrains.plugins.scala.lang.psi.adapters.PsiClassAdapter
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, CachedInUserData, ModCount}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.mutable

/**
 * @author ven
 */
trait ScTemplateDefinition extends ScNamedElement with PsiClassAdapter with Typeable {

  import ScTemplateDefinition._

  def qualifiedName: String = null

  def originalElement: Option[ScTemplateDefinition] = Option(getUserData(originalElemKey))
  def setDesugared(actualElement: ScTypeDefinition): ScTemplateDefinition = {
    putUserData(originalElemKey, actualElement)
    members.foreach { member =>
      member.syntheticNavigationElement = actualElement
      member.syntheticContainingClass = actualElement
    }
    this
  }
  // designates that this very element has been created as a result of macro transform
  // do not confuse with desugaredElement
  def isDesugared: Boolean = originalElement.isDefined

  def desugaredElement: Option[ScTemplateDefinition] = None

  @Cached(ModCount.anyScalaPsiModificationCount, this)
  def physicalExtendsBlock: ScExtendsBlock = this.stubOrPsiChild(ScalaElementType.EXTENDS_BLOCK).orNull

  def extendsBlock: ScExtendsBlock = desugaredElement.map(_.extendsBlock).getOrElse(physicalExtendsBlock)

  def innerExtendsListTypes: Array[PsiClassType] = {
    val eb = extendsBlock
    if (eb != null) {
      val tp = eb.templateParents

      implicit val elementScope: ElementScope = ElementScope(getProject)
      tp match {
        case Some(tp1) => (for (te <- tp1.allTypeElements;
                                t = te.`type`().getOrAny;
                                asPsi = t.toPsiType
                                if asPsi.isInstanceOf[PsiClassType]) yield asPsi.asInstanceOf[PsiClassType]).toArray[PsiClassType]
        case _ => PsiClassType.EMPTY_ARRAY
      }
    } else PsiClassType.EMPTY_ARRAY
  }

  def showAsInheritor: Boolean = extendsBlock.templateBody.isDefined

  def getTypeWithProjections(thisProjections: Boolean = false): TypeResult

  def functions: Seq[ScFunction] = extendsBlock.functions

  def aliases: Seq[ScTypeAlias] = extendsBlock.aliases

  def members: Seq[ScMember] = extendsBlock.members

  @CachedInUserData(this, ModCount.getBlockModificationCount)
  def syntheticMethods: Seq[ScFunction] = syntheticMethodsImpl

  protected def syntheticMethodsImpl: Seq[ScFunction] = Seq.empty

  def typeDefinitions: Seq[ScTypeDefinition] = extendsBlock.typeDefinitions

  @CachedInUserData(this, ModCount.getBlockModificationCount)
  def syntheticTypeDefinitions: Seq[ScTypeDefinition] = syntheticTypeDefinitionsImpl

  protected def syntheticTypeDefinitionsImpl: Seq[ScTypeDefinition] = Seq.empty

  @CachedInUserData(this, ModCount.getBlockModificationCount)
  def syntheticMembers: Seq[ScMember] = syntheticMembersImpl

  protected def syntheticMembersImpl: Seq[ScMember] = Seq.empty

  def selfTypeElement: Option[ScSelfTypeElement] = {
    val qual = qualifiedName
    if (qual != null && (qual == "scala.Predef" || qual == "scala")) return None
    extendsBlock.selfTypeElement
  }

  def selfType: Option[ScType] = extendsBlock.selfType

  def superTypes: List[ScType] = extendsBlock.superTypes
  def supers: Seq[PsiClass] = extendsBlock.supers

  def allTypeSignatures: Iterator[TypeSignature] =
    TypeDefinitionMembers.getTypes(this).allSignatures

  def allTypeSignaturesIncludingSelfType: Iterator[TypeSignature] = {
    selfType match {
      case Some(selfType) =>
        val clazzType = getTypeWithProjections().getOrAny
        selfType.glb(clazzType) match {
          case c: ScCompoundType =>
            TypeDefinitionMembers.getTypes(c, Some(clazzType)).allSignatures
          case _ =>
            allTypeSignatures
        }
      case _ =>
        allTypeSignatures
    }
  }

  private def isValSignature(s: TermSignature): Boolean = s match {
    case _: PhysicalMethodSignature => false
    case _ => s.namedElement.nameContext match {
      case _: ScValueOrVariable | _: ScClassParameter => s.namedElement.name == s.name
      case _: PsiField => true
      case _ => false
    }
  }

  def allVals: Iterator[TermSignature] = {
    TypeDefinitionMembers.getSignatures(this).allSignatures.filter(isValSignature)
  }

  def allValsIncludingSelfType: Iterator[TermSignature] = {
    selfType match {
      case Some(selfType) =>
        val clazzType = getTypeWithProjections().getOrAny
        selfType.glb(clazzType) match {
          case c: ScCompoundType =>
            TypeDefinitionMembers.getSignatures(c, Some(clazzType))
              .allSignatures
              .filter(isValSignature)
          case _ =>
            allVals
        }
      case _ =>
        allVals
    }
  }

  def allMethods: Iterator[PhysicalMethodSignature] =
    TypeDefinitionMembers.getSignatures(this)
      .allSignatures
      .collect {
        case p: PhysicalMethodSignature => p
      }

  def allMethodsIncludingSelfType: Iterator[PhysicalMethodSignature] = {
    selfType match {
      case Some(selfType) =>
        val clazzType = getTypeWithProjections().getOrAny
        selfType.glb(clazzType) match {
          case c: ScCompoundType =>
            TypeDefinitionMembers.getSignatures(c, Some(clazzType))
              .allSignatures
              .collect {
                case p: PhysicalMethodSignature => p
              }
          case _ =>
            allMethods
        }
      case _ =>
        allMethods
    }
  }

  def allSignatures: Iterator[TermSignature] =
    TypeDefinitionMembers.getSignatures(this).allSignatures

  def allSignaturesIncludingSelfType: Iterator[TermSignature] = {
    selfType match {
      case Some(selfType) =>
        val clazzType = getTypeWithProjections().getOrAny
        selfType.glb(clazzType) match {
          case c: ScCompoundType =>
            TypeDefinitionMembers.getSignatures(c, Some(clazzType)).allSignatures
          case _ =>
            allSignatures
        }
      case _ =>
       allSignatures
    }
  }

  def isScriptFileClass: Boolean = getContainingFile match {
    case file: ScalaFile => file.isScriptFile
    case _ => false
  }

  def addMember(member: ScMember, anchor: Option[PsiElement]): ScMember = {
    implicit val projectContext: ProjectContext = member.projectContext
    extendsBlock.templateBody.map {
      _.getNode
    }.map { node =>
      val beforeNode = anchor.map {
        _.getNode
      }.getOrElse {
        val last = node.getLastChildNode
        last.getTreePrev match {
          case result if isLineTerminator(result.getPsi) => result
          case _ => last
        }
      }

      val before = beforeNode.getPsi
      if (isLineTerminator(before))
        node.addChild(createNewLineNode(), beforeNode)
      node.addChild(member.getNode, beforeNode)

      val newLineNode = createNewLineNode()
      if (isLineTerminator(before)) {
        node.replaceChild(beforeNode, newLineNode)
      } else {
        node.addChild(newLineNode, beforeNode)
      }

      member
    }.getOrElse {
      val node = extendsBlock.getNode
      node.addChild(createWhitespace.getNode)
      node.addChild(createBodyFromMember(member.getText).getNode)
      members.head
    }
  }

  def deleteMember(member: ScMember) {
    member.getParent.getNode.removeChild(member.getNode)
  }

  def allFunctionsByName(name: String): Iterator[PsiMethod] = {
    TypeDefinitionMembers.getSignatures(this).forName(name)
      .iterator
      .collect {
        case p: PhysicalMethodSignature => p.method
      }
  }

  override def isInheritor(baseClass: PsiClass, deep: Boolean): Boolean =
    Path(baseClass) match {
      case Path.JavaObject => true // These doesn't appear in the superTypes at the moment, so special case required.
      case Path(_, _, kind) if kind.isFinal => false
      case _ if DumbService.getInstance(getProject).isDumb => false
      case path => (if (deep) superPathsDeep else superPaths).contains(path)
    }

  @Cached(ModCount.getModificationCount, this)
  private def superPaths: Set[Path] =
    supers.map(Path.apply).toSet

  @Cached(ModCount.getModificationCount, this)
  private def superPathsDeep: Set[Path] = {
    val collected = mutable.Set.empty[Path]

    def dfs(clazz: PsiClass): Unit = {
      val path = Path(clazz)

      if (collected.add(path)) {
        clazz match {
          case definition: ScTemplateDefinition =>
            val supersIterator = definition.supers.iterator
            while (supersIterator.hasNext) {
              dfs(supersIterator.next())
            }
          case _ =>
            val supersIterator = clazz.getSuperTypes.iterator
            while (supersIterator.hasNext) {
              supersIterator.next().resolveGenerics.getElement match {
                case null =>
                case next => dfs(next)
              }
            }
        }
      }
    }

    dfs(this)

    collected.remove(Path(this))
    collected.toSet
  }
}

object ScTemplateDefinition {
  object ExtendsBlock {
    def unapply(definition: ScTemplateDefinition): Some[ScExtendsBlock] = Some(definition.extendsBlock)
  }

  sealed abstract class Kind(val isFinal: Boolean)

  object Kind {
    object Class extends Kind(false)
    object Trait extends Kind(false)
    object Object extends Kind(true)
    object NewTd extends Kind(true)
    object SyntheticFinal extends Kind(true)
    object NonScala extends Kind(false)
  }

  case class Path(name: String, qualifiedName: Option[String], kind: Kind)

  object Path {

    val JavaObject = Path(
      CommonClassNames.JAVA_LANG_OBJECT_SHORT,
      Some(CommonClassNames.JAVA_LANG_OBJECT),
      Kind.NonScala
    )

    def apply(clazz: PsiClass): Path = {
      import Kind._
      val kind = clazz match {
        case _: ScTrait => Trait
        case _: ScClass => Class
        case _: ScObject => Object
        case _: ScNewTemplateDefinition => NewTd
        case synthetic: ScSyntheticClass =>
          synthetic.className match {
            case "AnyRef" | "AnyVal" => Class
            case _ => SyntheticFinal
          }
        case _ => NonScala
      }

      Path(clazz.name, Option(clazz.qualifiedName), kind)
    }
  }

  private val originalElemKey: Key[ScTemplateDefinition] = Key.create("ScTemplateDefinition.originalElem")

  implicit class SyntheticMembersExt(private val td: ScTemplateDefinition) extends AnyVal {
    //this method is not in the ScTemplateDefinition trait to avoid binary incompatible change
    def membersWithSynthetic: Seq[ScMember] =
      td.members ++ td.syntheticMembers ++ td.syntheticMethods ++ td.syntheticTypeDefinitions

  }

}
