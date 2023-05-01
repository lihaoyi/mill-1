package mill.main

import mill.define._
import mill.eval.{Evaluator, EvaluatorPaths}
import mill.util.{Watchable, Watched}
import mill.api.{PathRef, Result}
import mill.api.Strict.Agg


object RunScript {

  type TaskName = String

  def evaluateTasks(
      evaluator: Evaluator,
      scriptArgs: Seq[String],
      selectMode: SelectMode
  ): Either[String, (Seq[Watchable], Either[String, Seq[(Any, Option[ujson.Value])]])] = {
    for (targets <- ResolveTasks.resolve(evaluator, scriptArgs, selectMode))
      yield {
        val (watched, res) = evaluate(evaluator, Agg.from(targets.distinct))

        val watched2 = for {
          x <- res.toSeq
          (Watched(_, extraWatched), _) <- x
          w <- extraWatched
        } yield w

        pprint.log(watched.map(_.pretty))
        pprint.log(watched2.map(_.pretty))
        (watched ++ watched2, res)
      }
  }

  def evaluateTasksNamed[T](
      evaluator: Evaluator,
      scriptArgs: Seq[String],
      selectMode: SelectMode
  ): Either[String, (Seq[Watchable], Either[String, Seq[(Any, Option[(TaskName, ujson.Value)])]])] = {
    for (targets <- ResolveTasks.resolve(evaluator, scriptArgs, selectMode))
      yield {
        val (watched, res) = evaluateNamed(evaluator, Agg.from(targets.distinct))

        val watched2 = for {
          x <- res.toSeq
          (Watched(_, extraWatched), _) <- x
          w <- extraWatched
        } yield w

        (watched ++ watched2, res)
      }
  }

  def evaluate(
      evaluator: Evaluator,
      targets: Agg[Task[Any]]
  ): (Seq[Watchable], Either[String, Seq[(Any, Option[ujson.Value])]]) = {
    val (watched, results) = evaluateNamed(evaluator, targets)
    pprint.log(watched.map(_.pretty))
    // we drop the task name in the inner tuple
    (watched, results.map(_.map(p => (p._1, p._2.map(_._2)))))
  }

  /**
   * @param evaluator
   * @param targets
   * @return (watched-paths, Either[err-msg, Seq[(task-result, Option[(task-name, task-return-as-json)])]])
   */
  def evaluateNamed(
      evaluator: Evaluator,
      targets: Agg[Task[Any]]
  ): (Seq[Watchable], Either[String, Seq[(Any, Option[(TaskName, ujson.Value)])]]) = {
    val evaluated: Evaluator.Results = evaluator.evaluate(targets)
    val watched = evaluated.results
      .iterator
      .collect {
        case (t: SourcesImpl, Result.Success(ps: Seq[PathRef], _)) => ps.map(Watchable.Path(_))
        case (t: SourceImpl, Result.Success(p: PathRef, _)) => Seq(Watchable.Path(p))
        case (t: InputImpl[_], Result.Success(v, signature)) =>
          val pretty = t.ctx0.fileName + ":" + t.ctx0.lineNum
          println("INPUT FOUND " + pretty)
          Seq(Watchable.Value(() => signature(), signature(), pretty))
      }
      .flatten
      .toSeq

    pprint.log(targets)
    pprint.log(watched.map(_.pretty))

    val errorStr = Evaluator.formatFailing(evaluated)

    evaluated.failing.keyCount match {
      case 0 =>
        val nameAndJson = for (t <- targets.toSeq) yield {
          t match {
            case t: mill.define.NamedTask[_] =>
              val jsonFile = EvaluatorPaths.resolveDestPaths(evaluator.outPath, t).meta
              val metadata = upickle.default.read[Evaluator.Cached](ujson.read(jsonFile.toIO))
              Some(t.toString, metadata.value)

            case _ => None
          }
        }

        watched -> Right(evaluated.values.zip(nameAndJson))
      case n => watched -> Left(s"$n targets failed\n$errorStr")
    }
  }

}
