grammar AdvancedSearchQuery;

@header {
  package org.airsonic.player.service.search.parser;
}

query : expression;

expression
  : LPAREN expression RPAREN      # BracketExpression
  | expression (AND expression)+  # AndExpression
  | expression (OR expression)+   # OrExpression
  | NOT expression                # NotExpression
  | predicate (SPACE predicate)*  # PredicateExpression
  ;

predicate
  : field operator value       # OperatorPredicate
  | value LT field LT value    # BetweenPredicate
  ;

operator
  : EQ
  | FUZZEQ
  | REGEXP
  | LT
  | GT
  | LTE
  | GTE
  ;

field : UNQUOTED;

value : UNQUOTED | QUOTED;

AND        : [ ]* '&' [ ]* | [ ]+ 'AND' [ ]+;
OR         : [ ]* '|' [ ]* | [ ]+ 'OR' [ ]+;
NOT        : [ ]* '~' | 'NOT' [ ]+;
SPACE      : ' ';
UNQUOTED   : [a-zA-Z0-9]+;
QUOTED     : '"' (~["])* '"' | '\'' (~['])* '\''
  {
      String s = getText();
      setText(s.substring(1, s.length() - 1));
  };
TRIM       : [\t\r\n]+ -> skip;

EQ     : '=';
FUZZEQ : ':';
REGEXP : '::';
LT     : '<';
GT     : '>';
LTE    : '<=';
GTE    : '>=';
LPAREN : '(';
RPAREN : ')';
