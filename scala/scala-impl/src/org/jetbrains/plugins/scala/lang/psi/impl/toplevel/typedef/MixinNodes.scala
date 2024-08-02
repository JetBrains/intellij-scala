/**
 */
package org.jetbrains.plugins.scala.lang.psi
package impl
package toplevel
package typedef

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.{PsiClass, PsiClassType, PsiMethod, PsiNamedElement}
import com.intellij.util.containers.{ContainerUtil, SmartHashSet}
import com.intellij.util.{AstLoadingFilter, SmartList}
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.{Object2ObjectMap, Object2ObjectMaps, Object2ObjectOpenCustomHashMap}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.caches.{ModTracker, cachedInUserData, cachedWithRecursionGuard}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes.{MapImpl, SuperTypesData, extractClassOrUpperBoundClass}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{ExtractClass, ParameterizedType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}
import org.jetbrains.plugins.scala.util.{ScEquivalenceUtil, UnloadableThreadLocal}

import java.util
import java.util.concurrent.ConcurrentHashMap
import java.util.{HashMap => JMap, List => JList}
import scala.annotation.tailrec
import scala.collection.immutable.{ArraySeq, SeqMap}
import scala.collection.mutable
import scala.jdk.CollectionConverters._

abstract class MixinNodes[T <: Signature](signatureCollector: SignatureProcessor[T]) {
  type Map = MixinNodes.Map[T]

  def build(clazz: PsiClass, withSupers: Boolean, subst: ScSubstitutor = ScSubstitutor.empty): Map = {
    if (!clazz.isValid) MixinNodes.emptyMap[T]
    else {
      def build0: MapImpl[T] = {
        val map = new MapImpl[T]

        if (withSupers) {
          addSuperSignatures(SuperTypesData(clazz), map)
        }

        map.supersFinished()

        signatureCollector.processPsiClass(clazz, subst, map)
        map.sigsFinished()

        map
      }

      if (ScalaApplicationSettings.PRECISE_TEXT) { // SCL-21199
        build0
      } else {
        AstLoadingFilter.disallowTreeLoading(() => build0, () => "Tree access is disallowed in MixinNodes.build")
      }
    }
  }

  def build(andTpe: ScAndType): Map = {
    def collectChildrenNodes(tp: ScType): Seq[Map] = tp match {
      case ScAndType(lhs, rhs) => collectChildrenNodes(lhs) ++ collectChildrenNodes(rhs)
      case other               =>
        extractClassOrUpperBoundClass(other) match {
          case Some((cls, subst)) => Seq(build(cls, withSupers = true, subst = subst))
          case _                  => Seq.empty
        }
    }

    val maps = collectChildrenNodes(andTpe)
    maps.fold(MixinNodes.emptyMap)(_ intersect _)
  }

  def build(cp: ScCompoundType, compoundThisType: Option[ScType]): Map = {
    val map = new MapImpl[T]

    cp match {
      case comp: ScCompoundType => signatureCollector.processRefinement(comp, map)
      case _                    => ()
    }

    addSuperSignatures(SuperTypesData(cp, compoundThisType), map)
    map.supersFinished()

    signatureCollector.processRefinement(cp, map)
    map.sigsFinished()
    map
  }

  private def addSuperSignatures(superTypesData: SuperTypesData, map: Map): Unit = {

    for ((superClass, subst) <- superTypesData.substitutors) {
      signatureCollector.processPsiClass(superClass, subst, map)
    }

    for (compoundType <- superTypesData.refinements) {
      signatureCollector.processRefinement(compoundType, map)
    }
  }
}

object MixinNodes {
  val currentlyProcessedSigs: UnloadableThreadLocal[JMap[PsiClass, Map[TermSignature]]] =
    new UnloadableThreadLocal(new JMap)

  def withSignaturesFor[T](cls: PsiClass, sigs: Map[TermSignature])(f: =>T): T = try {
    currentlyProcessedSigs.value.put(cls, sigs)
    f
  } finally {
    currentlyProcessedSigs.value.remove(cls)
  }


  private case class SuperTypesData(substitutors: SeqMap[PsiClass, ScSubstitutor], refinements: Seq[ScCompoundType])

  private object SuperTypesData {

    def apply(thisClass: PsiClass): SuperTypesData =
      cachedInUserData("SuperTypesData.apply", thisClass, ModTracker.libraryAware(thisClass), Tuple1(thisClass)) {
        val superTypes = thisClass match {
          case syn: ScSyntheticClass          => syn.getSuperTypes.map(_.toScType()(syn)).toSeq
          case newTd: ScNewTemplateDefinition => MixinNodes.linearization(newTd)
          case _                              => MixinNodes.linearization(thisClass).drop(1)
        }
        val thisType = thisClass match {
          case td: ScTemplateDefinition => ScThisType(td)
          case _                        => null
        }
        SuperTypesData(superTypes, thisType)
      }

    def apply(cp: ScCompoundType, compoundThisType: Option[ScType]): SuperTypesData = {
      val superTypes = MixinNodes.linearization(cp)
      val thisType = compoundThisType.getOrElse(cp)
      SuperTypesData(superTypes, thisType)
    }

    private def apply(superTypes: Seq[ScType], @Nullable thisType: ScType): SuperTypesData = {
      val substitutorsBuilder = SeqMap.newBuilder[PsiClass, ScSubstitutor]
      val refinementsBuilder = List.newBuilder[ScCompoundType]

      for (superType <- superTypes) {
        superType.extractClassType match {
          case Some((superClass, s)) =>
            val seenFromClass = if (thisType == null) null else superClass
            val dependentSubst = superType match {
              case p @ ScProjectionType(proj, _)                       => ScSubstitutor(proj, seenFromClass).followed(p.actualSubst)
              case ParameterizedType(p @ ScProjectionType(proj, _), _) => ScSubstitutor(proj, seenFromClass).followed(p.actualSubst)
              case _                                                   => ScSubstitutor.empty
            }
            val thisTypeSubst = if (thisType == null) ScSubstitutor.empty else ScSubstitutor(thisType, seenFromClass)
            val newSubst = combine(s, superClass).followed(thisTypeSubst).followed(dependentSubst)
            substitutorsBuilder += superClass -> newSubst
          case _ =>
            dealias(superType) match {
              case cp: ScCompoundType =>
                refinementsBuilder += cp
              case _ =>
            }
        }
      }
      SuperTypesData(substitutorsBuilder.result(), refinementsBuilder.result())
    }

    private def combine(superSubst: ScSubstitutor, superClass: PsiClass): ScSubstitutor = {
      val typeParameters = superClass.getTypeParameters
      val substedTpts = typeParameters.map(tp => superSubst(TypeParameterType(tp)))
      ScSubstitutor.bind(typeParameters, substedTpts)
    }
  }

  def allSuperClasses(clazz: PsiClass): Set[PsiClass] =
    SuperTypesData(clazz).substitutors.keys.toSet

  def asSeenFromSubstitutor(superClass: PsiClass, thisClass: PsiClass): ScSubstitutor =
    SuperTypesData(thisClass).substitutors.getOrElse(superClass, ScSubstitutor.empty)

  class Node[T](val info: T, val fromSuper: Boolean) {
    private[this] var _concreteSuper: Node[T] = _
    private[this] var _supers: Seq[Node[T]] = Vector.empty

    private[MixinNodes] def addSuper(node: Node[T]): Unit = _supers :+= node

    private[MixinNodes] def setConcreteSuper(n: Node[T]): Unit = {
      if (_concreteSuper == null) {
        _concreteSuper = n
      }
    }

    private[MixinNodes] def concreteSuper: Option[Node[T]] = Option(_concreteSuper)

    def supers: Seq[Node[T]] = _supers
    def primarySuper: Option[Node[T]] = concreteSuper.orElse(supers.headOption)
  }

  trait Map[T <: Signature] extends SignatureSink[T] {
    def allNodes: Iterator[Node[T]] = allNames.iterator().asScala.map(forName).flatMap(_.nodesIterator)

    lazy val implicitNodes: Seq[Node[T]] = {
      val builder = ArraySeq.newBuilder[Node[T]]
      builder.sizeHint(implicitNames.size)
      val iterator = implicitNames.iterator()
      while (iterator.hasNext) {
        val thisMap = forName(iterator.next)
        thisMap.nodesIterator.foreach { node =>
          if (node.info.isImplicit || node.info.isExtensionMethod) {
            builder += node
          }
        }
      }
      builder.result()
    }

    def nodesIterator(
      decodedName:  String,
      isSupers:     Boolean,
      onlyImplicit: Boolean = false
    ): Iterator[Node[T]] = {

      val allIterator =
        if (decodedName != "") forName(decodedName).nodesIterator
        else if (onlyImplicit) implicitNodes.iterator
        else                   allNodes

      if (isSupers) allIterator.flatMap(node => if (node.fromSuper) Iterator(node) else node.primarySuper.iterator)
      else          allIterator
    }

    def allSignatures: Iterator[T]       = allNodes.map(_.info)
    def intersect(other: Map[T]): Map[T] = new IntersectionMap(this,  other)

    protected val forNameCache = new ConcurrentHashMap[String, AllNodes[T]]()

    def forName(name: String): AllNodes[T]
    def allNames: util.HashSet[String]
    def implicitNames: SmartHashSet[String]
  }

  class MapImpl[T <: Signature] extends Map[T] {
    override val allNames: util.HashSet[String] = new util.HashSet[String]
    override val implicitNames: SmartHashSet[String] = new SmartHashSet[String]

    private val thisSignaturesByName: JMap[String, JList[T]] = new JMap()
    private val supersSignaturesByName: JMap[String, JList[T]] = new JMap()

    private var fromSuper: Boolean                  = true
    private var finishedBuildingSignatures: Boolean = false

    def supersFinished(): Unit = fromSuper                = false
    def sigsFinished(): Unit = finishedBuildingSignatures = true

    override def put(signature: T): Unit = {
      val name = signature.name
      val buffer =
        if (fromSuper) supersSignaturesByName.computeIfAbsent(name, _ => new SmartList[T])
        else           thisSignaturesByName.computeIfAbsent(name, _ => new SmartList[T])

      buffer.add(signature)

      allNames.add(name)

      if (signature.isImplicit || signature.isExtensionMethod)
        implicitNames.add(name)
    }

    override def forName(name: String): AllNodes[T] = {
      val cleanName = ScalaNamesUtil.clean(name)
      def calculate: AllNodes[T] = {
        val thisSignatures  = thisSignaturesByName.getOrDefault(cleanName, ContainerUtil.emptyList[T])
        val superSignatures = supersSignaturesByName.getOrDefault(cleanName, ContainerUtil.emptyList[T])
        merge(thisSignatures, superSignatures)
      }

      if (finishedBuildingSignatures) {
        //do not cache intermediate results, forName may be called from resolve
        //for exports, while signatures are still being built
        forNameCache.atomicGetOrElseUpdate(cleanName, calculate)
      } else calculate
    }

    private def merge(thisSignatures: JList[T], superSignatures: JList[T]): AllNodes[T] = {
      val nodesMap = NodesMap.empty[T]
      val privates = PrivateNodes.empty[T]

      thisSignatures.forEach { thisSig =>

        val node = new Node(thisSig, fromSuper = false)

        if (thisSig.isPrivate) {
          privates.add(node)
        }
        else {
          nodesMap.putIfAbsent(thisSig, node) match {
            case null => // all as expected, unique signature inserted
            case old =>
              if (thisSig.isSynthetic && !old.info.isAbstract) {
                // reinsert real node back instead of synthetic
                nodesMap.put(thisSig, old)
              }
          }
        }
      }

      superSignatures.forEach { superSig =>
        val superNode = new Node(superSig, fromSuper = true)
        if (superSig.isPrivate) {
          privates.add(superNode)
        }
        else {
          nodesMap.putIfAbsent(superSig, superNode) match {
            case null => // not seen before
            case old if !superNode.info.isAbstract && (old.info.isSynthetic || old.info.isAbstract) =>
              //force update thisMap with a non-abstract and non-synthetic node
              nodesMap.put(superSig, superNode)

              //and copy already collected nodes to it
              old.supers.foreach(superNode.addSuper)
              old.concreteSuper.foreach(superNode.setConcreteSuper)

            case old =>
              old.addSuper(superNode)
              if (!superNode.info.isAbstract) {
                old.setConcreteSuper(superNode)
              }
          }
        }
      }

      new AllNodes(Object2ObjectMaps.synchronize(nodesMap), privates)
    }
  }

  class IntersectionMap[T <: Signature](lhsMap: Map[T], rhsMap: Map[T]) extends Map[T] {
    override val allNames: util.HashSet[String] =
      new util.HashSet[String]() {
        addAll(lhsMap.allNames)
        addAll(rhsMap.allNames)
      }

    override val implicitNames: SmartHashSet[String] = {
      val names = new SmartHashSet[String]()
      names.addAll(lhsMap.implicitNames)
      names.addAll(rhsMap.implicitNames)
      names
    }

    override def forName(name: String): AllNodes[T] = {
      val cleanName = ScalaNamesUtil.clean(name)
      val fromLhs   = lhsMap.forName(name)
      val fromRhs   = rhsMap.forName(name)
      forNameCache.atomicGetOrElseUpdate(cleanName, fromLhs.merge(fromRhs))
    }

    override def put(signature: T): Unit = ()
  }

  def emptyMap[T <: Signature]: MixinNodes.Map[T] = new MixinNodes.MapImpl[T]

  class AllNodes[T <: Signature](private val publics: NodesMap[T], private val privates: PrivateNodes[T]) {

    def merge(other: AllNodes[T]): AllNodes[T] = {
      val newPublics  = NodesMap.empty[T]
      val newPrivates = PrivateNodes.empty[T]

      def returnType(e: PsiNamedElement): ScType = e match {
        case fn: ScFunction         => fn.returnType.getOrAny
        case m: PsiMethod           => m.getReturnType.toScType()(e.projectContext)
        case tpd: ScTypedDefinition => tpd.`type`().getOrAny
        case other                  => throw new IllegalArgumentException(s"Unexpected signature element of class ${other.getClass}")
      }

      def addNode(node: Node[T]): Unit = {
        node.info match {
          case sig if sig.isPrivate => newPrivates.add(node)
          case _: TypeSignature     => newPublics.put(node.info, node) // todo: merge alias bounds
          case sig: TermSignature   =>
            newPublics.merge(node.info, node, (oldNode, _) => {
              val oldSig     = oldNode.info.asInstanceOf[TermSignature]
              val oldElement = oldSig.namedElement

              oldElement match {
                case e @ (_: PsiMethod | _: ScBindingPattern | _: ScFieldId) =>
                  //intersect return types of same-signature members
                  val sigReturnType = sig.intersectedReturnType.getOrElse(returnType(sig.namedElement))
                  val oldReturnType = oldSig.intersectedReturnType.getOrElse(returnType(e))

                  val combinedSubst         = oldSig.substitutor.followed(sig.substitutor)
                  val intersectedReturnType = ScAndType(oldReturnType, sigReturnType)

                  val intersectedSig =
                    sig.copy(substitutor = combinedSubst, intersectedReturnType = intersectedReturnType.toOption)

                  new Node(intersectedSig, fromSuper = true).asInstanceOf[Node[T]]
                case _ => oldNode
              }
            })
        }
      }

      nodesIterator.foreach(addNode)
      other.nodesIterator.foreach(addNode)

      new AllNodes(Object2ObjectMaps.synchronize(newPublics), newPrivates)
    }

    def get(s: T): Option[Node[T]] = {
      publics.get(s) match {
        case null => privates.get(s)
        case node => Some(node)
      }
    }

    def nodesIterator: Iterator[Node[T]] = new Iterator[Node[T]] {
      private val iter1 = publics.values.iterator
      private val iter2 = privates.nodesIterator

      override def hasNext: Boolean = iter1.hasNext || iter2.hasNext

      override def next(): Node[T] = if (iter1.hasNext) iter1.next() else iter2.next()
    }

    def iterator: Iterator[T] = nodesIterator.map(_.info)

    def findNode(named: PsiNamedElement): Option[Node[T]] = {
      var publicNode: Node[T] = null
      publics.forEach { (k, v) =>
        if (publicNode == null && named == k.namedElement) {
          publicNode = v
        }
      }

      Option(publicNode).orElse {
        privates.asScala.find(node => node.info.namedElement == named)
      }
    }

    def isEmpty: Boolean = publics.isEmpty && privates.isEmpty
  }

  //each set contains private members of some class with a fixed name
  //most of them are of size 0 and 1
  type PrivateNodes[T <: Signature] = SmartList[Node[T]]

  object PrivateNodes {
    def empty[T <: Signature]: PrivateNodes[T] = new SmartList[Node[T]]
  }

  implicit class PrivateNodesOps[T <: Signature](list: PrivateNodes[T]) {
    def get(s: T): Option[Node[T]] = {
      val iterator = list.iterator
      while (iterator.hasNext) {
        val next = iterator.next()
        if (s.namedElement == next.info.namedElement) return Some(next)
      }
      None
    }

    def nodesIterator: Iterator[Node[T]] = list.iterator.asScala
  }

  type NodesMap[T <: Signature] = Object2ObjectMap[T, Node[T]]

  object NodesMap {
    private def hashingStrategy[T <: Signature]: Hash.Strategy[T] =
      new Hash.Strategy[T] {
        override def hashCode(t: T): Int = t.equivHashCode
        override def equals(t: T, t1: T): Boolean = {
          if (t == null || t1 == null) false
          else t.equiv(t1)
        }
      }

    def empty[T <: Signature]: NodesMap[T] = new Object2ObjectOpenCustomHashMap[T, Node[T]](2, hashingStrategy[T])
  }

  def linearization(clazz: PsiClass): Seq[ScType] =
    cachedWithRecursionGuard("linearization", clazz, Seq.empty[ScType], ModTracker.libraryAware(clazz)) {
      clazz match {
        case obj: ScObject if obj.isPackageObject && obj.qualifiedName == "scala" =>
          Seq(ScalaType.designator(obj))
        case newTd: ScNewTemplateDefinition =>
          generalLinearization(None, newTd.superTypes)
        case _ =>
          ProgressManager.checkCanceled()
          def default =
            if (clazz.getTypeParameters.isEmpty)
              ScalaType.designator(clazz)
            else
              ScParameterizedType(ScalaType.designator(clazz), clazz.getTypeParameters.map(TypeParameterType(_)).toSeq)

          val classType = clazz match {
            case td: ScTypeDefinition => td.`type`().getOrElse(default)
            case _                    => default
          }
          val supers: Seq[ScType] = {
            implicit val ctx: ProjectContext = clazz
            clazz match {
              case td: ScTemplateDefinition => td.superTypes
              case clazz: PsiClass => clazz.getSuperTypes.map {
                case ctp: PsiClassType =>
                  //noinspection ScalaRedundantCast
                  val cl = ctp.resolve().asInstanceOf[PsiClass]
                  if (cl != null && cl.qualifiedName == "java.lang.Object") ScDesignatorType(cl)
                  else ctp.toScType()
                case ctp => ctp.toScType()
              }.toSeq
            }
          }

          generalLinearization(Some(classType), supers)
      }
    }

  def linearization(compound: ScCompoundType, addTp: Boolean = false): Seq[ScType] = {
    val comps     = compound.components
    val classType = Option.when(addTp)(compound)
    generalLinearization(classType, comps)
  }

  private def generalLinearization(classType: Option[ScType], supers: Iterable[ScType]): Seq[ScType] = {
    val baseTypes      = mutable.ArrayBuffer.empty[ScType]
    val qualifiedNames = mutable.HashSet.empty[String]

    def classString(clazz: PsiClass): String =
      clazz match {
        case obj: ScObject => "Object: " + obj.qualifiedName
        case tra: ScTrait  => "Trait: " + tra.qualifiedName
        case _             => "Class: " + clazz.qualifiedName
      }

    def add(tp: ScType): Unit = {
      extractClassOrUpperBoundClass(tp) match {
        case Some((clazz, _)) if clazz.qualifiedName != null && !qualifiedNames.contains(classString(clazz)) =>
          tp +=: baseTypes
          qualifiedNames += classString(clazz)
        case Some((clazz, _)) if clazz.getTypeParameters.nonEmpty =>
          val idx = baseTypes.indexWhere {
            case ExtractClass(cls) if ScEquivalenceUtil.areClassesEquivalent(cls, clazz) => true
            case _                                                                       => false
          }

          if (idx != -1) {
            val newTp = baseTypes(idx)
            if (tp.conforms(newTp)) baseTypes.update(idx, tp)
          }
        case _ =>
          dealias(tp) match {
            case c: ScCompoundType => c +=: baseTypes
            case _                 =>
          }
      }
    }

    val iterator = supers.iterator
    while (iterator.hasNext) {
      var tp = iterator.next()

      @tailrec
      def updateTp(tp: ScType, depth: Int = 0): ScType = {
        tp match {
          case AliasType(_, _, Right(upper)) =>
            if (tp != upper && depth < 100) updateTp(upper, depth + 1)
            else                            tp
          case _ =>
            tp match {
              case ex: ScExistentialType  => ex.quantified
              case tpt: TypeParameterType => tpt.upperType
              case _                      => tp
            }
        }
      }

      tp = updateTp(tp)
      extractClassOrUpperBoundClass(tp) match {
        case Some((clazz, subst)) =>
          val lin         = linearization(clazz)
          val newIterator = lin.reverseIterator

          while (newIterator.hasNext) {
            val tp = newIterator.next()
            add(subst(tp))
          }
        case _ =>
          dealias(tp) match {
            case c: ScCompoundType =>
              val lin         = linearization(c, addTp = true)
              val newIterator = lin.reverseIterator

              while (newIterator.hasNext) {
                val tp = newIterator.next()
                add(tp)
              }

            case _ =>
          }
      }
    }
    classType.foreach(add)
    baseTypes.to(ArraySeq)
  }

  private def dealias(tp: ScType) = tp match {
    case AliasType(_: ScTypeAliasDefinition, lower, _) => lower.getOrElse(tp)
    case _                                             => tp
  }

  private def extractClassOrUpperBoundClass(tp: ScType): Option[(PsiClass, ScSubstitutor)] =
    tp match {
      case TypeParameterType(tparam) => tparam.upperType.extractClassType
      case ParameterizedType(TypeParameterType(tparam), targs) =>
        val upperBound = tparam.upperType
        upperBound match {
          case ScTypePolymorphicType(internal, tps) =>
            val subst = ScSubstitutor.bind(tps, targs)
            subst(internal).extractClassType
          case t => ParameterizedType(t, targs).extractClassType
        }
      case _ => tp.extractClassType
    }
}
