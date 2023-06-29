package mill.codesig

import JType.{Cls => JCls}

/**
 * Traverses the call graph and inheritance hierarchy summaries produced by
 * [[LocalSummarizer]] and [[ExternalSummarizer]] to resolve method calls to
 * their potential destinations and compute transitive properties of the
 * call graph
 */
object MethodCallResolver{
  case class Result(localCalls: Map[MethodCall, Set[MethodDef]],
                    externalCalledClasses: Map[MethodCall, Set[JCls]],
                    externalClassLocalDests: Map[JCls, Set[MethodDef]])

  def resolveAllMethodCalls(localSummary: LocalSummarizer.Result,
                            externalSummary: ExternalSummarizer.Result,
                            logger: Logger): Result = {

    val allDirectAncestors = logger{
      localSummary.mapValues(_.directAncestors) ++
      externalSummary.directAncestors
    }

    val directDescendents = logger{
      allDirectAncestors
        .toVector
        .flatMap { case (k, vs) => vs.map((_, k)) }
        .groupMap(_._1)(_._2)
    }

    val externalClsToLocalClsMethods0 = logger{
      localSummary
        .items
        .keySet
        .flatMap { cls =>
          transitiveExternalMethods(cls, allDirectAncestors, externalSummary.directMethods)
            .map { case (upstreamCls, localMethods) =>
              // <init> methods are final and cannot be overriden
              (upstreamCls, Map(cls -> localMethods.filter(m => !m.static && m.name != "<init>")))
            }
        }
        .groupMapReduce(_._1)(_._2)(_ ++ _)
    }

    // Make sure that when doing an external method call, we look up all
    // methods both defined on and inherited by the type in question, since
    // any of those could potentially get called by the external method
    val externalClsToLocalClsMethods = logger{
      externalClsToLocalClsMethods0.map{ case (cls, _) =>

        val all = clsAndAncestors(Seq(cls), _ => false, allDirectAncestors)
          .toVector
          .map(externalClsToLocalClsMethods0(_))

        val allKeys = all
          .flatMap(_.keys)
          .map((key: JCls) => (key, all.flatMap(_.get(key)).flatten.toSet))
          .toMap


        cls -> allKeys
      }
    }

    val resolvedCalls = resolveAllMethodCalls0(
      localSummary,
      externalClsToLocalClsMethods,
      allDirectAncestors,
      localSummary.mapValues(_.superClass) ++ externalSummary.directSuperclasses,
      directDescendents,
      externalSummary.directMethods,
      logger
    )

    resolvedCalls
  }

  def resolveAllMethodCalls0(localSummary: LocalSummarizer.Result,
                             externalClsToLocalClsMethods: Map[JCls, Map[JCls, Set[MethodSig]]],
                             allDirectAncestors: Map[JCls, Set[JCls]],
                             directSuperclasses: Map[JCls, JCls],
                             directDescendents: Map[JCls, Vector[JCls]],
                             externalDirectMethods: Map[JCls, Set[MethodSig]],
                             logger: Logger): Result = {

    def methodExists(cls: JCls, call: MethodCall): Boolean = {
      localSummary.items.get(cls).exists(_.methods.keysIterator.exists(sigMatchesCall(_, call))) ||
      externalDirectMethods.get(cls).exists(_.exists(sigMatchesCall(_, call)))
    }

    def resolveLocalReceivers(call: MethodCall): Set[JCls] = call.invokeType match {
      case InvokeType.Static =>
        clsAndSupers(call.cls, methodExists(_, call), directSuperclasses)
          .find(methodExists(_, call))
          .toSet

      case InvokeType.Special => Set(call.cls)

      case InvokeType.Virtual =>
        val directDef = call.toDirectMethodDef
        if (localSummary.get(call.cls, directDef).exists(_.isPrivate)) Set(call.cls)
        else {
          val descendents = clsAndDescendents(call.cls, directDescendents)

          clsAndAncestors(descendents, methodExists(_, call), allDirectAncestors)
            .filter(methodExists(_, call))
        }
    }

    val allCalls = localSummary
      .mapValuesOnly(_.methods)
      .iterator
      .flatMap(_.values)
      .flatMap(_.calls)
      .toSet

    val callToResolved = logger {
      allCalls
        .iterator
        .map { call =>
          val (localReceivers, externalReceivers) =
            resolveLocalReceivers(call).partition(localSummary.contains)

          val localMethodDefs = localReceivers.map(MethodDef(_, call.toDirectMethodDef))

          // When a call to an external method call is made, we don't know what the
          // implementation will do. We thus have to conservatively assume it can call
          // any method on any of the argument types that get passed to it, including
          // the `this` type if the method call is not static.
          val methodParamClasses =
            if (externalReceivers.isEmpty) Set.empty[JCls]
            else {
              val argTypes = call.desc.args.collect { case c: JCls => c }
              val thisTypes =
                if (call.invokeType == InvokeType.Static) Set.empty[JCls] else externalReceivers

              (argTypes ++ thisTypes).toSet
            }

          (call, (localMethodDefs, methodParamClasses))
        }
        .toMap
    }

    Result(
      localCalls = callToResolved
        .map{ case (call, (local, external)) => (call, local)},

      externalCalledClasses = callToResolved
        .map{ case (call, (local, external)) => (call, external)},

      externalClassLocalDests = callToResolved
        .flatMap { case (call, (_, external)) => external }
        .map{cls =>
          cls -> externalClsToLocalClsMethods.getOrElse(cls, Nil)
            .flatMap { case (k, vs) => vs.map(m => MethodDef(k, m)) }
            .toSet
        }
        .toMap
    )

  }

  def transitiveExternalAncestors(cls: JCls,
                                  allDirectAncestors: Map[JCls, Set[JCls]]): Set[JCls] = {
    Set(cls) ++
    allDirectAncestors
      .getOrElse(cls, Set.empty[JCls])
      .flatMap(transitiveExternalAncestors(_, allDirectAncestors))
  }

  def transitiveExternalMethods(cls: JCls,
                                allDirectAncestors: Map[JCls, Set[JCls]],
                                externalDirectMethods: Map[JCls, Set[MethodSig]]): Map[JCls, Set[MethodSig]] = {
    allDirectAncestors(cls)
      .flatMap(transitiveExternalAncestors(_, allDirectAncestors))
      .map(cls => (cls, externalDirectMethods.getOrElse(cls, Set())))
      .toMap
  }

  def sigMatchesCall(sig: MethodSig, call: MethodCall) = {
    sig.name == call.name && sig.desc == call.desc && (sig.static == (call.invokeType == InvokeType.Static))
  }


  def clsAndSupers(cls: JCls,
                   skipEarly: JCls => Boolean,
                   directSuperclasses: Map[JCls, JCls]): Seq[JCls] = {
    Util.breadthFirst(Seq(cls))(cls =>
      if(skipEarly(cls)) Nil else directSuperclasses.get(cls)
    )
  }

  def clsAndAncestors(classes: IterableOnce[JCls],
                      skipEarly: JCls => Boolean,
                      allDirectAncestors: Map[JCls, Set[JCls]]): Set[JCls] = {
    Util.breadthFirst(classes)(cls =>
      if(skipEarly(cls)) Nil else allDirectAncestors.getOrElse(cls, Nil)
    ).toSet
  }

  def clsAndDescendents(cls: JCls,
                        directDescendents: Map[JCls, Vector[JCls]]): Set[JCls] = {
    Util.breadthFirst(Seq(cls))(directDescendents.getOrElse(_, Nil)).toSet
  }

}