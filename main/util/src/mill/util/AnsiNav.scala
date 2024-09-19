package mill.util

import java.io.PrintStream

// Reference https://gist.github.com/fnky/458719343aabd01cfb17a3a4f7296797
case class AnsiNav(output: PrintStream) {
  def control(n: Int, c: Char): Unit = output.print(AnsiNav.control(n, c))

  /**
   * Move up `n` squares
   */
  def up(n: Int): Any = if (n != 0)  control(n, 'A')

  /**
   * Move down `n` squares
   */
  def down(n: Int): Any = if (n != 0) control(n, 'B')

  /**
   * Move right `n` squares
   */
  def right(n: Int): Any = if (n != 0)  control(n, 'C')


  /**
   * Move left `n` squares
   */
  def left(n: Int): Any = if (n != 0)  control(n, 'D')

  /**
   * Clear the screen
   *
   * n=0: clear from cursor to end of screen
   * n=1: clear from cursor to start of screen
   * n=2: clear entire screen
   */
  def clearScreen(n: Int): Unit = control(n, 'J')

  /**
   * Clear the current line
   *
   * n=0: clear from cursor to end of line
   * n=1: clear from cursor to start of line
   * n=2: clear entire line
   */
  def clearLine(n: Int): Unit = control(n, 'K')

}
object AnsiNav{
  def control(n: Int, c: Char): String = "\u001b[" + n + c
  def up(n: Int): String = if (n != 0) control(n, 'A') else ""
  def down(n: Int): String = if (n != 0) control(n, 'B') else ""
  def right(n: Int): String = if (n != 0)  control(n, 'C') else ""
  def left(n: Int): String = if (n != 0)  control(n, 'D') else ""
  def clearScreen(n: Int): String = control(n, 'J')
  def clearLine(n: Int): String = control(n, 'K')
}
