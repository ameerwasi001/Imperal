import java.io.File
import kotlin.math.roundToInt

val LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".split("")
val DIGITS = "0123456789".split("")
val whitespaces = listOf('\t', "\r", " ")

//Tokens
val TT_NUMBER = "NUMBER"
val TT_IDENTIFIER = "IDENTIFIER"
val TT_KEYWORD = "KEYWORD"
val TT_PLUS = "PLUS"
val TT_MINUS = "MINUS"
val TT_MUL = "MUL"
val TT_DIV = "DIV"
val TT_RPAREN = "RPAREN"
val TT_LPAREN = "LPAREN"
val TT_RCURLY = "RCURLY"
val TT_LCURLY = "LCURLY"
val TT_RSQUARE = "RSQUARE"
val TT_LSQUARE = "LSQUARE"
val TT_EE = "EE"
val TT_NE = "NE"
val TT_GT = "GT"
val TT_LT = "LT"
val TT_GTE = "GTE"
val TT_LTE = "LTE"
val TT_EOF = "EOF"
val TT_NOT = "NOT"
val TT_AND = "AND"
val TT_OR = "OR"
val TT_STRING = "STRING"
val TT_EQUALS = "EQUALS"
val TT_COMMA = "COMMA"
val TT_COLON = "COLON"
val TT_ARROW = "ARROW"
val TT_DARROW = "DARROW"
val TT_NEWLINE = "NEWLINE"
val TT_ERROR = "ERROR"

val KEYWORDS = listOf(
  "fun",
  "if",
  "else",
  "while",
  "return",
  "for",
  "in",
  "as",
  "prev",
  "define",
  "encap",
  "sencap"
)

class Position(idx : Int, ln : Int, col : Int, fn : String, ftxt : String){
  public var idx : Int = idx
  public var ln : Int = ln
  public var col : Int = col
  public var fn : String = fn
  public var ftxt : String = ftxt

    fun advance(current_char : String? = null) : Position {
        this.idx += 1
        this.col += 1
        if(current_char == '\n'.toString()){
            this.ln += 1
            this.col = 0
          }
        return this
      }

    fun copy() : Position{
        return Position(this.idx, this.ln, this.col, this.fn, this.ftxt)
      }
}


open class Error(pos_start : Position?, pos_end : Position?, error_name : String, details : String){
    public val pos_start = pos_start
    public val pos_end = pos_end
    public val error_name = error_name
    public val details = details

    open fun as_string() : String {
        val result = "${this.error_name} : ${this.details}, File ${this.pos_start?.fn} in line number ${if (this.pos_start==null) this.pos_start else this.pos_start.ln + 1}"
        return result
      }
}

class IllegalCharacterError(pos_start : Position?, pos_end : Position?, details : String) : Error (pos_start, pos_end, "Illegal Character: ", details){}

class InvalidSyntaxError(pos_start : Position?, pos_end : Position?, details : String = "") : Error (pos_start, pos_end, "Invalid Syntax: ", details){}


class RTError(pos_start : Position?, pos_end : Position?, context : Context, details : String = "") : Error (pos_start, pos_end, "Runtime Error: ", details){
  val context = context

  override fun as_string() : String {
    var result = this.generate_traceback()
    result += "${this.error_name}: ${this.details}"
    return result
  }

  fun generate_traceback() : String {
    var result = ""
    var pos = this.pos_start
    var ctx : Context? = this.context
    while (ctx != null){
      result = "File: ${pos?.fn}, line: ${(if (pos == null) 0 else pos.ln+1).toString()} ${ctx.display_name} main\n" + result
      pos = ctx.parent_entry_pos
      ctx = ctx.parent
    }
    return "Error Traceback, (Most recent call last):\n" + result
  }
}

class Token(type_ : String, value : String?, pos_start : Position, pos_end : Position?) {
    public var type_ : String = type_
    public var value : String? = value
    public var pos_start : Position = pos_start.copy()
    public var pos_end : Position = if (pos_end==null) pos_start.copy() else pos_end.copy()

    fun matches(name : String, value : String) : Boolean{
        return ((this.type_ == name) && (this.value == value))
      }

    override fun toString() : String{
        if (this.value != null) {
          var type_ = this.type_
          var value = this.value
          return "[ $type_ : $value]"
        }
        return this.type_
      }
}

class Lexer(fn : String, text : String){
    public val fn = fn
    public val text = text
    public val pos = Position(-1, 0, -1, this.fn, this.text)
    public var current_char : String? = null
    init {
        this.advance()
      }

    fun advance(){
        this.pos.advance(this.current_char)
        this.current_char = if (this.pos.idx < this.text.length) this.text[this.pos.idx].toString() else null
      }

    fun make_number() : Token {
        var number_str = ""
        val pos_start = this.pos.copy()
        var e_count = 0
        var dot_count = 0
        val valid_chars = mutableListOf<String>()
        valid_chars.addAll(DIGITS)
        valid_chars.add("e")
        valid_chars.add(".")
        while((this.current_char != null) && (valid_chars.contains(this.current_char.toString()))){
            if (this.current_char == ".") {dot_count+=1}
            if (this.current_char == "e") {e_count+=1}
            if (dot_count == 2) {break}
            if (e_count == 2) {break}
            if ((dot_count == 1) && (e_count == 1)) {break}
            number_str += this.current_char
            this.advance()
          }
        return Token(TT_NUMBER, number_str, pos_start, this.pos.copy())
      }

  fun make_string() : Token {
    var string : String = ""
    var escape_character = false
    val escape_characters = mapOf(
      "t" to "\t",
      "n" to "\n"
    )
    val pos_start = this.pos.copy()
    this.advance()
    while (this.current_char != "\"" || escape_character){
      if (escape_character) {
        string += if (escape_characters[this.current_char] != null) escape_characters[this.current_char] else this.current_char
        escape_character = false
      } else {
        if (this.current_char == "\\"){
            escape_character = true
        } else {
            string += this.current_char
        }
        this.advance()
      }
    }
    this.advance()
    return Token(TT_STRING, string, pos_start, this.pos.copy())
  }

  fun make_identifier() : Token {
    var id_str = ""
    val valid_chars = mutableListOf<String>()
    val pos_start = this.pos.copy()
    valid_chars.addAll(LETTERS)
    valid_chars.addAll(DIGITS)
    valid_chars.add("_")
    while (valid_chars.contains(this.current_char.toString())){
      id_str += this.current_char
      this.advance()
    }
    if (KEYWORDS.contains(id_str)){
      return Token(TT_KEYWORD, id_str, pos_start, this.pos.copy())
    } else {
      return Token(TT_IDENTIFIER, id_str, pos_start, this.pos.copy())
    }
  }

  fun greater_or_ge() : Token {
    val pos_start = this.pos.copy()
    var tok_type = TT_GT
    this.advance()
    if (this.current_char == "="){
      tok_type = TT_GTE
      this.advance()
    }
    return Token(tok_type, null, pos_start, this.pos.copy())
  }

  fun lesser_or_le() : Token {
      val pos_start = this.pos.copy()
      var tok_type = TT_LT
      this.advance()
      if (this.current_char == "="){
          tok_type = TT_LTE
          this.advance()
      }
      return Token(tok_type, null, pos_start, this.pos.copy())
    }

    fun equals_or_ee_or_darrow() : Token {
        val pos_start = this.pos.copy()
        var tok_type = TT_EQUALS
        this.advance()
        if (this.current_char == "="){
            tok_type = TT_EE
            this.advance()
        } else if (this.current_char == ">"){
            tok_type = TT_DARROW
            this.advance()
        }
        return Token(tok_type, null, pos_start, this.pos.copy())
      }

      fun minus_or_arrow() : Token {
        val pos_start = this.pos.copy()
        var tok_type = TT_MINUS
        this.advance()
        if (this.current_char == ">"){
            tok_type = TT_ARROW
            this.advance()
        }
        return Token(tok_type, null, pos_start, this.pos.copy())
      }

    fun not_or_ne() : Token {
      val pos_start = this.pos.copy()
      var tok_type = TT_NOT
      this.advance()
      if (this.current_char == "="){
          tok_type = TT_NE
          this.advance()
      }
      return Token(tok_type, null, pos_start, this.pos.copy())
    }


    fun generate_tokens() : Pair<MutableList<Token>, Error?> {
      val tokens = mutableListOf<Token>()
      while (this.current_char != null){
        if (whitespaces.contains(this.current_char.toString())){
              this.advance()
        } else if (listOf("\n", ";").contains(this.current_char.toString())){
            tokens.add(Token(TT_NEWLINE, null, this.pos, null))
            this.advance()
        } else if (this.current_char == '+'.toString()){
            tokens.add(Token(TT_PLUS, null, this.pos, null))
            this.advance()
        } else if (this.current_char == '-'.toString()){
          tokens.add(this.minus_or_arrow())
        } else if (this.current_char == '*'.toString()){
          tokens.add(Token(TT_MUL, null, this.pos, null))
          this.advance()
        } else if (this.current_char == '/'.toString()){
          tokens.add(Token(TT_DIV, null, this.pos, null))
          this.advance()
        } else if (this.current_char == '('.toString()){
          tokens.add(Token(TT_RPAREN, null, this.pos, null))
          this.advance()
        } else if (this.current_char == ')'.toString()){
          tokens.add(Token(TT_LPAREN, null, this.pos, null))
          this.advance()
        } else if (this.current_char == "{"){
          tokens.add(Token(TT_RCURLY, null, this.pos, null))
          this.advance()
        } else if (this.current_char == "}"){
          tokens.add(Token(TT_LCURLY, null, this.pos, null))
          this.advance()
        } else if (this.current_char == "["){
          tokens.add(Token(TT_RSQUARE, null, this.pos, null))
          this.advance()
        } else if (this.current_char == "]"){
          tokens.add(Token(TT_LSQUARE, null, this.pos, null))
          this.advance()
        } else if (this.current_char == "|"){
          tokens.add(Token(TT_OR, null, this.pos, null))
          this.advance()
        } else if (this.current_char == "&"){
          tokens.add(Token(TT_AND, null, this.pos, null))
          this.advance()
        } else if (this.current_char == ":"){
          tokens.add(Token(TT_COLON, null, this.pos, null))
          this.advance()
        } else if (this.current_char == ","){
          tokens.add(Token(TT_COMMA, null, this.pos, null))
          this.advance()
        } else if (this.current_char == ">"){
          tokens.add(this.greater_or_ge())
        } else if (this.current_char == "<"){
          tokens.add(this.lesser_or_le())
        } else if (this.current_char == "!"){
          tokens.add(this.not_or_ne())
        } else if (this.current_char == "="){
          tokens.add(this.equals_or_ee_or_darrow())
        } else if (this.current_char == "\""){
          tokens.add(this.make_string())
        } else if (LETTERS.contains(this.current_char.toString())) {
          tokens.add(this.make_identifier())
        } else if (DIGITS.contains(this.current_char.toString())) {
          tokens.add(this.make_number())
        } else {
          val pos_start = this.pos.copy()
          val char = this.current_char
          this.advance()
          val toks = mutableListOf(Token(TT_ERROR, null, this.pos, null))
          return Pair(toks, IllegalCharacterError(pos_start, this.pos.copy(), "'$char'"))
        }
    }
    tokens.add(Token(TT_EOF, null, this.pos, null))
    return Pair(tokens, null)
  }
}

interface Visitor<out R>{
  fun visit(node: EmptyNode, context : Context): R
  fun visit(node: NumberNode, context : Context): R
  fun visit(node: StringNode, context : Context): R
  fun visit(node: BinOpNode, context : Context): R
  fun visit(node: UnaryOpNode, context : Context): R
  fun visit(node: VarAssignNode, context : Context): R
  fun visit(node: VarAccessNode, context : Context): R
  fun visit(node: StatementsNode, context : Context): R
  fun visit(node: IfNode, context : Context): R
  fun visit(node: FunDefNode, context : Context): R
  fun visit(node: FunCallNode, context : Context): R
  fun visit(node: ReturnNode, context : Context): R
  fun visit(node: WhileNode, context : Context): R
  fun visit(node: FunDeclNode, context : Context): R
  fun visit(node: ListNode, context : Context): R
  fun visit(node: ForNode, context : Context): R
  fun visit(node: EncapNode, context : Context): R
  fun visit(node: PrevNode, context : Context): R
}

interface Node {
  fun <R> accept(visitor : Visitor<R>, context : Context): R
  override fun toString(): String
}

class NumberNode(number : String, pos_start : Position, pos_end : Position) : Node {
  public val number = number
  public val pos_start = pos_start
  public val pos_end = pos_end

  override fun <R> accept(visitor : Visitor<R>, context : Context) : R {
    return visitor.visit(this, context)
  }

  override fun toString() : String {
        val num = this.number.toString()
        return "$num"
    }
}

class StringNode(string : String, pos_start : Position, pos_end : Position) : Node {
  public val string = string
  public val pos_start = pos_start
  public val pos_end = pos_end

  override fun <R> accept(visitor : Visitor<R>, context : Context) : R {
    return visitor.visit(this, context)
  }

  override fun toString() : String {
        val string = this.string
        return "$string"
    }
}

class ListNode(elements : MutableList<Node>, pos_start : Position, pos_end : Position) : Node {
  public val elements = elements
  public val pos_start = pos_start
  public val pos_end = pos_end

  override fun <R> accept(visitor : Visitor<R>, context : Context) : R {
    return visitor.visit(this, context)
  }

  override fun toString() : String {
    return "${this.elements}"
  }
}

class StatementsNode(statements : MutableList<Node>, may_decl : Boolean, pos_start : Position, pos_end : Position) : Node {
  public val statements = statements
  public val may_decl = may_decl
  public val pos_start = pos_start
  public val pos_end = pos_end

  override fun <R> accept(visitor : Visitor<R>, context : Context) : R {
    return visitor.visit(this, context)
  }

  override fun toString() : String {
    return "{${this.statements}} -> ${this.may_decl}"
  }
}

class BinOpNode(left : Node, op_tok : Token, right : Node, pos_start : Position, pos_end : Position) : Node{
  public val left = left
  public val op_tok = op_tok
  public val right = right
  public val pos_start = pos_start
  public val pos_end = pos_end

  override fun <R> accept(visitor : Visitor<R>, context : Context) : R {
    return visitor.visit(this, context)
  }

  override fun toString() : String {
        return "(${this.left.toString()}, ${this.op_tok.toString()}, ${this.right.toString()})"
  }
}

class UnaryOpNode(op_tok : Token, factor : Node, pos_start : Position, pos_end : Position) : Node {
  public val op_tok = op_tok
  public val factor = factor
  public val pos_start = pos_start
  public val pos_end = pos_end

  override fun <R> accept(visitor : Visitor<R>, context : Context) : R {
    return visitor.visit(this, context)
  }

  override fun toString() : String {
        return "(${this.op_tok.toString()} ${this.factor.toString()})"
  }
}

class PrevNode(expression : Node, pos_start : Position, pos_end : Position) : Node {
  public val expression = expression
  public val pos_start = pos_start
  public val pos_end = pos_end

  override fun <R> accept(visitor : Visitor<R>, context : Context) : R {
    return visitor.visit(this, context)
  }

  override fun toString() : String {
        return "prev ${this.expression.toString()}"
  }

}

class VarAssignNode(identifier : Token, expr : Node, is_topLevel : Boolean, pos_start : Position, pos_end : Position) : Node {
  public val identifier = identifier
  public val expr = expr
  public val is_topLevel = is_topLevel
  public val pos_start = pos_start
  public val pos_end = pos_end

  override fun <R> accept(visitor : Visitor<R>, context : Context) : R {
    return visitor.visit(this, context)
  }

  override fun toString() : String {
        return "(${this.identifier.toString()} = ${this.expr.toString()})"
  }
}

class VarAccessNode(identifier : Token, pos_start : Position, pos_end : Position) : Node {
  public val identifier = identifier
  public val pos_start = pos_start
  public val pos_end = pos_end

  override fun <R> accept(visitor : Visitor<R>, context : Context) : R {
    return visitor.visit(this, context)
  }

  override fun toString() : String {
        return "(${this.identifier.value})"
  }
}

class IfNode(condition : Node, if_block : Node, else_block : Node, should_return : Boolean, pos_start : Position, pos_end : Position) : Node {
  public val condition = condition
  public val if_block = if_block
  public val else_block = else_block
  public val should_return = should_return
  public val pos_start = pos_start
  public val pos_end = pos_end

  override fun <R> accept(visitor : Visitor<R>, context : Context) : R {
    return visitor.visit(this, context)
  }

  override fun toString() : String {
        return "if (${this.condition.toString()}) then ${this.if_block.toString()} else ${this.else_block.toString()}"
  }
}

class FunDefNode(name : String, arg_names : MutableList<Token>, body_node : Node, should_auto_return : Boolean, pos_start : Position, pos_end : Position) : Node {
  public val name = name
  public val arg_names = arg_names
  public val body_node = body_node
  public val should_auto_return = should_auto_return
  public val pos_start = pos_start
  public val pos_end = pos_end

  override fun <R> accept(visitor : Visitor<R>, context : Context) : R {
    return visitor.visit(this, context)
  }

  override fun toString() : String {
        return "${this.name}(${this.arg_names}) { ${this.body_node.toString()} }"
  }
}

class FunDeclNode(name : String, body : Node, pos_start : Position, pos_end : Position) : Node {
  public val name = name
  public val body = body
  public val pos_start = pos_start
  public val pos_end = pos_end

  override fun <R> accept(visitor : Visitor<R>, context : Context) : R {
    return visitor.visit(this, context)
  }

  override fun toString() : String {
        return "fun ${this.name}"
  }
}

class FunCallNode(callee : Node, args : MutableList<Node>, pos_start : Position, pos_end : Position) : Node {
  public val callee = callee
  public val args = args
  public val pos_start = pos_start
  public val pos_end = pos_end

  override fun <R> accept(visitor : Visitor<R>, context : Context) : R {
    return visitor.visit(this, context)
  }

  override fun toString() : String {
        return "${this.callee.toString()}(${this.args})"
  }
}

class ReturnNode(expr : Node, pos_start : Position, pos_end : Position) : Node {
  public val expr = expr
  public val pos_start = pos_start
  public val pos_end = pos_end

  override fun <R> accept(visitor : Visitor<R>, context : Context) : R {
    return visitor.visit(this, context)
  }

  override fun toString() : String {
        return "return ${this.expr.toString()})"
  }
}

class WhileNode(condition : Node, while_block : Node, pos_start : Position, pos_end : Position) : Node {
  public val condition = condition
  public val statement = while_block
  public val pos_start = pos_start
  public val pos_end = pos_end

  override fun <R> accept(visitor : Visitor<R>, context : Context) : R {
    return visitor.visit(this, context)
  }

  override fun toString() : String {
        return "while ${this.condition.toString()}) { ${this.statement.toString()} }"
  }
}

class ForNode(identifier : Token, list_expr : Node, body : Node, is_expr : Boolean, filter : Node?, pos_start : Position, pos_end : Position) : Node {
  public val identifier = identifier
  public val list_expr = list_expr
  public val body = body
  public val is_expr = is_expr
  public val pos_start = pos_start
  public val pos_end = pos_end
  public val filter : Node = if (filter == null) VarAccessNode(Token(TT_IDENTIFIER, "true", this.pos_start, this.pos_end), this.pos_start, this.pos_end) else filter

  override fun <R> accept(visitor : Visitor<R>, context : Context) : R {
    return visitor.visit(this, context)
  }

  override fun toString() : String {
        return "for (${this.identifier.toString()} in ${this.list_expr.toString()}) { ${this.body.toString()} }"
  }
}

class EncapNode(statements : Node, encap_name: String, is_sencap : Boolean, parent : Node?, pos_start : Position, pos_end : Position) : Node {
  public val statements = statements
  public val encap_name = encap_name
  public val is_sencap = is_sencap
  public val parent = parent
  public val pos_start = pos_start
  public val pos_end = pos_end

  override fun <R> accept(visitor : Visitor<R>, context : Context) : R {
    return visitor.visit(this, context)
  }

  override fun toString() : String {
        return "encap ${this.statements}"
  }
}

class EmptyNode() : Node {
  override fun <R> accept(visitor : Visitor<R>, context : Context) : R {
    return visitor.visit(this, context)
  }

  override fun toString() : String {
        return "Empty"
  }
}
val emptyNode = EmptyNode()

class ParseResult {
  public var error : Error? = null
  public var node : Node = emptyNode
  public var advance_count = 0
  public var to_reverse_count = 0
  public var last_registered_advance_count : Int? = null

  fun register_advancement(){
        this.advance_count += 1
    }

  fun register(res : ParseResult) : Node {
        this.last_registered_advance_count = res.advance_count
        this.advance_count += res.advance_count
        if (res.error != null) { this.error = res.error }
        return res.node
    }

  fun try_register(res : ParseResult) : Node {
        if (res.error != null) {
            this.to_reverse_count = res.advance_count
            return emptyNode
          }
        return this.register(res)
    }

  fun success(node : Node) : ParseResult {
      this.node = node
      return this
    }

  fun failure(error : Error?) : ParseResult {
      if (!(this.error != null) || this.advance_count == 0){
          this.error = error
      }
      return this
    }
}

class Parser(tokens : MutableList<Token>){
  public val tokens = tokens
  public var tok_idx = -1
  public var next_tok_idx = 0
  public var current_tok : Token? = null
  public var next_tok : Token? = null
  init {
    this.advance()
  }

  fun update_current_tok(){
      if (this.tok_idx >= 0 && this.tok_idx<this.tokens.size){
          this.current_tok = this.tokens[this.tok_idx]
      }
      if (this.next_tok_idx >= 0 && this.next_tok_idx<this.tokens.size){
          this.next_tok = this.tokens[this.next_tok_idx]
      }
  }

  fun advance(){
        this.tok_idx += 1
        this.next_tok_idx += 1
        this.update_current_tok()
  }

  fun reverse(amount : Int = 1) : Token? {
        this.tok_idx -= amount
        this.update_current_tok()
        return this.current_tok
  }

  fun parse() : ParseResult {
    val res = this.declarations()
    if (res.error == null && this.current_tok!!.type_ != TT_EOF){
      return res.failure(InvalidSyntaxError(
          this.current_tok!!.pos_start, this.current_tok!!.pos_end,
          "Expected '*', '/', '+', or '-'"
        ))
    }
    return res
  }

  fun declarations() : ParseResult {
    val res = ParseResult()
    val declarations = mutableListOf<Node>()
    val pos_start = this.current_tok!!.pos_start.copy()
    while (this.current_tok!!.type_ != TT_EOF){
      while (this.current_tok!!.type_ == TT_NEWLINE){
        this.advance()
      }
      if (this.current_tok!!.type_ == TT_EOF) { break }
      declarations.add(res.register(this.declaration()))
      if (res.error != null) { return res }
    }
    return res.success(StatementsNode(declarations, true, pos_start, this.current_tok!!.pos_end))
  }

  fun declaration() : ParseResult {
    val res = ParseResult()
    val pos_start = this.current_tok!!.pos_start.copy()
    if (this.current_tok!!.type_ != TT_IDENTIFIER){
      return res.failure(InvalidSyntaxError(
        pos_start, this.current_tok!!.pos_end,
        "Expected an identifier"
        ))
    }
    val var_name = this.current_tok!!
    res.register_advancement()
    this.advance()
    if (this.current_tok!!.type_ != TT_EQUALS){
      return res.failure(InvalidSyntaxError(
        pos_start, this.current_tok!!.pos_end,
        "Expected '='"
        ))
    }
    res.register_advancement()
    this.advance()
    val stmnt = res.register(this.statement(true))
    if (res.error != null) { return res }
    return res.success(VarAssignNode(var_name, stmnt, true, pos_start, this.current_tok!!.pos_end.copy()))
  }

  fun statements(may_decl: Boolean) : ParseResult {
    val res = ParseResult()
    val statements = mutableListOf<Node>()
    val pos_start = this.current_tok!!.pos_start.copy()
    if (this.current_tok?.type_ != TT_RCURLY){
      return res.failure(InvalidSyntaxError(
        pos_start, this.current_tok!!.pos_end.copy(),
        "Expected beginning token"
        ))
    }
    this.advance()
    while (this.current_tok?.type_ == TT_NEWLINE){
        res.register_advancement()
        this.advance()
    }
    val statement = res.register(this.statement(true))
    if (res.error != null){ return res }
    statements.add(statement)
    while (this.current_tok?.type_ == TT_NEWLINE){
      while (this.current_tok?.type_ == TT_NEWLINE){
          res.register_advancement()
          this.advance()
      }
      if (this.current_tok?.type_ == TT_LCURLY){
        break
      }
      val stmnt = res.register(this.statement(true))
      if (res.error != null) { return res }
      statements.add(stmnt)
    }
    if (this.current_tok?.type_ != TT_LCURLY){
      return res.failure(InvalidSyntaxError(
        pos_start, this.current_tok!!.pos_end.copy(),
        "Expected ending token"
        ))
    }
    res.register_advancement()
    this.advance()
    return res.success(StatementsNode(statements, may_decl, pos_start, this.current_tok!!.pos_end.copy()))
  }

  fun statement(may_decl : Boolean) : ParseResult {
    val res = ParseResult()
    val pos_start = this.current_tok!!.pos_start.copy()
    if ((this.current_tok?.type_ == TT_IDENTIFIER) && (this.next_tok?.type_ == TT_EQUALS)){
      val var_name = this.current_tok!!
      res.register_advancement()
      this.advance()
      res.register_advancement()
      this.advance()
      val expr = res.register(this.expr())
      if (res.error != null) { return res }
      return res.success(VarAssignNode(var_name, expr, false, pos_start, this.current_tok!!.pos_end.copy()))
    } else if (this.current_tok!!.matches(TT_KEYWORD, "if")){
      val expr = res.register(if_statement())
      if (res.error != null) { return res }
      return res.success(expr)
    } else if (this.current_tok?.type_ == TT_RCURLY){
      val statements = res.register(this.statements(may_decl))
      if (res.error != null) { return res }
      return res.success(statements)
    } else if (this.current_tok!!.matches(TT_KEYWORD, "define")){
      res.register_advancement()
      this.advance()
      if (this.current_tok!!.type_ != TT_IDENTIFIER){
        return res.failure(InvalidSyntaxError(
          this.current_tok!!.pos_start.copy(), this.current_tok!!.pos_end.copy(),
          "Expected an identifier"
          ))
      }
      val identifier = this.current_tok!!.value!!
      res.register_advancement()
      this.advance()
      if (this.current_tok!!.type_ != TT_EQUALS){
        return res.failure(InvalidSyntaxError(
          this.current_tok!!.pos_start.copy(), this.current_tok!!.pos_end.copy(),
          "Expected '='"
          ))
      }
      res.register_advancement()
      this.advance()
      val expr = res.register(this.expr())
      if (res.error != null) { return res }
      return res.success(FunDefNode(identifier, mutableListOf<Token>(), expr, true, pos_start, this.current_tok!!.pos_end.copy()))
    } else if (this.current_tok!!.matches(TT_KEYWORD, "fun") && this.next_tok?.type_ == TT_IDENTIFIER){
      val fun_stmnt = res.register(this.fun_expr(true))
      if (res.error != null) { return res }
      return res.success(fun_stmnt)
    } else if (this.current_tok!!.matches(TT_KEYWORD, "while")){
      val while_stmnt = res.register(this.while_statement())
      if (res.error != null) { return res }
      return res.success(while_stmnt)
    } else if (this.current_tok!!.matches(TT_KEYWORD, "for")){
      val for_stmnt = res.register(this.for_statement())
      if (res.error != null) { return res }
      return res.success(for_stmnt)
    } else if (this.current_tok!!.matches(TT_KEYWORD, "return")){
      res.register_advancement()
      this.advance()
      val expr = res.try_register(this.expr())
      if (expr is EmptyNode){
        this.reverse(res.to_reverse_count)
      }
      return res.success(ReturnNode(expr, pos_start, this.current_tok!!.pos_end.copy()))
    }
    val expr = res.register(this.expr())
    if (res.error != null) { return res }
    return res.success(expr)
  }

  fun if_statement() : ParseResult {
    val res = ParseResult()
    val pos_start = this.current_tok!!.pos_start.copy()
    if (!this.current_tok!!.matches(TT_KEYWORD, "if")){
      return res.failure(InvalidSyntaxError(
        pos_start, this.current_tok!!.pos_end,
        "Expected the keyword 'if'"
        ))
    }
    res.register_advancement()
    this.advance()
    val condition = res.register(this.expr())
    if (res.error != null) { return res }
    val if_block = res.register(this.statement(false))
    if (res.error != null) { return res }
    var else_block : Node = emptyNode
    if (this.current_tok!!.matches(TT_KEYWORD, "else")){
      res.register_advancement()
      this.advance()
      else_block = res.register(this.statement(false))
      if (res.error != null) { return res }
    }
    return res.success(IfNode(condition, if_block, else_block, false, pos_start, this.current_tok!!.pos_end.copy()))
  }

  fun for_statement() : ParseResult {
    val res = ParseResult()
    val pos_start = this.current_tok!!.pos_start.copy()
    if (!this.current_tok!!.matches(TT_KEYWORD, "for")){
      return res.failure(InvalidSyntaxError(
        pos_start, this.current_tok!!.pos_end,
        "Expected the keyword 'for'"
        ))
    }
    res.register_advancement()
    this.advance()
    if (this.current_tok!!.type_ != TT_IDENTIFIER){
      return res.failure(InvalidSyntaxError(
        pos_start, this.current_tok!!.pos_end,
        "Expected an identifier"
        ))
    }
    val var_name = this.current_tok!!
    res.register_advancement()
    this.advance()
    if (!this.current_tok!!.matches(TT_KEYWORD, "in")){
      return res.failure(InvalidSyntaxError(
        pos_start, this.current_tok!!.pos_end,
        "Expected the keyword 'in'"
        ))
    }
    res.register_advancement()
    this.advance()
    val list_expr = res.register(this.expr())
    if (res.error != null) { return res }
    val loop_body = res.register(this.statement(false))
    return res.success(ForNode(var_name, list_expr, loop_body, false, null, pos_start, this.current_tok!!.pos_end.copy()))
  }

  fun while_statement() : ParseResult {
    val res = ParseResult()
    val pos_start = this.current_tok!!.pos_start.copy()
    if (!this.current_tok!!.matches(TT_KEYWORD, "while")){
      return res.failure(InvalidSyntaxError(
        pos_start, this.current_tok!!.pos_end,
        "Expected the keyword 'while'"
        ))
    }
    res.register_advancement()
    this.advance()
    val condition = res.register(this.expr())
    if (res.error != null) { return res }
    val while_block = res.register(this.statement(false))
    if (res.error != null) { return res }
    return res.success(WhileNode(condition, while_block, pos_start, this.current_tok!!.pos_end.copy()))
  }

  fun bin_op(func_a : () -> ParseResult, ops : List<String>, func_b : () -> ParseResult) : ParseResult {
    val res = ParseResult()
    var left = res.register(func_a())
    if (res.error != null) { return res }
    val pos_start = this.current_tok!!.pos_start.copy()
    while (ops.contains(this.current_tok!!.type_)){
      val op_tok = this.current_tok!!
      res.register_advancement()
      this.advance()
      val right = res.register(func_b())
      if (res.error != null){ return res }
      left = BinOpNode(left, op_tok, right, pos_start, this.current_tok!!.pos_end)
    }
    return res.success(left)
  }

  fun expr() : ParseResult {
    val res = ParseResult()
    val pos_start = this.current_tok!!.pos_start.copy()
    val expr = res.register(this.bin_op({this.comp_expr()}, listOf(TT_AND, TT_OR), {this.comp_expr()}))
    if (res.error != null) { return res }
    if (this.current_tok!!.matches(TT_KEYWORD, "if")){
      res.register_advancement()
      this.advance()
      val condition = res.register(this.expr())
      if (res.error != null) { return res }
      if (!(this.current_tok!!.matches(TT_KEYWORD, "else"))){
        return res.failure(InvalidSyntaxError(
          pos_start, this.current_tok!!.pos_end.copy(),
          "Expected 'else'"
          ))
      }
      res.register_advancement()
      this.advance()
      val else_expr = res.register(this.expr())
      if (res.error != null) { return res }
      return res.success(IfNode(condition, expr, else_expr, true, pos_start, this.current_tok!!.pos_end.copy()))
    } else if (this.current_tok!!.type_ == TT_COLON) {
      val args = mutableListOf<Node>()
      res.register_advancement()
      this.advance()
      val arg = res.register(this.expr())
      if (res.error != null) { return res }
      args.add(arg)
      while (this.current_tok!!.type_ == TT_COMMA){
        res.register_advancement()
        this.advance()
        val arg = res.register(this.expr())
        if (res.error != null) { return res }
        args.add(arg)
      }
      return res.success(FunCallNode(expr, args, pos_start, this.current_tok!!.pos_end.copy()))
    } else if (this.current_tok!!.type_ == TT_NOT){
      res.register_advancement()
      this.advance()
      return res.success(FunCallNode(expr, mutableListOf<Node>(), pos_start, this.current_tok!!.pos_end.copy()))
    } else if (this.current_tok!!.matches(TT_KEYWORD, "for")) {
      res.register_advancement()
      this.advance()
      if (this.current_tok!!.type_ != TT_IDENTIFIER){
        return res.failure(InvalidSyntaxError(
          this.current_tok!!.pos_start, this.current_tok!!.pos_end,
          "Expected an identifier"
          ))
      }
      val identifier = this.current_tok!!
      res.register_advancement()
      this.advance()
      if (!this.current_tok!!.matches(TT_KEYWORD, "in")){
        return res.failure(InvalidSyntaxError(
          this.current_tok!!.pos_start, this.current_tok!!.pos_end,
          "Expected the keyword 'in'"
          ))
      }
      res.register_advancement()
      this.advance()
      val list_expr = res.register(this.expr())
      if (res.error != null) { return res }
      var condition : Node? = null
      if (this.current_tok!!.type_ == TT_ARROW){
        res.register_advancement()
        this.advance()
        if (!this.current_tok!!.matches(TT_KEYWORD, "if")){
          return res.failure(InvalidSyntaxError(
            this.current_tok!!.pos_start, this.current_tok!!.pos_end,
            "Expected the keyword 'if'"
            ))
        }
        res.register_advancement()
        this.advance()
        condition = res.register(this.expr())
        if (res.error != null) { return res }
      }
      return res.success(ForNode(identifier, list_expr, expr, true, condition, pos_start, this.current_tok!!.pos_end.copy()))
    }
    return res.success(expr)
  }

  fun comp_expr() : ParseResult {
    val res = ParseResult()
    val node = res.register(this.bin_op({this.arith_expr()}, listOf(TT_EE, TT_NE, TT_GT, TT_GTE, TT_LT, TT_LTE), {this.arith_expr()}))
    if (res.error != null) { return res }
    return res.success(node)
  }

  fun arith_expr() : ParseResult {
    return this.bin_op({this.term()}, listOf(TT_PLUS, TT_MINUS), {this.term()})
  }

  fun term() : ParseResult {
    return this.bin_op({this.factor()}, listOf(TT_MUL, TT_DIV), {this.factor()})
  }

  fun factor() : ParseResult {
    return this.bin_op({this.atom()}, listOf(TT_DARROW), {this.atom()})
  }

  fun atom() : ParseResult {
    val res = ParseResult()
    val pos_start = this.current_tok!!.pos_start.copy()
    if (listOf(TT_PLUS, TT_MINUS).contains(this.current_tok!!.type_)){
      val unary_op = this.current_tok!!
      res.register_advancement()
      this.advance()
      val factor = res.register(this.atom())
      if (res.error != null) { return res }
      return res.success(UnaryOpNode(unary_op, factor, pos_start, this.current_tok!!.pos_end))
    } else if (this.current_tok?.type_ == TT_NOT){
        val unaryOp = this.current_tok!!
        res.register_advancement()
        this.advance()
        val expr = res.register(this.comp_expr())
        if (res.error != null) { return res }
        return res.success(UnaryOpNode(unaryOp, expr, pos_start, this.current_tok!!.pos_end.copy()))
    } else if (this.current_tok!!.type_ == TT_NUMBER){
        val pos_end = this.current_tok!!.pos_end.copy()
        val value = this.current_tok!!.value!!
        res.register_advancement()
        this.advance()
        return res.success(NumberNode(value, pos_start, pos_end))
    } else if (this.current_tok!!.type_ == TT_STRING){
        val pos_end = this.current_tok!!.pos_end.copy()
        val value = this.current_tok!!.value!!
        res.register_advancement()
        this.advance()
        return res.success(StringNode(value, pos_start, pos_end))
    } else if (this.current_tok!!.type_ == TT_IDENTIFIER){
        val pos_end = this.current_tok!!.pos_end.copy()
        val id_tok = this.current_tok!!
        res.register_advancement()
        this.advance()
        return res.success(VarAccessNode(id_tok, pos_start, pos_end))
    } else if (this.current_tok!!.matches(TT_KEYWORD, "fun")){
        val expr = res.register(this.fun_expr(false))
        if (res.error != null) { return res }
        return res.success(expr)
    } else if (this.current_tok!!.matches(TT_KEYWORD, "prev")){
        res.register_advancement()
        this.advance()
        val expr = res.register(this.expr())
        if (res.error != null) { return res }
        return res.success(PrevNode(expr, pos_start, this.current_tok!!.pos_end))
    } else if (this.current_tok!!.matches(TT_KEYWORD, "encap") || this.current_tok!!.matches(TT_KEYWORD, "sencap")){
        var encap_name = ""
        var parent : Node? = null
        val is_sencap = this.current_tok!!.value!! == "sencap"
        res.register_advancement()
        this.advance()
        if (this.current_tok!!.type_ != TT_RCURLY){
          return res.failure(InvalidSyntaxError(
            this.current_tok!!.pos_start.copy(), this.current_tok!!.pos_end.copy(),
            "Expected a '{'"
            ))
        }
        val statements = res.register(this.statement(true))
        if (res.error != null) { return res }
        if (this.current_tok!!.matches(TT_KEYWORD, "as")){
          res.register_advancement()
          this.advance()
          if (this.current_tok!!.type_ != TT_IDENTIFIER){
            return res.failure(InvalidSyntaxError(
              this.current_tok!!.pos_start, this.current_tok!!.pos_end,
              "Expected an identifier"
              ))
          }
          encap_name = this.current_tok!!.value!!
          res.register_advancement()
          this.advance()
          if (this.current_tok!!.type_ == TT_COLON){
            res.register_advancement()
            this.advance()
            if (this.current_tok!!.type_ != TT_IDENTIFIER){
              return res.failure(InvalidSyntaxError(
                pos_start, this.current_tok!!.pos_end,
                "Expected an identifier"
                ))
            }
            parent = res.register(this.expr())
            if (res.error != null) { return res }
          }
        }
        return res.success(EncapNode(statements, encap_name, is_sencap, parent, pos_start, this.current_tok!!.pos_end.copy()))
    } else if (this.current_tok!!.type_ == TT_RSQUARE){
        val expr = res.register(this.list_expr())
        if (res.error != null) { return res }
        return res.success(expr)
    } else if (this.current_tok!!.type_ == TT_RPAREN){
      res.register_advancement()
      this.advance()
      val expr = res.register(this.expr())
      if (res.error != null) { return res }
      if (this.current_tok!!.type_ != TT_LPAREN){
        return res.failure(InvalidSyntaxError(
            pos_start, this.current_tok!!.pos_end,
            "Expected ')'"
          ))
      }
      res.register_advancement()
      this.advance()
      return res.success(expr)
    }
    return res.failure(InvalidSyntaxError(
        pos_start, this.current_tok!!.pos_end,
        "Expected NUMBER, IDENTIFIER, or '('"
      ))
  }

  fun list_expr() : ParseResult {
    val res = ParseResult()
    val elements = mutableListOf<Node>()
    val pos_start = this.current_tok!!.pos_start.copy()
    if (this.current_tok!!.type_ != TT_RSQUARE){
      return res.failure(InvalidSyntaxError(
        pos_start,  this.current_tok!!.pos_end,
        "Expected '['"
        ))
    }
    res.register_advancement()
    this.advance()
    if (this.current_tok!!.type_ != TT_LSQUARE){
      val first_element = res.register(this.expr())
      if (res.error != null) { return res }
      elements.add(first_element)
      while (this.current_tok!!.type_ == TT_COMMA){
        res.register_advancement()
        this.advance()
        val element = res.register(this.expr())
        if (res.error != null) { return res }
        elements.add(element)
      }
    }
    if (this.current_tok!!.type_ != TT_LSQUARE){
      return res.failure(InvalidSyntaxError(
        pos_start,  this.current_tok!!.pos_end,
        "Expected ']'"
        ))
    }
    res.register_advancement()
    this.advance()
    return res.success(ListNode(elements, pos_start, this.current_tok!!.pos_end.copy()))
  }

  fun fun_expr(stmnt : Boolean) : ParseResult {
    val res = ParseResult()
    val pos_start = this.current_tok!!.pos_start.copy()
    var name = "<anonymous>"
    val arg_names = mutableListOf<Token>()
    var body_node : Node
    var body = false
    var should_auto_return = false
    if (!(this.current_tok!!.matches(TT_KEYWORD, "fun"))){
      return res.failure(InvalidSyntaxError(
        pos_start,  this.current_tok!!.pos_end,
        "Expected the keyword 'fun'"
        ))
    }
    res.register_advancement()
    this.advance()
    if (this.current_tok!!.type_ == TT_IDENTIFIER){
      if (!stmnt){
        return res.failure(InvalidSyntaxError(
          pos_start,  this.current_tok!!.pos_end,
          "Lambda expressions are not named"
          ))
      }
      name = this.current_tok!!.value!!
      res.register_advancement()
      this.advance()
    }

    if (this.current_tok!!.type_ == TT_COLON){
      body = true
      res.register_advancement()
      this.advance()
      if (this.current_tok!!.type_ != TT_IDENTIFIER){
        return res.failure(InvalidSyntaxError(
          pos_start,  this.current_tok!!.pos_end,
          "Expected an identifier"
          ))
      }
      arg_names.add(this.current_tok!!)
      res.register_advancement()
      this.advance()
      while (this.current_tok!!.type_ == TT_COMMA){
        res.register_advancement()
        this.advance()
        if (this.current_tok!!.type_ != TT_IDENTIFIER){
          return res.failure(InvalidSyntaxError(
            pos_start,  this.current_tok!!.pos_end,
            "Expected an identifier"
            ))
        }
        arg_names.add(this.current_tok!!)
        res.register_advancement()
        this.advance()
      }
    }
    if (this.current_tok!!.type_ == TT_ARROW){
      should_auto_return = true
      res.register_advancement()
      this.advance()
      body_node = res.register(this.expr())
    } else if (body) {
      body_node = res.register(this.statement(true))
    } else {
      body_node = VarAccessNode(Token("null", null, pos_start, this.current_tok!!.pos_end.copy()), pos_start, this.current_tok!!.pos_end.copy())
    }
    if (res.error != null) { return res }
    val pos_end = this.current_tok!!.pos_end.copy()
    return res.success(if (body) FunDefNode(name, arg_names, body_node, should_auto_return, pos_start, pos_end)
    else FunDeclNode(name, body_node, pos_start, pos_end))
  }

}

class SymbolTable(parent : SymbolTable? = null){
  public var symbol_table = mutableMapOf<String, Value>()
  public var parent = parent

  fun get(name : String) : Value? {
      var output = this.symbol_table[name]
      val parent = this.parent
      if ((output == null) && (parent != null)){
        output = parent.get(name)
      }
      return output
    }

  fun set(name : String, value : Value) : Value {
    this.symbol_table.put(name, value)
    return value
  }

  fun set_table(symbol_table : MutableMap<String, Value>) : SymbolTable {
    this.symbol_table = symbol_table
    return this
  }

  fun specContains(name : String) : Boolean {
    return this.symbol_table[name] != null
  }

  fun contains(name : String) : Pair<Boolean, SymbolTable?> {
    var got = this.symbol_table[name] != null
    var found_table = if (got) this else null
    val parent = this.parent
    if ((!got) && (parent != null)){
      val (found, ret_output) = parent.contains(name)
      if (found){
        got = found
        found_table = ret_output
      }
    }
    return Pair(got, found_table)
  }


  fun copy() : SymbolTable {
    val new_table = SymbolTable(this.parent)
    new_table.symbol_table = this.symbol_table.toMutableMap()
    return new_table
  }

  override fun toString() : String {
    return "${this.symbol_table.toString()} -> ${this.parent.toString()}"
  }
}

open class Context(display_name : String, symbol_table : SymbolTable, parent : Context? = null, parent_entry_pos : Position? = null){
  public val display_name = display_name
  public val id = context_count
  public val symbol_table = symbol_table
  public val parent = parent
  public val parent_entry_pos = parent_entry_pos

  init {
    context_count += 1
  }

  fun copy() : Context {
    val new_context = Context(this.display_name, this.symbol_table.copy(), this.parent, this.parent_entry_pos)
    return new_context
  }

  override fun toString() : String {
    return "<$display_name>"
  }
}

class ErrorContext() : Context("Error", SymbolTable(), null, null){}

val errorContext = ErrorContext()

var context_count = 0

open class Value() {
  open var pos_start : Position? = null
  open var pos_end : Position? = null
  open var context : Context? = null

  fun set_context(context : Context?) : Value {
    this.context = context
    return this
  }

  fun set_pos(pos_start : Position? = null, pos_end : Position? = null) : Value {
    this.pos_start = pos_start
    this.pos_end = pos_end
    return this
  }

  open fun add(other : Value) : Pair<Value, Error?> {
    return Pair(emptyValue, this.illegal_operation(other))
  }

  open fun sub(other : Value) : Pair<Value, Error?> {
    return Pair(emptyValue, this.illegal_operation(other))
  }

  open fun mul(other : Value) : Pair<Value, Error?> {
    return Pair(emptyValue, this.illegal_operation(other))
  }

  open fun div(other : Value) : Pair<Value, Error?> {
    return Pair(emptyValue, this.illegal_operation(other))
  }

  open fun gt(other : Value) : Pair<Value, Error?> {
    return Pair(emptyValue, this.illegal_operation(other))
  }

  open fun gte(other : Value) : Pair<Value, Error?> {
    return Pair(emptyValue, this.illegal_operation(other))
  }

  open fun lt(other : Value) : Pair<Value, Error?> {
    return Pair(emptyValue, this.illegal_operation(other))
  }

  open fun lte(other : Value) : Pair<Value, Error?> {
    return Pair(emptyValue, this.illegal_operation(other))
  }

  open fun ne(other : Value) : Pair<Value, Error?> {
    return Pair(emptyValue, this.illegal_operation(other))
  }

  open fun ee(other : Value) : Pair<Value, Error?> {
    return Pair(emptyValue, this.illegal_operation(other))
  }

  open fun is_true() : Boolean {
    return false
  }

  open fun execute(args : MutableList<Value>) : RTResult {
    return RTResult().failure(this.illegal_operation())
  }

  open fun not() : Pair<Value, Error?>{
    val bool = Bool(!this.is_true())
    return Pair(bool, null)
  }

  open fun and(other : Value) : Pair<Value, Error?> {
    val bool = Bool(this.is_true() && other.is_true()).set_pos(this.pos_end!!.copy(), this.pos_end!!.copy()).set_context(this.context)
    return Pair(bool, null)
  }

  open fun or(other : Value) : Pair<Value, Error?> {
    val bool = Bool(this.is_true() || other.is_true()).set_pos(this.pos_end!!.copy(), this.pos_end!!.copy()).set_context(this.context)
    return Pair(bool, null)
  }

  open fun copy() : Value{
    println("No copy method defined")
    return this
  }

  fun illegal_operation(other : Value? = null) : Error {
    return RTError(
      this.pos_start, if (other == null) this.pos_end else other.pos_end,
      this.context!!,
      "illegal operation"
    )
  }

}

class Empty() : Value(){}

val emptyValue = Empty()

open class Number(value : Float) : Value() {
  public val value : Float = value

  override fun add(other : Value) : Pair<Value, Error?> {
    if (other is Number) {
      val new_number = Number(this.value + other.value).set_pos(this.pos_end!!.copy(), this.pos_end!!.copy()).set_context(this.context)
      return Pair(new_number, null)
    }
    return Pair(emptyValue, this.illegal_operation(other))
  }

  override fun sub(other : Value) : Pair<Value, Error?> {
    if (other is Number) {
      val new_number = Number(this.value - other.value).set_pos(this.pos_end!!.copy(), this.pos_end!!.copy()).set_context(this.context)
      return Pair(new_number, null)
    }
    return Pair(emptyValue, this.illegal_operation(other))
  }

  override fun mul(other : Value) : Pair<Value, Error?> {
    if (other is Number) {
      val new_number = Number(this.value * other.value).set_pos(this.pos_end!!.copy(), this.pos_end!!.copy()).set_context(this.context)
      return Pair(new_number, null)
    }
    return Pair(emptyValue, this.illegal_operation(other))
  }

  override fun div(other : Value) : Pair<Value, Error?> {
    if (other is Number) {
      if (other.value == 0.00f){
        return Pair(emptyValue, RTError(
          this.pos_start, other.pos_end,
          this.context!!,
          "Division by zero"
        ))
      }
      val new_number = Number(this.value / other.value).set_pos(this.pos_end!!.copy(), this.pos_end!!.copy()).set_context(this.context)
      return Pair(new_number, null)
    }
    return Pair(emptyValue, this.illegal_operation(other))
  }

  override fun gt(other : Value) : Pair<Value, Error?> {
    if (other is Number) {
      val new_number = Bool(this.value > other.value).set_pos(this.pos_end!!.copy(), this.pos_end!!.copy()).set_context(this.context)
      return Pair(new_number, null)
    }
    return Pair(emptyValue, this.illegal_operation(other))
  }

  override fun gte(other : Value) : Pair<Value, Error?> {
    if (other is Number) {
      val new_number = Bool(this.value >= other.value).set_pos(this.pos_end!!.copy(), this.pos_end!!.copy()).set_context(this.context)
      return Pair(new_number, null)
    }
    return Pair(emptyValue, this.illegal_operation(other))
  }

  override fun lt(other : Value) : Pair<Value, Error?> {
    if (other is Number) {
      val new_number = Bool(this.value < other.value).set_pos(this.pos_end!!.copy(), this.pos_end!!.copy()).set_context(this.context)
      return Pair(new_number, null)
    }
    return Pair(emptyValue, this.illegal_operation(other))
  }

  override fun lte(other : Value) : Pair<Value, Error?> {
    if (other is Number) {
      val new_number = Bool(this.value <= other.value).set_pos(this.pos_end!!.copy(), this.pos_end!!.copy()).set_context(this.context)
      return Pair(new_number, null)
    }
    return Pair(emptyValue, this.illegal_operation(other))
  }

  override fun ne(other : Value) : Pair<Value, Error?> {
    if (other is Number) {
      val new_number = Bool(this.value != other.value).set_pos(this.pos_end!!.copy(), this.pos_end!!.copy()).set_context(this.context)
      return Pair(new_number, null)
    }
    return Pair(emptyValue, this.illegal_operation(other))
  }

  override fun ee(other : Value) : Pair<Value, Error?> {
    if (other is Number) {
      val new_number = Bool(this.value == other.value).set_pos(this.pos_end!!.copy(), this.pos_end!!.copy()).set_context(this.context)
      return Pair(new_number, null)
    }
    return Pair(emptyValue, this.illegal_operation(other))
  }

  override fun is_true() : Boolean {
    return this.value != 0f
  }

  override fun copy() : Value {
    val ret_copy = Number(this.value)
    ret_copy.set_pos(this.pos_start, this.pos_end)
    ret_copy.set_context(this.context)
    return ret_copy
  }

  override fun toString() : String {
    return "${this.value}"
  }
}

class Bool(value : Boolean) : Number(if (value) 1f else 0f){
  val state = value
  override fun toString() : String {
    val t = "true"
    val f = "false"
    return "${if (this.is_true()) t else f}"
  }

  override fun copy() : Value {
    val ret_copy = Bool(this.state)
    ret_copy.set_pos(this.pos_start, this.pos_end)
    ret_copy.set_context(this.context)
    return ret_copy
  }
}

class NullType() : Number(0f){
  override fun toString() : String {
    return "null"
  }

  override fun copy() : Value {
    val ret_copy = NullType()
    ret_copy.set_pos(this.pos_start, this.pos_end)
    ret_copy.set_context(this.context)
    return ret_copy
  }
}

open class Str(value : String) : Value() {
  public val value : String = value

  override fun add(other : Value) : Pair<Value, Error?> {
    if (other is Str) {
      val new_number = Str(this.value + other.value).set_pos(this.pos_end!!.copy(), this.pos_end!!.copy()).set_context(this.context)
      return Pair(new_number, null)
    }
    return Pair(emptyValue, this.illegal_operation(other))
  }

  override fun sub(other : Value) : Pair<Value, Error?> {
    if (other is Number) {
      val index = if (other.value < 0) (other.value + this.value.length).toInt() else (other.value).toInt()
      val new_string = Str(this.value.substring(index)).set_pos(this.pos_end!!.copy(), this.pos_end!!.copy()).set_context(this.context)
      return Pair(new_string, null)
    }
    return Pair(emptyValue, this.illegal_operation(other))
  }

  override fun mul(other : Value) : Pair<Value, Error?> {
    if (other is Number) {
      val new_number = Str(this.value.repeat(other.value.toInt())).set_pos(this.pos_end!!.copy(), this.pos_end!!.copy()).set_context(this.context)
      return Pair(new_number, null)
    }
    return Pair(emptyValue, this.illegal_operation(other))
  }

  override fun div(other : Value) : Pair<Value, Error?> {
    if (other is Number) {
      val index = if (other.value < 0) (other.value + this.value.length).toInt() else (other.value).toInt()
      if (this.value[index] != null){
        val item = Str(this.value[index].toString()).set_pos(this.pos_start!!, this.pos_end!!).set_context(this.context!!)
        return Pair(item, null)
      } else {
        return Pair(emptyValue, RTError(
          this.pos_start, this.pos_end,
          this.context!!,
          "List has ${this.value.length} elements as ${this.value} but ${other.value} was retrieved"
          ))
      }
    }
    return Pair(emptyValue, this.illegal_operation(other))
  }

  override fun ne(other : Value) : Pair<Value, Error?> {
    if (other is Str) {
      val new_number = Bool(this.value != other.value).set_pos(this.pos_end!!.copy(), this.pos_end!!.copy()).set_context(this.context)
      return Pair(new_number, null)
    }
    return Pair(emptyValue, this.illegal_operation(other))
  }

  override fun ee(other : Value) : Pair<Value, Error?> {
    if (other is Str) {
      val new_number = Bool(this.value == other.value).set_pos(this.pos_end!!.copy(), this.pos_end!!.copy()).set_context(this.context)
      return Pair(new_number, null)
    }
    return Pair(emptyValue, this.illegal_operation(other))
  }

  override fun is_true() : Boolean {
    return this.value.length != 0
  }

  override fun copy() : Value {
    val ret_copy = Str(this.value)
    ret_copy.set_pos(this.pos_start, this.pos_end)
    ret_copy.set_context(this.context)
    return ret_copy
  }

  override fun toString() : String {
    return "${this.value}"
  }
}

class list(elements : MutableList<Value>, scope : Context) : Value() {
    val value = elements
    var scope = scope

    init {
      this.set_scope(this.scope)
    }

  fun set_scope(scope : Context) {
    this.scope = scope
    for (elem in this.value){
      if (elem is Function){
        elem.set_scope(this.scope)
      }
    }
    return
  }

    override fun add(other : Value) : Pair<Value, Error?> {
      val new_list = mutableListOf<Value>()
      new_list.addAll(this.value)
      new_list.add(other)
      val new_list_obj = list(new_list, scope).set_pos(this.pos_end!!.copy(), this.pos_end!!.copy()).set_context(this.context)
      return Pair(new_list_obj, null)
    }

    override fun sub(other : Value) : Pair<Value, Error?> {
      if (other is Number) {
        val new_list = mutableListOf<Value>()
        new_list.addAll(this.value)
        val index = if (other.value < 0) (other.value + new_list.size).toInt() else (other.value).toInt()
        if (new_list.size > other.value){
          new_list.removeAt(index)
          val new_list_obj = list(new_list, scope).set_pos(this.pos_end!!.copy(), this.pos_end!!.copy()).set_context(this.context)
          return Pair(new_list_obj, null)
        }
      }
      return Pair(emptyValue, this.illegal_operation(other))
    }

    override fun mul(other : Value) : Pair<Value, Error?> {
      if (other is list) {
        val new_list = mutableListOf<Value>()
        new_list.addAll(this.value)
        new_list.addAll(other.value)
        val new_list_obj = list(new_list, scope).set_pos(this.pos_end!!.copy(), this.pos_end!!.copy()).set_context(this.context)
        return Pair(new_list_obj, null)
      }
      return Pair(emptyValue, this.illegal_operation(other))
    }

    override fun div(other : Value) : Pair<Value, Error?> {
      if (other is Number) {
        val index = if (other.value < 0) (other.value + this.value.size).toInt() else (other.value).toInt()
        if (this.value.size > index){
          val item = this.value[index]
          return Pair(item, null)
        } else {
          return Pair(emptyValue, RTError(
            this.pos_start, this.pos_end,
            this.context!!,
            "List has ${this.value.size} elements as ${this.value} but ${other.value} was retrieved"
            ))
        }
      }
      return Pair(emptyValue, this.illegal_operation(other))
    }

    fun truth_of_list(other : Value) : Boolean {
        val truth = mutableListOf<Boolean>()
        if (other is list){
          if (other.value.size != this.value.size) { return false }
          for ((index, element) in this.value.withIndex()){
              if ((0 <= index) && (index < other.value.size)) {
                  val (call, _) = this.value[index].ee(other.value[index])
                  if (call is Bool){
                    if (call.state){
                      truth.add(true)
                    } else {
                      break
                    }
                  } else {
                    break
                  }
              }
          }
          return (truth.size == this.value.size) && (truth.size == other.value.size)
        } else {
          return false
        }
      }

    override fun ee(other: Value) : Pair<Value, Error?> {
      return Pair(Bool(this.truth_of_list(other)).set_pos(this.pos_start, this.pos_end).set_context(this.context), null)
    }

    override fun ne(other: Value) : Pair<Value, Error?> {
      return Pair(Bool(!this.truth_of_list(other)).set_pos(this.pos_start, this.pos_end).set_context(this.context), null)
    }

    override fun is_true() : Boolean {
      return this.value.size > 0
    }

    override fun copy() : Value {
      val ret_copy = list(this.value, this.scope)
      ret_copy.set_pos(this.pos_start, this.pos_end)
      ret_copy.set_context(this.context)
      return ret_copy
    }

    override fun toString() : String {
      return "${this.value}"
    }
}

class ContextObj(map : MutableMap<String, Value>, types : MutableList<String>, parent_context : Context, pos_start : Position, pos_end : Position) : Value() {
    public val value = map
    public val types = types
    public val symbol_table = SymbolTable(parent_context.symbol_table).set_table(map)
    public val unused = this.set_pos(pos_start, pos_end)
    public val parent_context = parent_context
    public val sys_context = Context(random_str(), symbol_table, parent_context, this.pos_start!!)

    override fun add(other : Value) : Pair<Value, Error?> {
      if (other is ContextObj){
        val new_map = this.value.toMutableMap()
        new_map.putAll(other.value)
        val new_types = mutableListOf<String>()
        new_types.addAll(this.types)
        new_types.addAll(other.types)
        val new_map_obj = ContextObj(new_map, new_types, this.parent_context, this.pos_start!!, this.pos_end!!).set_pos(this.pos_end!!.copy(), this.pos_end!!.copy()).set_context(this.context)
        return Pair(new_map_obj, null)
      } else {
        return Pair(emptyValue, this.illegal_operation())
      }
    }

    override fun div(other : Value) : Pair<Value, Error?> {
      if (other is Str) {
        val value = this.sys_context.symbol_table.get(other.value)
        if (value != null){
          return Pair(value, null)
        } else {
          return Pair(nullValue, null)
        }
      }
      return Pair(emptyValue, this.illegal_operation(other))
    }

    fun truth_of_map(other: Value) : Boolean {
      var truth_value = 0
      if (other is ContextObj){
        for ((key, value) in this.value){
          val other_key = other.value[key]
          if (other_key != null){
            val (equality, _) = value.ee(other_key)
            if (equality is Bool){
              if (equality.value == 1.00f){
                truth_value += 1
              } else {
                break
              }
            }
          } else {
            break
          }
        }
        return (truth_value == this.value.keys.size) && (truth_value == other.value.keys.size)
      } else {
        return false
      }
      return false
    }

    override fun ee(other: Value) : Pair<Value, Error?> {
      return Pair(Bool(this.truth_of_map(other)).set_pos(this.pos_start, this.pos_end).set_context(this.context), null)
    }

    override fun ne(other: Value) : Pair<Value, Error?> {
      return Pair(Bool(!this.truth_of_map(other)).set_pos(this.pos_start, this.pos_end).set_context(this.context), null)
    }

    override fun is_true() : Boolean {
      return this.value.size > 0
    }

    override fun copy() : Value {
      val new_types = mutableListOf<String>()
      new_types.addAll(this.types)
      val ret_copy = ContextObj(this.value.toMutableMap(), new_types, parent_context.copy(), this.pos_start!!, this.pos_end!!)
      ret_copy.set_pos(this.pos_start, this.pos_end)
      ret_copy.set_context(this.context)
      return ret_copy
    }

    override fun toString() : String {
      return "<${if (this.types == mutableListOf<String>()) "Anonymous" else this.types[this.types.size - 1]} Instance>"
    }
}


open class BaseFunction(name : String, scope : Context, init : Boolean) : Value() {
  public val name = name
  public var scope = scope
  public var init = init

  fun generate_new_context() : Context {
    val new_symbol_table = SymbolTable(this.scope.symbol_table)
    val new_context = Context(this.name, new_symbol_table, this.scope, this.pos_start!!)
    return new_context
  }

  fun check_args(arg_names : MutableList<String>, args : MutableList<Value>) : RTResult {
    val res = RTResult()
    if (args.size > arg_names.size){
      return res.failure(RTError(
        this.pos_start, this.pos_end,
        this.context!!,
        "${args.size - arg_names.size} too many args passed into '${this.name}'"
      ))
    } else if (args.size < arg_names.size){
        return res.failure(RTError(
            this.pos_start, this.pos_end,
            this.context!!,
            "${args.size - arg_names.size} too few args passed into '${this.name}'"
          ))
    }
    return res.success(nullValue, this.scope)
  }

  fun populate_args(arg_names : MutableList<String>, args : MutableList<Value>, exec_ctx : Context){
    for (i in args.indices){
      val arg_name = arg_names[i]
      val arg_value = args[i]
      arg_value.set_context(exec_ctx)
      exec_ctx.symbol_table.set(arg_name, arg_value)
    }
  }

  fun check_and_populate_args(arg_names : MutableList<String>, args : MutableList<Value>, exec_ctx : Context) : RTResult {
    val res = RTResult()
    res.register(this.check_args(arg_names, args))
    if (res.should_return()) { return res }
    this.populate_args(arg_names, args, exec_ctx)
    return res.success(nullValue, this.scope)
  }

  open fun initialize(scope : Context){
    this.scope = scope
    this.init = true
  }

  fun set_scope(scope : Context){
    this.scope = scope
  }

}

class Function(name : String, body_node : Node, arg_names : MutableList<String>, scope : Context, should_auto_return : Boolean, init : Boolean = true) : BaseFunction(name, scope, init) {
  public var body_node = body_node
  public var arg_names = arg_names
  public var should_auto_return = should_auto_return

  override fun execute(args : MutableList<Value>) : RTResult {
    val res = RTResult()
    val interpreter = Interpreter()
    val exec_ctx = this.generate_new_context()
    res.register(this.check_and_populate_args(this.arg_names, args, exec_ctx))
    if (res.should_return()) { return res }
    val (value, _) = res.register(this.body_node.accept(interpreter, exec_ctx))
    if (res.should_return() && res.func_return_value is Empty) { return res }
    var ret_value : Value = if (this.should_auto_return) value else if (!(res.func_return_value is Empty)) res.func_return_value else nullValue
    return res.success(ret_value, exec_ctx)
  }

  override fun copy() : Function {
    val ret_copy : Function = Function(this.name, this.body_node, this.arg_names, this.scope, this.should_auto_return)
    ret_copy.set_context(this.context)
    ret_copy.set_pos(this.pos_start, this.pos_end)
    return ret_copy
  }

  fun initialize(body_node : Node, arg_names : MutableList<String>, scope : Context, should_auto_return : Boolean){
    this.body_node = body_node
    this.arg_names = arg_names
    this.scope = scope
    this.should_auto_return = should_auto_return
    this.init = true
  }

  override fun toString() : String {
    return "<${this.name}>"
  }

}

class RTResult {
  public var value : Value = emptyValue
  public var context : Context = errorContext
  public var error : Error? = null
  public var func_return_value : Value = emptyValue
  public var loop_should_continue = false
  public var loop_should_break = false

  fun reset(){
        this.value = emptyValue
        this.context = errorContext
        this.error = null
        this.func_return_value = emptyValue
        this.loop_should_continue = false
        this.loop_should_break = false
    }

  fun register(res : RTResult) : Pair<Value, Context> {
        if (res.error != null) { this.error = res.error }
        this.loop_should_continue = res.loop_should_continue
        this.func_return_value = res.func_return_value
        this.loop_should_break = res.loop_should_break
        return Pair(res.value, res.context)
    }

    fun success(value : Value, context : Context) : RTResult {
        this.reset()
        this.value = value
        this.context = context
        return this
    }

    fun success_return(value : Value) : RTResult {
        this.reset()
        this.func_return_value = value
        return this
    }

    fun success_continue() : RTResult {
        this.reset()
        this.loop_should_continue = true
        return this
    }

    fun success_break() : RTResult {
        this.reset()
        this.loop_should_break = true
        return this
    }

    fun failure(error : Error) : RTResult {
        this.reset()
        this.error = error
        return this
    }

    fun should_return() : Boolean {
        return (
            this.error != null ||
            (!(this.func_return_value is Empty)) ||
            this.loop_should_continue != false ||
            this.loop_should_break != false
        )
      }
}

fun emptyNodeList() : MutableList<Node> { return mutableListOf<Node>() }

fun funDeclBodyFactory(pos_start : Position, pos_end : Position) : Node {
  return VarAccessNode(Token(TT_IDENTIFIER, "null", pos_start, pos_end), pos_start, pos_end)
}

class FunDeclarator() : Visitor<MutableList<Node>> {
  override fun visit(node : NumberNode, context : Context) : MutableList<Node> {
    return emptyNodeList()
  }

  override fun visit(node : StringNode, context : Context) : MutableList<Node> {
    return emptyNodeList()
  }

  override fun visit(node : ListNode, context : Context) : MutableList<Node> {
    return emptyNodeList()
  }

  override fun visit(node : BinOpNode, context : Context) : MutableList<Node> {
    return emptyNodeList()
  }

  override fun visit(node : UnaryOpNode, context : Context) : MutableList<Node> {
    return emptyNodeList()
  }

  override fun visit(node : PrevNode, context : Context) : MutableList<Node> {
    return node.expression.accept(this, context)
  }

  override fun visit(node : VarAssignNode, context : Context) : MutableList<Node> {
    if (node.expr is FunDefNode){
      val identifier = node.identifier.value!!
      node.expr.body_node.accept(this, context)
      val body = funDeclBodyFactory(node.pos_start, node.pos_end)
      val declNode = FunDeclNode(identifier, body, node.pos_start, node.pos_end)
      return mutableListOf<Node>(declNode)
    }
    return node.expr.accept(this, context)
  }

  override fun visit(node : StatementsNode, context : Context) : MutableList<Node> {
    val allDecls = emptyNodeList()
    for (statement in node.statements){
        val decls = statement.accept(this, context)
        allDecls.addAll(decls)
    }
    if (node.may_decl){
      node.statements.addAll(0, allDecls)
      return emptyNodeList()
    }
    return allDecls
  }

  override fun visit(node : VarAccessNode, context : Context) : MutableList<Node> {
    return emptyNodeList()
  }

  override fun visit(node : IfNode, context : Context) : MutableList<Node> {
    val allDecls = emptyNodeList()
    allDecls.addAll(node.if_block.accept(this, context))
    allDecls.addAll(node.else_block.accept(this, context))
    return allDecls
  }

  override fun visit(node : FunDefNode, context : Context) : MutableList<Node> {
    val identifier = node.name
    val body = funDeclBodyFactory(node.pos_start, node.pos_end)
    node.body_node.accept(this, context)
    val declNode = FunDeclNode(identifier, body, node.pos_start, node.pos_end)
    return mutableListOf<Node>(declNode)
  }

  override fun visit(node : ReturnNode, context : Context) : MutableList<Node> {
    return emptyNodeList()
  }

  override fun visit(node: WhileNode, context : Context): MutableList<Node> {
    return node.statement.accept(this, context)
  }

  override fun visit(node: ForNode, context : Context): MutableList<Node> {
    return node.body.accept(this, context)
  }

  override fun visit(node : EncapNode, context : Context) : MutableList<Node> {
    return node.statements.accept(this, context)
  }

  override fun visit(node : FunCallNode, context : Context) : MutableList<Node> {
    return emptyNodeList()
  }

  override fun visit(node: FunDeclNode, context : Context) : MutableList<Node> {
    return emptyNodeList()
  }

  override fun visit(node : EmptyNode, context : Context) : MutableList<Node> {
      return emptyNodeList()
  }

}

class Interpreter() : Visitor<RTResult> {
  override fun visit(node : NumberNode, context : Context) : RTResult {
    return RTResult().success(Number(node.number.toFloat()).set_pos(node.pos_start, node.pos_end).set_context(context), context)
  }

  override fun visit(node : StringNode, context : Context) : RTResult {
    return RTResult().success(Str(node.string).set_pos(node.pos_start, node.pos_end).set_context(context), context)
  }

  override fun visit(node : ListNode, context : Context) : RTResult {
    val res = RTResult()
    val elements = mutableListOf<Value>()
    for (elem in node.elements){
      val (element, _) = res.register(elem.accept(this, context))
      if (res.should_return()){ return res }
      elements.add(element)
    }
    return res.success(list(elements, context.copy()).set_pos(node.pos_start, node.pos_end).set_context(context), context)
  }

  override fun visit(node : BinOpNode, context : Context) : RTResult {
    val res = RTResult()
    var result : Value
    var error : Error?
    when (node.op_tok.type_) {
      TT_PLUS -> {
        val (left, _) = res.register(node.left.accept(this, context))
        if (res.should_return()) { return res }
        val (right, _) = res.register(node.right.accept(this, context))
        if (res.should_return()) { return res }
        val (ret_value, ret_error) = left.add(right)
        result = ret_value
        error = ret_error

      }
      TT_MINUS -> {
        val (left, _) = res.register(node.left.accept(this, context))
        if (res.should_return()) { return res }
        val (right, _) = res.register(node.right.accept(this, context))
        if (res.should_return()) { return res }
        val (ret_value, ret_error) = left.sub(right)
        result = ret_value
        error = ret_error

      }
      TT_MUL -> {
        val (left, _) = res.register(node.left.accept(this, context))
        if (res.should_return()) { return res }
        val (right, _) = res.register(node.right.accept(this, context))
        if (res.should_return()) { return res }
        val (ret_value, ret_error) = left.mul(right)
        result = ret_value
        error = ret_error

      }
      TT_DIV -> {
        val (left, _) = res.register(node.left.accept(this, context))
        if (res.should_return()) { return res }
        val (right, _) = res.register(node.right.accept(this, context))
        if (res.should_return()) { return res }
        val (ret_value, ret_error) = left.div(right)
        result = ret_value
        error = ret_error

      }
      TT_GT -> {
        val (left, _) = res.register(node.left.accept(this, context))
        if (res.should_return()) { return res }
        val (right, _) = res.register(node.right.accept(this, context))
        if (res.should_return()) { return res }
        val (ret_value, ret_error) = left.gt(right)
        result = ret_value
        error = ret_error
      }
      TT_GTE -> {
        val (left, _) = res.register(node.left.accept(this, context))
        if (res.should_return()) { return res }
        val (right, _) = res.register(node.right.accept(this, context))
        if (res.should_return()) { return res }
        val (ret_value, ret_error) = left.gte(right)
        result = ret_value
        error = ret_error

      }
      TT_LT -> {
        val (left, _) = res.register(node.left.accept(this, context))
        if (res.should_return()) { return res }
        val (right, _) = res.register(node.right.accept(this, context))
        if (res.should_return()) { return res }
        val (ret_value, ret_error) = left.lt(right)
        result = ret_value
        error = ret_error

      }
      TT_LTE -> {
        val (left, _) = res.register(node.left.accept(this, context))
        if (res.should_return()) { return res }
        val (right, _) = res.register(node.right.accept(this, context))
        if (res.should_return()) { return res }
        val (ret_value, ret_error) = left.lte(right)
        result = ret_value
        error = ret_error

      }
      TT_EE -> {
        val (left, _) = res.register(node.left.accept(this, context))
        if (res.should_return()) { return res }
        val (right, _) = res.register(node.right.accept(this, context))
        if (res.should_return()) { return res }
        val (ret_value, ret_error) = left.ee(right)
        result = ret_value
        error = ret_error

      }
      TT_NE -> {
        val (left, _) = res.register(node.left.accept(this, context))
        if (res.should_return()) { return res }
        val (right, _) = res.register(node.right.accept(this, context))
        if (res.should_return()) { return res }
        val (ret_value, ret_error) = left.ne(right)
        result = ret_value
        error = ret_error

      }
      TT_AND -> {
        val (left, _) = res.register(node.left.accept(this, context))
        if (res.should_return()) { return res }
        val (right, _) = res.register(node.right.accept(this, context))
        if (res.should_return()) { return res }
        val (ret_value, ret_error) = left.and(right)
        result = ret_value
        error = ret_error

      }
      TT_OR -> {
        val (left, _) = res.register(node.left.accept(this, context))
        if (res.should_return()) { return res }
        val (right, _) = res.register(node.right.accept(this, context))
        if (res.should_return()) { return res }
        val (ret_value, ret_error) = left.or(right)
        result = ret_value
        error = ret_error

      }
      TT_DARROW -> {
        val (left, _) = res.register(node.left.accept(this, context))
        if (res.should_return()) { return res }
        if (left is ContextObj) {
          val (right, _) = res.register(node.right.accept(this, left.sys_context))
          if (res.should_return()) { return res }
          result = right
          error = null
        } else {
          result = emptyValue
          error = left.illegal_operation()
        }
      }
      else -> {
        val (left, _) = res.register(node.left.accept(this, context))
        if (res.should_return()) { return res }
        result = emptyValue
        error = left.illegal_operation()
      }
    }
    if (error != null) { return res.failure(error) }
    return res.success(result.set_pos(node.pos_start, node.pos_end).set_context(context), context)
  }

  override fun visit(node : UnaryOpNode, context : Context) : RTResult {
    val res = RTResult()
    val (factor, _) = res.register(node.factor.accept(this, context))
    if (res.should_return()) { return res }
    val (result, error) = when (node.op_tok.type_){
      TT_MINUS -> factor.mul(Number(-1.00f))
      TT_PLUS -> factor.mul(Number(1.00f))
      TT_NOT -> factor.not()
      else -> Pair(emptyValue, factor.illegal_operation())
    }

    if (error != null) { return res.failure(error) }
    return res.success(result.set_pos(node.pos_start, node.pos_end).set_context(context), context)
  }

  override fun visit(node : VarAssignNode, context : Context) : RTResult {
    val res = RTResult()
    val identifier = node.identifier.value!!
    var assignment_context = context
    val (expr, _) = res.register(node.expr.accept(this, assignment_context))
    val re_init_error = RTError(node.pos_start, node.pos_end, context, "Cannot re-initialize the variable '${identifier}'")
    if (res.error != null){ return res }
    if (context.symbol_table.specContains(identifier) || expr is ContextObj){
      val new_symbol_table = SymbolTable(assignment_context.symbol_table.copy())
      assignment_context = Context(identifier, new_symbol_table, assignment_context, node.pos_start)
    }
    val (contains, found_table) = assignment_context.symbol_table.contains(identifier)
    if (expr is Function){
      if (contains){
        val fun_value = found_table!!.get(identifier)
        if (fun_value is Function && !fun_value.init){
          fun_value.initialize(expr.body_node, expr.arg_names, expr.scope, expr.should_auto_return)
        } else if (fun_value is Function && fun_value.init && node.is_topLevel){
          return res.failure(re_init_error)
        }
      }
    } else if (contains && node.is_topLevel){
      return res.failure(re_init_error)
    }
    assignment_context.symbol_table.set(identifier, expr)
    if (expr is Function){
      expr.set_scope(assignment_context.copy())
    }

    if (expr is list){
      expr.set_scope(assignment_context.copy())
    }

    return res.success(expr, assignment_context)
  }

  override fun visit(node : StatementsNode, context : Context) : RTResult {
    val res = RTResult()
    val id_str = random_str()
    val new_symbol_table = SymbolTable(context.symbol_table)
    var last_context = Context(id_str, new_symbol_table, context, node.pos_start)
    var last_value : Value = falseValue
    for (statement in node.statements){
      val (new_value, new_context) = res.register(statement.accept(this, last_context))
      if (res.should_return()){ return res }
      if (!(statement is StatementsNode)){
        last_context = new_context
      }
      last_value = new_value
    }
    return res.success(last_value, last_context)
  }

  override fun visit(node : VarAccessNode, context : Context) : RTResult {
    val res = RTResult()
    val value = context.symbol_table.get(node.identifier.value!!)
    if (value == null){
      return res.failure(RTError(
        node.pos_start, node.pos_end,
        context,
        "Undefined variable ${node.identifier.value}"
        ))
    }
    val ret_value = value.copy().set_pos(node.pos_start, node.pos_end).set_context(context)
    return res.success(ret_value, context)
  }

  override fun visit(node : IfNode, context : Context) : RTResult {
    val res = RTResult()
    val (condition, _) = res.register(node.condition.accept(this, context))
    if (condition.is_true()){
      val (result, new_context) = res.register(node.if_block.accept(this, context))
      if (res.should_return()) { return res }
      return res.success(if (node.should_return) result else falseValue, new_context)
    }
    if (!(node.else_block is EmptyNode)){
      val (result, new_context) = res.register(node.else_block.accept(this, context))
      if (res.should_return()) { return res }
      return res.success(if (node.should_return) result else falseValue, new_context)
    }
    return res.success(falseValue, context)
  }

  override fun visit(node : FunDefNode, context : Context) : RTResult {
    val res = RTResult()
    val fun_name = node.name
    val body_node = node.body_node
    val arg_names : MutableList<String> = node.arg_names.map({x -> x.value!!}).toMutableList()
    val main_fun = Function(fun_name, body_node, arg_names, context, node.should_auto_return).set_context(context).set_pos(node.pos_start, node.pos_end)
    var assignment_context = context
    if (fun_name != "<anonymous>"){
      val (contains, found_table) = context.symbol_table.contains(fun_name)
      if (contains){
        val fun_value = found_table!!.get(fun_name)
        if (fun_value is Function && main_fun is Function && !fun_value.init){
          fun_value.initialize(main_fun.body_node, main_fun.arg_names, main_fun.scope, main_fun.should_auto_return)
        } else if (context.symbol_table.specContains(fun_name)){
          val new_symbol_table = SymbolTable(context.symbol_table)
          assignment_context = Context("--$fun_name", new_symbol_table, context, node.pos_start)
        }
      }
      assignment_context.symbol_table.set(fun_name, main_fun)
      if (main_fun is BaseFunction){
        main_fun.set_scope(assignment_context.copy())
      }
    }
    return res.success(main_fun, assignment_context)
  }

  override fun visit(node : FunCallNode, context : Context) : RTResult {
    val res = RTResult()
    val args = mutableListOf<Value>()
    var (callee, _) = res.register(node.callee.accept(this, context))
    if (res.should_return()) { return res }
    callee = callee.copy().set_pos(node.pos_start, node.pos_end).set_context(context)
    for (arg in node.args){
      val (arg_value, _) = res.register(arg.accept(this, context))
      if (res.should_return()) { return res }
      args.add(arg_value)
    }
    var (return_value, _) = res.register(callee.execute(args))
    if (res.should_return()) { return res }
    return_value = return_value.copy().set_pos(node.pos_start, node.pos_end).set_context(context)
    return res.success(return_value, context)
  }

  override fun visit(node : ReturnNode, context : Context) : RTResult {
    val res = RTResult()
    var value : Value
    if (!(node.expr is EmptyNode)){
      val (expr, _) = res.register(node.expr.accept(this, context))
      value = expr
      if (res.should_return()) { return res }
    } else {
      value = nullValue
    }
    return res.success_return(value)
  }

  override fun visit(node: WhileNode, context : Context): RTResult {
    val res = RTResult()
    var last_context = context
    var last_value : Value = nullValue
    while (true){
      val (condition, _) = res.register(node.condition.accept(this, last_context))
      if (res.should_return()) { return res }
      if (!(condition.is_true())) { break }
      val (ret_value, ret_context) = res.register(node.statement.accept(this, last_context))
      last_context = ret_context
      last_value = ret_value
      if (res.should_return()  && res.loop_should_break == false && res.loop_should_continue == false) { return res }
      if (res.loop_should_break){
        break
      }
      if (res.loop_should_continue){
        continue
      }
    }
    return res.success(last_value, last_context)
  }

  override fun visit(node : ForNode, context : Context) : RTResult {
      val res = RTResult()
      val elements = mutableListOf<Value>()
      val (list_expr, _) = res.register(node.list_expr.accept(this, context))
      if (res.error != null){ return res }
      if (list_expr is list){
        var last_context = context
        var last_value : Value = nullValue
        for (element in list_expr.value){
          val last_symbol_table = SymbolTable(last_context.symbol_table)
          last_context = Context(random_str(), last_symbol_table, last_context, node.pos_start)
          last_context.symbol_table.set(node.identifier.value!!, element)
          val (filter, _) = res.register(node.filter.accept(this, last_context))
          if (res.should_return()) { return res }
          if (filter.is_true()){
            val (new_value, new_context) = res.register(node.body.accept(this, last_context))
            if (res.error != null){ return res }
            last_context = new_context
            last_value = new_value
            if (node.is_expr){
              elements.add(last_value)
            }
          }
      }
      return res.success(if (node.is_expr) list(elements, last_context).set_pos(node.pos_start, node.pos_end).set_context(last_context) else last_value,
        if (node.is_expr) context else last_context)
    } else {
      return res.failure(RTError(
        node.pos_start, node.pos_end,
        context,
        "$list_expr must be a list"
        ))
    }
  }

  override fun visit(node: EncapNode, context: Context) : RTResult {
    fun backlink(chain: SymbolTable, link: SymbolTable) : SymbolTable {
      var current_point : SymbolTable? = chain
      while (current_point!!.parent != null){
        current_point = current_point.parent
      }
      current_point.parent = link
      return chain
    }
    val res = RTResult()
    var parent : Value = emptyValue
    if (node.parent != null){
      var (temp_parent, _) = res.register(node.parent.accept(this, context))
      parent = temp_parent
      if (res.error != null){ return res }
      if (!(parent is ContextObj)){
        return res.failure(RTError(
          node.pos_start, node.pos_end,
          context,
          "Encaps can only inherit from other Encaps"
          ))
      }
    }
    val (_, new_context) = res.register(node.statements.accept(this,
      if (parent is ContextObj) Context(random_str(), backlink(parent.sys_context.symbol_table, context.symbol_table), context, node.pos_start)
      else context
      )
    )
    if (res.should_return()) { return res }
    val hashmap = new_context.symbol_table.symbol_table.toMutableMap()
    var current_context = new_context
    var new_symbol_table = SymbolTable()
    if (node.is_sencap){
      var max_depth = 1000
      var current_depth = 0
      while (context.id != current_context.id){
        if (current_context.parent == null){ return res.failure(RTError(node.pos_start, node.pos_end, current_context, "Fuck, that wasn't supposed to happen")) }
        new_symbol_table.parent = current_context.symbol_table
        new_symbol_table.parent!!.parent = null
        current_context = current_context.parent!!
        current_depth += 1
        if (current_depth > max_depth){
          return res.failure(RTError(
            node.pos_start, node.pos_end,
            current_context,
            "Map depth of context nesting allowed inside Sencaps exceeded"
            ))
        }
      }
    }
    val encap_types = if (node.encap_name == "") mutableListOf<String>() else mutableListOf<String>(node.encap_name)
    if (parent is ContextObj){
      new_symbol_table = backlink(parent.sys_context.symbol_table, new_symbol_table)
    }
    if (node.is_sencap){
      current_context = Context(random_str(), new_symbol_table, null, null)
    }
    return res.success(ContextObj(hashmap, encap_types, current_context.copy(), node.pos_start, node.pos_end).set_pos(node.pos_start, node.pos_end).set_context(context), context)
  }

  override fun visit(node: PrevNode, context : Context) : RTResult {
    val res = RTResult()
    val parent_context = context.parent
    if (parent_context != null){
      val (value, _) = res.register(node.expression.accept(this, parent_context))
      if (res.error != null) { return res }
      return res.success(value, context)
    }
    return res.failure(RTError(
      node.pos_start, node.pos_end,
      context,
      "No parent context found"
      ))
  }

  override fun visit(node: FunDeclNode, context : Context) : RTResult {
    val res = RTResult()
    val main_fun = Function(node.name, node.body, mutableListOf<String>(), context.copy(), true, init = false).set_pos(node.pos_start, node.pos_end).set_context(context)
    var assignment_context = context
    if (context.symbol_table.specContains(node.name)){
      val new_symbol_table = SymbolTable(context.symbol_table)
      assignment_context = Context("--${node.name}", new_symbol_table, context, node.pos_start)
    }
    assignment_context.symbol_table.set(node.name, main_fun)
    if (main_fun is BaseFunction){
      main_fun.set_scope(assignment_context.copy())
    }
    return res.success(main_fun, assignment_context)
  }

  override fun visit(node : EmptyNode, context : Context) : RTResult {
      return RTResult()
  }

}

fun random_str() : String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..35)
        .map { allowedChars.random() }
        .joinToString("")
}

fun set_values(symbol_table : SymbolTable) : SymbolTable {
  symbol_table.set("true", trueValue)
  symbol_table.set("false", falseValue)
  symbol_table.set("null", nullValue)
  return symbol_table
}

val trueValue = Bool(true)
val falseValue = Bool(false)
val nullValue = NullType()
val global_symbol_table = set_values(SymbolTable())
var global_context = Context("<module>", global_symbol_table)

fun run(fn : String, txt : String) : Pair<String, Error?> {
  val lexer = Lexer(fn, txt)
  val (tokens, error) = lexer.generate_tokens()
  if (error != null){
    return Pair("", error)
  }
  val parser = Parser(tokens)
  val ast = parser.parse()
  if (ast.error != null){
    return Pair("", ast.error)
  }
  ast.node.accept(FunDeclarator(), global_context)
  val result = ast.node.accept(Interpreter(), global_context)
  if (result.error != null){
    return Pair("", result.error)
  }
  return Pair("${result.value}", null)
}

fun main(args: Array<String>){
  if (args.size == 1){
    val content = File(args[0]).readText(Charsets.UTF_8)
    val (res, error) = run("<module>", content)
    if (error != null){
      println(error.as_string())
    }
    println(res)
  } else {
    while (true){
      print("Imperal> ")
      val (res, error) = run("<module>", readLine().toString())
      if (error != null){
        println(error.as_string())
      }
      println(res)
    }
  }
}
