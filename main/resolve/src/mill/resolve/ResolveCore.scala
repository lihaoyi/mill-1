package mill.resolve

import mill.define._
import mill.util.EitherOps

import java.lang.reflect.InvocationTargetException
import scala.collection.immutable.Seq
import scala.reflect.NameTransformer.decode

/**
 * Takes a single list of segments, without braces but including wildcards, and
 * resolves all possible modules, targets or commands that the segments could
 * resolve to.
 *
 * Returns only the [[Segments]] of the things it resolved, without reflecting
 * on the `java.lang.reflect.Member`s or instantiating the final tasks. Those
 * are left to downstream callers to do, with the exception of instantiating
 * [[mill.define.Cross]] modules which is needed to identify their cross
 * values which is necessary for resolving tasks within them.
 *
 * Returns a [[Result]], either containing a [[Success]] containing the
 * [[Resolved]] set, [[NotFound]] if it couldn't find anything with some
 * metadata about what it was looking for, or [[Error]] if something blew up.
 */
private object ResolveCore {
  sealed trait Resolved {
    def segments: Segments
  }

  object Resolved {
    case class Module(segments: Segments, cls: Class[_]) extends Resolved
    case class Target(segments: Segments) extends Resolved
    case class Command(segments: Segments) extends Resolved
  }

  sealed trait Result

  case class Success(value: Set[Resolved]) extends Result {
    assert(value.nonEmpty)
  }

  sealed trait Failed extends Result

  case class NotFound(
      deepest: Segments,
      found: Set[Resolved],
      next: Segment,
      possibleNexts: Set[Segment]
  ) extends Failed

  case class Error(msg: String) extends Failed

  def catchWrapException[T](t: => T): Either[String, T] = {
    try Right(t)
    catch {
      case e: InvocationTargetException => makeResultException(e.getCause, new Exception())
      case e: Exception => makeResultException(e, new Exception())
    }
  }

  def makeResultException(e: Throwable, base: Exception): Left[String, Nothing] = {
    val outerStack = new mill.api.Result.OuterStack(base.getStackTrace)
    Left(mill.api.Result.Exception(e, outerStack).toString)
  }

  def resolve(
      baseModules: BaseModuleTree,
      remainingQuery: List[Segment],
      current: Resolved,
      querySoFar: Segments
  ): Result = {
    remainingQuery match {
      case Nil => Success(Set(current))
      case head :: tail =>
        def recurse(searchModules: Set[Resolved]): Result = {
          val (failures, successesLists) = searchModules
            .map(r => resolve(baseModules, tail, r, querySoFar ++ Seq(head)))
            .partitionMap { case s: Success => Right(s.value); case f: Failed => Left(f) }

          val (errors, notFounds) = failures.partitionMap {
            case s: NotFound => Right(s)
            case s: Error => Left(s.msg)
          }

          if (errors.nonEmpty) Error(errors.mkString("\n"))
          else if (successesLists.flatten.nonEmpty) Success(successesLists.flatten)
          else notFounds.size match {
            case 1 => notFounds.head
            case _ => notFoundResult(baseModules, querySoFar, current, head)
          }
        }

        (head, current) match {
          case (Segment.Label(singleLabel), m: Resolved.Module) =>
            val resOrErr = singleLabel match {
              case "__" =>
                val self = Seq(Resolved.Module(m.segments, m.cls))
                val transitiveOrErr =
                  resolveTransitiveChildren(
                    baseModules,
                    m.cls,
                    None,
                    current.segments,
                    Nil
                  )

                transitiveOrErr.map(transitive => self ++ transitive)

              case "_" =>
                resolveDirectChildren(
                  baseModules,
                  m.cls,
                  None,
                  current.segments
                )

              case pattern if pattern.startsWith("__:") =>
                val typePattern = pattern.split(":").drop(1)
                val self = Seq(Resolved.Module(m.segments, m.cls))

                val transitiveOrErr = resolveTransitiveChildren(
                  baseModules,
                  m.cls,
                  None,
                  current.segments,
                  typePattern
                )

                transitiveOrErr.map(transitive => self ++ transitive)

              case pattern if pattern.startsWith("_:") =>
                val typePattern = pattern.split(":").drop(1)
                resolveDirectChildren(
                  baseModules,
                  m.cls,
                  None,
                  current.segments,
                  typePattern
                )

              case _ =>
                resolveDirectChildren(
                  baseModules,
                  m.cls,
                  Some(singleLabel),
                  current.segments
                )
            }

            resOrErr match {
              case Left(err) => Error(err)
              case Right(res) => recurse(res.toSet)
            }

          case (Segment.Cross(cross), m: Resolved.Module) =>
            if (classOf[Cross[_]].isAssignableFrom(m.cls)) {
              instantiateModule(baseModules, current.segments).flatMap {
                case c: Cross[_] =>
                  catchWrapException(
                    if (cross == Seq("__")) for ((_, v) <- c.valuesToModules.toSeq) yield v
                    else if (cross.contains("_")) {
                      for {
                        (segments, v) <- c.segmentsToModules.toList
                        if segments.length == cross.length
                        if segments.zip(cross).forall { case (l, r) => l == r || r == "_" }
                      } yield v
                    } else {
                      val crossOrDefault = if (cross.isEmpty) {
                        // We want to select the default (first) crossValue
                        c.defaultCrossSegments
                      } else cross
                      c.segmentsToModules.get(crossOrDefault.toList).toSeq
                    }
                  )
              } match {
                case Left(err) => Error(err)
                case Right(searchModules) =>
                  recurse(
                    searchModules
                      .map(m => Resolved.Module(m.millModuleSegments, m.getClass))
                      .toSet
                  )
              }

            } else notFoundResult(baseModules, querySoFar, current, head)

          case _ => notFoundResult(baseModules, querySoFar, current, head)
        }
    }
  }
  def instantiateModule(
      baseModules: BaseModuleTree,
      segments: Segments
  ): Either[String, Module] =
    instantiateModule0(baseModules, segments).map(_._1)

  def instantiateModule0(
      baseModules: BaseModuleTree,
      segments: Segments
  ): Either[String, (Module, BaseModule)] = {

    segments.value.foldLeft[Either[String, (Module, BaseModule)]](Right((
      baseModules.rootModule,
      baseModules.rootModule
    ))) {
      case (Right((current, currentRoot)), Segment.Label(s)) =>
        assert(s != "_", s)
        resolveDirectChildren0(
          baseModules,
          current.millModuleSegments,
          current.getClass,
          Some(s)
        ).flatMap {
          case Seq((_, Some(f))) =>
            val res = f(current)
            res.map {
              case b: BaseModule =>
                (b, b)
              case b =>
                (b, currentRoot)
            }
          case unknown =>
            sys.error(
              s"Unable to resolve single child " +
                s"rootModule: ${baseModules.rootModule}, segments: ${segments.render}," +
                s"current: $current, s: ${s}, unknown: $unknown"
            )
        }

      case (Right((current, currentRoot)), Segment.Cross(vs)) =>
        assert(!vs.contains("_"), vs)

        catchWrapException(
          current
            .asInstanceOf[Cross[_]]
            .segmentsToModules(vs.toList)
            .asInstanceOf[Module] -> currentRoot
        )

      case (Left(err), _) => Left(err)
    }

  }

  def resolveTransitiveChildren(
      baseModules: BaseModuleTree,
      cls: Class[_],
      nameOpt: Option[String],
      segments: Segments,
      typePattern: Seq[String]
  ): Either[String, Set[Resolved]] = {
    val direct =
      resolveDirectChildren(baseModules, cls, nameOpt, segments, typePattern)
    direct.flatMap { direct =>
      for {
        directTraverse <-
          resolveDirectChildren(baseModules, cls, nameOpt, segments, Nil)
        indirect0 = directTraverse
          .collect { case m: Resolved.Module =>
            resolveTransitiveChildren(
              baseModules,
              m.cls,
              nameOpt,
              m.segments,
              typePattern
            )
          }
        indirect <- EitherOps.sequence(indirect0).map(_.flatten)
      } yield direct ++ indirect
    }
  }

  private def resolveParents(c: Class[_]): Seq[Class[_]] =
    Seq(c) ++
      Option(c.getSuperclass).toSeq.flatMap(resolveParents) ++
      c.getInterfaces.flatMap(resolveParents)

  /**
   * Check if the given class matches a given type selector as string
   * @param cls
   * @param typePattern
   * @return
   */
  private def classMatchesTypePred(typePattern: Seq[String])(cls: Class[_]): Boolean =
    typePattern
      .forall { pat =>
        val negate = pat.startsWith("^") || pat.startsWith("!")
        val clsPat = pat.drop(if (negate) 1 else 0)

        // We split full class names by `.` and `$`
        // a class matches a type patter, if the type pattern segments match from the right
        // to express a full match, use `_root_` as first segment

        val typeNames = clsPat.split("[.$]").toSeq.reverse

        val parents = resolveParents(cls)
        val classNames = parents.flatMap(c =>
          ("_root_$" + c.getName).split("[.$]").toSeq.reverse.inits.toSeq.filter(_.nonEmpty)
        )

        val isOfType = classNames.contains(typeNames)
        if (negate) !isOfType else isOfType
      }

  def resolveDirectChildren(
      baseModules: BaseModuleTree,
      cls: Class[_],
      nameOpt: Option[String],
      segments: Segments
  ): Either[String, Set[Resolved]] =
    resolveDirectChildren(
      baseModules,
      cls,
      nameOpt,
      segments,
      typePattern = Nil
    )

  def resolveDirectChildren(
      baseModules: BaseModuleTree,
      cls: Class[_],
      nameOpt: Option[String],
      segments: Segments,
      typePattern: Seq[String]
  ): Either[String, Set[Resolved]] = {

    val crossesOrErr = if (classOf[Cross[_]].isAssignableFrom(cls) && nameOpt.isEmpty) {
      instantiateModule(baseModules, segments).map {
        case cross: Cross[_] =>
          for (item <- cross.items) yield {
            Resolved.Module(segments ++ Segment.Cross(item.crossSegments), item.cls)
          }

        case _ => Nil
      }
    } else Right(Nil)

    crossesOrErr.flatMap { crosses =>
      val filteredCrosses = crosses.filter { c =>
        classMatchesTypePred(typePattern)(c.cls)
      }

      resolveDirectChildren0(baseModules, segments, cls, nameOpt, typePattern)
        .map(
          _.map {
            case (Resolved.Module(s, cls), _) => Resolved.Module(segments ++ s, cls)
            case (Resolved.Target(s), _) => Resolved.Target(segments ++ s)
            case (Resolved.Command(s), _) => Resolved.Command(segments ++ s)
          }
            .toSet
            .++(filteredCrosses)
        )
    }
  }

  def resolveDirectChildren0(
      baseModules: BaseModuleTree,
      segments: Segments,
      cls: Class[_],
      nameOpt: Option[String]
  ): Either[String, Seq[(Resolved, Option[Module => Either[String, Module]])]] =
    resolveDirectChildren0(baseModules, segments, cls, nameOpt, Nil)

  def resolveDirectChildren0(
      baseModules: BaseModuleTree,
      segments: Segments,
      cls: Class[_],
      nameOpt: Option[String],
      typePattern: Seq[String]
  ): Either[String, Seq[(Resolved, Option[Module => Either[String, Module]])]] = {
    def namePred(n: String) = nameOpt.isEmpty || nameOpt.contains(n)

    val modulesOrErr: Either[String, Seq[(Resolved, Option[Module => Either[String, Module]])]] = {
      if (classOf[DynamicModule].isAssignableFrom(cls)) {
        instantiateModule(baseModules, segments).map {
          case m: DynamicModule =>
            m.millModuleDirectChildren
              .filter(c => namePred(c.millModuleSegments.parts.last))
              .filter(c => classMatchesTypePred(typePattern)(c.getClass))
              .map(c =>
                (
                  Resolved.Module(
                    Segments.labels(c.millModuleSegments.parts.last),
                    c.getClass
                  ),
                  Some((x: Module) => Right(c))
                )
              )
        }
      } else Right {
        val nestedModuleScObjects =
          for {
            prefix <- baseModules.prefixForClass(cls).toSeq
            (prefix2, m2) <- baseModules.lookupByParent(Some(prefix))
            if nameOpt.isEmpty || nameOpt.contains(prefix2.last)
          } yield (
            Resolved.Module(
              Segments.labels(decode(prefix2.last)),
              m2.getClass
            ),
            Some((_: Module) => Right(m2))
          )

        val reflectMemberObjects = Reflect
          .reflectNestedObjects0[Module](cls, namePred)
          .filter {
            case (_, member) =>
              val memberCls = member match {
                case f: java.lang.reflect.Field => f.getType
                case f: java.lang.reflect.Method => f.getReturnType
              }
              classMatchesTypePred(typePattern)(memberCls)
          }
          .map { case (name, member) =>
            Resolved.Module(
              Segments.labels(decode(name)),
              member match {
                case f: java.lang.reflect.Field => f.getType
                case f: java.lang.reflect.Method => f.getReturnType
              }
            ) -> (
              member match {
                case f: java.lang.reflect.Field =>
                  Some((x: Module) => catchWrapException(f.get(x).asInstanceOf[Module]))

                case f: java.lang.reflect.Method =>
                  Some((x: Module) => catchWrapException(f.invoke(x).asInstanceOf[Module]))
              }
            )
          }

        nestedModuleScObjects ++ reflectMemberObjects
      }
    }

    val targets = Reflect
      .reflect(
        cls,
        classOf[Task[_]],
        x =>
          namePred(x) &&
          baseModules.targetNamesByClass.exists{case (cls, names) =>
            cls.isAssignableFrom(cls) && names.contains(x)
          },
        noParams = true
      )
      .map { m =>
        Resolved.Target(Segments.labels(decode(m.getName))) ->
          None
      }

    val commands = Reflect
      .reflect(cls, classOf[Command[_]], namePred, noParams = false)
      .map(m => decode(m.getName))
      .map { name => Resolved.Command(Segments.labels(name)) -> None }

    modulesOrErr.map(_ ++ targets ++ commands)
  }

  def notFoundResult(
      baseModules: BaseModuleTree,
      querySoFar: Segments,
      current: Resolved,
      next: Segment
  ): NotFound = {
    val possibleNexts = current match {
      case m: Resolved.Module =>
        resolveDirectChildren(
          baseModules,
          m.cls,
          None,
          current.segments
        ).toOption.get.map(
          _.segments.value.last
        )

      case _ => Set[Segment]()
    }

    NotFound(querySoFar, Set(current), next, possibleNexts)
  }
}
