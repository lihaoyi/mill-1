package mill.contrib.gitlab

import mill._
import mill.api.Result.{Failure, Success}
import mill.api.Result
import mill.define.{Command, ExternalModule, Task}
import scalalib._

trait GitlabPublishModule extends PublishModule { outer =>

  def publishRepository: ProjectRepository

  def skipPublish: Boolean = false

  def tokenLookup: GitlabTokenLookup = new GitlabTokenLookup {}

  def gitlabHeaders(
      systemProps: Map[String, String] = sys.props.toMap
  ): Task[GitlabAuthHeaders] = task.anon {
    val auth = tokenLookup.resolveGitlabToken(task.env, systemProps, task.workspace)
    auth match {
      case Left(msg) =>
        Failure(
          s"Token lookup for PUBLISH repository ($publishRepository) failed with $msg"
        ): Result[GitlabAuthHeaders]
      case Right(value) => Success(value)
    }
  }

  def publishGitlab(
      readTimeout: Int = 60000,
      connectTimeout: Int = 5000
  ): define.Command[Unit] = task.command {

    val gitlabRepo = publishRepository

    val PublishModule.PublishData(artifactInfo, artifacts) = publishArtifacts()
    if (skipPublish) {
      task.log.info(s"SkipPublish = true, skipping publishing of $artifactInfo")
    } else {
      val uploader = new GitlabUploader(gitlabHeaders()(), readTimeout, connectTimeout)
      new GitlabPublisher(
        uploader.upload,
        gitlabRepo,
        task.log
      ).publish(artifacts.map { case (a, b) => (a.path, b) }, artifactInfo)
    }

  }
}

object GitlabPublishModule extends ExternalModule {

  def publishAll(
      personalToken: String,
      gitlabRoot: String,
      projectId: Int,
      publishArtifacts: mill.main.Tasks[PublishModule.PublishData],
      readTimeout: Int = 60000,
      connectTimeout: Int = 5000
  ): Command[Unit] = task.command {
    val repo = ProjectRepository(gitlabRoot, projectId)
    val auth = GitlabAuthHeaders.privateToken(personalToken)

    val artifacts = task.sequence(publishArtifacts.value)().map {
      case data @ PublishModule.PublishData(_, _) => data.withConcretePath
    }
    val uploader = new GitlabUploader(auth, readTimeout, connectTimeout)

    new GitlabPublisher(
      uploader.upload,
      repo,
      task.log
    ).publishAll(
      artifacts: _*
    )
  }

  lazy val millDiscover: mill.define.Discover[this.type] = mill.define.Discover[this.type]
}
