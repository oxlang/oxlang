// The Ox scanner.
// Used to implement the reader.

package io.ox_lang.scanner

import java.io.PushbackInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.Character
import java.lang.StringBuilder
import java.util.Iterator

// Tokens are read from streams, which are identified by a
// streamIdentifer of type T, defined by the user.
public data class StreamLocation<T>(
  val streamIdentifer: T,
  val offset: Long,
  val lineNumber: Long,
  val columnNumber: Long
)

// Tokens are of a type
public enum class TokenType {
  // lists are ()
  LPAREN, RPAREN,

  // lists are also []
  LBRACKET, RBRACKET,

  // mappings and sets use {}
  LBRACE, RBRACE,

  // Whitespace
  NEWLINE, WHITESPACE,

  // reader macros
  HASH, QUOTE, COMMENT,

  // atoms
  STRING, NUMBER, SYMBOL, KEYWORD
}

// Tokens themselves

public data class Token<T>(
  val tokenType: TokenType,
  val location: StreamLocation<T>,
  val value: Any
)

// Before we can define the scanner, we need the exception it throws

public class ScannerException(val location: StreamLocation<Object>, message: String, cause: Throwable? = null) : Exception(message, cause)

// And now for the scanner

private class TokenScanner<T>(
  val stream: PushbackInputStream,
  val streamIdentifer: T,
  val offset: Long = 0,
  val lineNumber: Long = 0,
  val columnNumber: Long = 0,
  var firstColumnIndex: Long = 0
) : Iterator<Token<T>> {
  // HACK (arrdem 2019-05-20):
  //   Newlines and every other character is a part of its own line.
  //   If we were to compute the "next" position each time we read(), we'd be off by one.
  //   The computed position is after all ONE AHEAD of the current position, which is 0-indexed.
  //   So we do this game where we keep two locations, the "current" and "next" locations.
  private var curLoc: StreamLocation<T> = StreamLocation<T>(streamIdentifer, 0, 0, 0)
  private var nextLoc: StreamLocation<T> = StreamLocation<T>(streamIdentifer, offset, lineNumber, columnNumber)

  private fun read(): Int {
    val c: Int = this.stream.read()

    if (c == -1) {
      return c
    } else {
      // Maintain location bookkeeping

      // FIXME (arrdem 2019-05-20):
      //   This isn't strictly correct - since we're using a UTF-8 backed
      //   reader, each char we read is quite possibly multibyte. Oh well.
      this.curLoc = this.nextLoc

      if (c == 10) {
        this.nextLoc = StreamLocation<T>(
          streamIdentifer,
          this.curLoc.offset + 1,
          this.curLoc.lineNumber + 1,
          firstColumnIndex
        );
      } else {
        // Yes this is broken for tabs, no I don't care, tabs are 1spc
        this.nextLoc = StreamLocation<T>(
          streamIdentifer,
          this.curLoc.offset + 1,
          this.curLoc.lineNumber,
          this.curLoc.columnNumber + 1
        );
      }

      return c
    }
  }

  private fun scanString(tt: TokenType, _c: Char): Token<T> {
    val start = curLoc
    val buff = StringBuilder()

    var escaped = false
    while (true) {
      val i = this.read()
      if (i == -1) {
        throw ScannerException(
          start as StreamLocation<Object>,
          "Reached end of stream while scanning a string!");
      } else {
        val c = i.toChar()
        if (escaped) {
          when (c) {
            '\\', '\"' -> { escaped = false; buff.append(c) }
            else -> throw ScannerException(
              start as StreamLocation<Object>,
              String.format("Encountered illegal escaped character %c while scanning a string!", c))
          }
        } else if (c == '\\') {
          escaped = true
        } else if (c == '\"') {
          break
        } else {
          buff.append(c)
        }
      }
    }
    return Token(tt, curLoc, buff.toString())
  }

  private fun scanSymbol(tt: TokenType, startChar: Char): Token<T> {
    val start = curLoc
    val buff = StringBuilder()
    val piped = when (startChar) {
      '|' -> true
      else -> {buff.append(startChar); false}
    }

    read@ while (true) {
      val i = this.read()
      if (i == -1 && !piped) {
        break
      } else if (i == -1 && piped) {
        throw ScannerException(
          start as StreamLocation<Object>,
          String.format("Encountered end of stream while scanning a piped symbol!")
        );
      } else {
        val c = i.toChar()
        when (c) {
          '|' -> break@read
          else -> buff.append(c)
        }
      }
    }

    return Token(tt, curLoc, buff.toString())
  }

  override fun hasNext(): Boolean {
    // I think this is correct - the iterator has
    // SOMETHING as long as the underlying PBR has
    // SOMETHING.
    var i = this.stream.read()
    try {
      return i != -1
    } finally {
      this.stream.unread(i)
    }
  }

  public override fun next(): Token<T>? {
    val c: Int = this.read()

    if (c == -1) {
      return null
    }

    val ch = Character.valueOf(c.toChar())

    val tt = when (c.toChar()) {
      '(' -> TokenType.LPAREN
      ')' -> TokenType.RPAREN
      '[' -> TokenType.LBRACKET
      ']' -> TokenType.RBRACKET
      '{' -> TokenType.LBRACE
      '}' -> TokenType.RBRACE
      ';' -> TokenType.COMMENT
      '#' -> TokenType.HASH
      '\'' -> TokenType.QUOTE
      '\"' -> TokenType.STRING

      else -> when {
        // really getting fancy here, gonna need some more logic
        Character.isWhitespace(c) -> TokenType.WHITESPACE
        Character.isDigit(c) -> TokenType.NUMBER
        else -> TokenType.SYMBOL
      }
    }

    // String scanning
    when (tt) {
      TokenType.STRING -> return scanString(tt, ch)
      TokenType.SYMBOL -> return scanSymbol(tt, ch)
      // FIXME (arrdem 2019-05-21):
      //   Process numbers
      else -> return Token(tt, curLoc, ch)
    }
  }

  public override fun remove() {
    throw UnsupportedOperationException()
  }
}

// Forcing the generated class name
object Scanner {
  public fun <T> scan(stream: InputStream, streamIdentifier: T): Iterator<Token<T>> {
    // yo dawg I heard u leik streams
    return TokenScanner<T>(PushbackInputStream(InputStreamReader(stream, "UTF-8") as InputStream), streamIdentifier)
  }

  @JvmStatic fun main(args: Array<String>) {
    for ((index, arg) in args.withIndex()) {
      val scanner = scan(ByteArrayInputStream(arg.toByteArray()), String.format("Arg %d", index))
      while (scanner.hasNext()) {
        System.out.println(scanner.next())
      }
    }
  }
}