//// SNIPPET:BUILD1
import mill._, javalib._

object foo extends JavaModule {

  def sources = Task {
    os.write(
      Task.dest / "Foo.java",
      """package foo;
        |
        |public class Foo {
        |    public static void main(String[] args) {
        |        System.out.println("Hello World");
        |    }
        |}
      """.stripMargin
    )
    Seq(PathRef(Task.dest))
  }

  def compile = Task {
    println("Compiling...")
    super.compile()
  }

  def run(args: Task[Args] = Task.anon(Args())) = Task.command {
    println("Running..." + args().value.mkString(" "))
    super.run(args)()
  }
}
//// SNIPPET:BUILD2

object foo2 extends JavaModule {
  def generatedSources = Task {
    os.write(Task.dest / "Foo.java", """...""")
    Seq(PathRef(Task.dest))
  }
}

object foo3 extends JavaModule {
  def sources = Task {
    os.write(Task.dest / "Foo.java", """...""")
    super.sources() ++ Seq(PathRef(Task.dest))
  }
}
