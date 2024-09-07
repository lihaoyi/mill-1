package mill.eval

import mill.api.BlockableExecutionContext

import java.util.concurrent.ForkJoinPool.ManagedBlocker
import scala.concurrent.ExecutionContext
import java.util.concurrent.{
  ExecutorService,
  ForkJoinPool,
  LinkedBlockingQueue,
  ThreadPoolExecutor,
  TimeUnit
}

private object ExecutionContexts {

  /**
   * Execution context that runs code immediately when scheduled, without
   * spawning a separate thread or thread-pool. Used to turn parallel-async
   * Future code into nice single-threaded code without needing to rewrite it
   */
  object RunNow extends BlockableExecutionContext {
    def blocking[T](t: => T): T = t
    def execute(runnable: Runnable): Unit = runnable.run()
    def reportFailure(cause: Throwable): Unit = {}
    def close(): Unit = () // do nothing
  }

  /**
   * A simple thread-pool-based ExecutionContext with configurable thread count
   * and AutoCloseable support
   */
  class ThreadPool(threadCount: Int) extends BlockableExecutionContext {
    val forkJoinPool: ForkJoinPool = new ForkJoinPool(threadCount)
    val threadPool: ExecutorService = forkJoinPool

    def blocking[T](t: => T): T = {
      @volatile var res: T = null.asInstanceOf[T]
      ForkJoinPool.managedBlock(new ManagedBlocker {
        def block(): Boolean = {
          res = t
          false
        }
        def isReleasable: Boolean = false
      })
      res
    }

    def execute(runnable: Runnable): Unit = threadPool.submit(runnable)
    def reportFailure(t: Throwable): Unit = {}
    def close(): Unit = threadPool.shutdown()
  }
}
