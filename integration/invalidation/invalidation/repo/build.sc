import mill._

import $ivy.`org.scalaj::scalaj-http:2.4.2`

def task = Task {
  build.a.input()
  build.b.input()
  build.c.input()
}

object module extends Module {
  def task = Task {
    println("task")
    build.a.input()
    build.b.input()
    build.c.input()
  }
}

def taskE = Task {
  println("taskE")
  build.e.input()
}

def taskSymbols = Task {
  println("taskSymbols")
  build.`-#!+→&%=~`.input()
}

def taskSymbolsInFile = Task {
  println("taskSymbolsInFile")
  build.`-#+&%`.module.input()
}
