package mill.runner

import sun.misc.{Signal, SignalHandler}

import java.io._
import java.net.Socket
import scala.jdk.CollectionConverters._
import org.newsclub.net.unix.AFUNIXServerSocket
import org.newsclub.net.unix.AFUNIXSocketAddress
import mill.main.client._
import mill.api.{SystemStreams, internal}
import mill.main.client.ProxyStream.Output
import mill.main.client.lock.{Lock, Locks}
import scala.util.Try

object MillServerMain {
  def main(args0: Array[String]): Unit = {
    // Disable SIGINT interrupt signal in the Mill server.
    //
    // This gets passed through from the client to server whenever the user
    // hits `Ctrl-C`, which by default kills the server, which defeats the purpose
    // of running a background server. Furthermore, the background server already
    // can detect when the Mill client goes away, which is necessary to handle
    // the case when a Mill client that did *not* spawn the server gets `CTRL-C`ed
    Signal.handle(
      new Signal("INT"),
      new SignalHandler() {
        def handle(sig: Signal) = {} // do nothing
      }
    )

    val acceptTimeoutMillis =
      Try(System.getProperty("mill.server_timeout").toInt).getOrElse(5 * 60 * 1000) // 5 minutes

    new MillServerMain(
      serverDir = os.Path(args0(0)),
      () => System.exit(Util.ExitServerCodeWhenIdle()),
      acceptTimeoutMillis = acceptTimeoutMillis,
      Locks.files(args0(0))
    ).run()
  }
}
class MillServerMain(
    serverDir: os.Path,
    interruptServer: () => Unit,
    acceptTimeoutMillis: Int,
    locks: Locks
) extends mill.main.server.Server[RunnerState](
      serverDir,
      interruptServer,
      acceptTimeoutMillis,
      locks
    ) {

  def stateCache0 = RunnerState.empty

  def main0(
      args: Array[String],
      stateCache: RunnerState,
      mainInteractive: Boolean,
      streams: SystemStreams,
      env: Map[String, String],
      setIdle: Boolean => Unit,
      userSpecifiedProperties: Map[String, String],
      initialSystemProperties: Map[String, String]
  ): (Boolean, RunnerState) = {
    try MillMain.main0(
        args = args,
        stateCache = stateCache,
        mainInteractive = mainInteractive,
        streams0 = streams,
        bspLog = None,
        env = env,
        setIdle = setIdle,
        userSpecifiedProperties0 = userSpecifiedProperties,
        initialSystemProperties = initialSystemProperties
      )
    catch MillMain.handleMillException(streams.err, stateCache)
  }
}
