package mill.javalib

import mill.*
import mill.javalib.publish.{Artifact, PublishInfo}
import mill.util.Jvm
import os.Path

private[mill] trait MavenWorkerSupport extends CoursierModule with OfflineSupportModule {
  private def mavenWorkerClasspath: T[Seq[PathRef]] = Task {
    defaultResolver().classpath(Seq(
      Dep.millProjectModule("mill-libs-javalib-maven-worker")
    ))
  }

  override def prepareOffline(all: mainargs.Flag): Task.Command[Seq[PathRef]] = Task.Command {
    (super.prepareOffline(all)() ++ mavenWorkerClasspath()).distinct
  }

  private def mavenWorkerClassloader: Task.Worker[ClassLoader] = Task.Worker {
    val classPath = mavenWorkerClasspath().map(_.path)
    Jvm.createClassLoader(classPath = classPath, parent = getClass.getClassLoader)
  }

  private[mill] def mavenWorker: Task.Worker[internal.MavenWorkerSupport.Api] = Task.Worker {
    mavenWorkerClassloader().loadClass("mill.javalib.maven.worker.impl.WorkerImpl")
      .getConstructor().newInstance().asInstanceOf[internal.MavenWorkerSupport.Api]
  }
}
object MavenWorkerSupport {
  trait Api {

    /** Publishes artifacts to a remote Maven repository. */
    def publishToRemote(
        uri: String,
        workspace: os.Path,
        username: String,
        password: String,
        artifacts: IterableOnce[RemoteM2Publisher.M2Artifact]
    ): RemoteM2Publisher.DeployResult
  }

  object RemoteM2Publisher {
    @deprecated("This should have been an internal API.", "1.0.1")
    def asM2Artifacts(
        pom: os.Path,
        artifact: Artifact,
        publishInfos: IterableOnce[PublishInfo]
    ): List[M2Artifact] =
      M2Artifact.POM(
        pom,
        artifact
      ) +: publishInfos.iterator.map(M2Artifact.Default(_, artifact)).toList

    private[mill] def asM2ArtifactsFromPublishDatas(
        artifact: Artifact,
        publishDatas: Map[os.SubPath, PathRef]
    ): List[M2Artifact.Default] =
      publishDatas.iterator.map { case (name, pathRef) =>
        val publishInfo = PublishInfo.IvyMetadata.parseFromFile(
          fileName = name.toString,
          artifactId = artifact.id,
          artifactVersion = artifact.version
        ).toPublishInfo(pathRef)
        M2Artifact.Default(publishInfo, artifact): M2Artifact.Default
      }.toList

    enum M2Artifact {
      case Default(info: PublishInfo, artifact: Artifact)
      case POM(pom: os.Path, artifact: Artifact)
    }

    case class DeployResult(
        // The classes which Maven returns aren't loaded here, thus we'd need to perform a translation, but we do not use
        // the result for anything else but logging, thus we just convert them to strings on the worker side.
        artifacts: Vector[String],
        // Same
        metadatas: Vector[String]
    ) {
      override def toString: String =
        s"""DeployResult(
           |  artifacts = ${artifacts.mkString("[\n    ", "\n    ", "\n  ]")}
           |  metadatas = ${metadatas.mkString("[\n    ", "\n    ", "\n  ]")}
           |)
           |""".stripMargin
    }
  }
}
