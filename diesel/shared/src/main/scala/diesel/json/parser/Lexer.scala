/*
 * Copyright 2018 The Diesel Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package diesel.json.parser

object Lexer {

  def apply(input: String): Lexer = new Lexer(input)

  sealed trait NextToken {
    val offset: Int
  }
  case class Eos(offset: Int)                      extends NextToken
  case class InvalidToken(offset: Int)             extends NextToken
  case class ValidToken(offset: Int, token: Token) extends NextToken

  sealed trait Rule {
    def token(input: String, offset: Int): Option[Token]
  }

  val rules: Seq[Rule] = Seq(
    NumberRule,
    BooleanRule,
    NullRule,
    StringRule,
    OpenArrayRule,
    CloseArrayRule,
    OpenObjectRule,
    CloseObjectRule,
    CommaRule,
    SemiColonRule
  )

  object NumberRule extends Rule {

    private val digits: Set[Char] = "0123456789".toSet

    override def token(input: String, offset: Int): Option[Token] = {
      var index    = offset
      val len      = input.length()
      var digit    = true
      var nbDigits = 0
      while (index < len && digit) {
        val charAtIndex = input.charAt(index)
        digit = digits.contains(charAtIndex)
        if (digit) {
          nbDigits = nbDigits + 1
        }
        index = index + 1
      }
      if (nbDigits > 0) {
        Some(Token(offset, nbDigits, NumberLiteral))
      } else {
        None
      }
    }
  }

  object BooleanRule extends Rule {
    override def token(input: String, offset: Int): Option[Token] = {
      val sub = input.drop(offset)
      if (sub.startsWith("true")) {
        Some(Token(offset, 4, BooleanLiteral))
      } else if (sub.startsWith("false")) {
        Some(Token(offset, 5, BooleanLiteral))
      } else {
        None
      }
    }
  }

  object NullRule extends Rule {
    override def token(input: String, offset: Int): Option[Token] = {
      if (input.drop(offset).startsWith("null")) {
        Some(Token(offset, 4, NullLiteral))
      } else {
        None
      }
    }
  }

  object StringRule extends Rule {
    override def token(input: String, offset: Int): Option[Token] = {
      val l = input.length
      if (input.charAt(offset) == '"') {
        var i        = offset + 1
        var closedAt = -1
        while (i < l && closedAt == -1) {
          val c = input.charAt(i)
          if (c == '"') {
            closedAt = i
          } else {
            i = i + 1
          }
        }
        if (closedAt == -1) {
          None
        } else {
          Some(Token(offset, closedAt - offset + 1, StringLiteral))
        }
      } else {
        None
      }
    }
  }

  class SingleCharRule(val c: Char, val tokenType: TokenType) extends Rule {
    override def token(input: String, offset: Int): Option[Token] = {
      if (input.charAt(offset) == c) {
        Some(Token(offset, 1, tokenType))
      } else {
        None
      }
    }
  }

  object OpenArrayRule   extends SingleCharRule('[', OpenArray)
  object CloseArrayRule  extends SingleCharRule(']', CloseArray)
  object OpenObjectRule  extends SingleCharRule('{', OpenObject)
  object CloseObjectRule extends SingleCharRule('}', CloseObject)
  object CommaRule       extends SingleCharRule(',', Comma)
  object SemiColonRule   extends SingleCharRule(':', SemiColon)

}

class Lexer(input: String) {

  private var curIndex: Int    = 0
  private val inputLength: Int = input.length()

  import Lexer._

  def next(): NextToken = {
    // eat whitespaces
    while (curIndex < inputLength && input.charAt(curIndex).isWhitespace) {
      curIndex = curIndex + 1
    }

    if (curIndex >= inputLength) {
      Eos(curIndex)
    } else {

      // try lexer rules
      val rulesArr             = rules.toArray
      val nbRules              = rules.size
      var ruleIndex            = 0
      var token: Option[Token] = None
      while (ruleIndex < nbRules && token.isEmpty) {
        val rule = rulesArr.apply(ruleIndex)
        token = rule.token(input, curIndex)
        ruleIndex = ruleIndex + 1
      }
      token match {
        case None    =>
          InvalidToken(curIndex)
        case Some(t) =>
          val i = curIndex
          curIndex += t.length
          ValidToken(i, t)
      }
    }
  }

}
