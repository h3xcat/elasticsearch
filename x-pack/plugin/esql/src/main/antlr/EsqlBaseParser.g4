/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

parser grammar EsqlBaseParser;

options {tokenVocab=EsqlBaseLexer;}

singleStatement
    : query EOF
    ;

query
    : sourceCommand                 #singleCommandQuery
    | query PIPE processingCommand  #compositeQuery
    ;

sourceCommand
    : explainCommand
    | fromCommand
    | rowCommand
    | metricsCommand
    | searchCommand
    | showCommand
    | metaCommand
    ;

processingCommand
    : evalCommand
    | inlinestatsCommand
    | limitCommand
    | lookupCommand
    | keepCommand
    | sortCommand
    | statsCommand
    | whereCommand
    | dropCommand
    | renameCommand
    | dissectCommand
    | grokCommand
    | enrichCommand
    | mvExpandCommand
    ;

whereCommand
    : WHERE booleanExpression
    ;

booleanExpression
    : NOT booleanExpression                                                      #logicalNot
    | valueExpression                                                            #booleanDefault
    | regexBooleanExpression                                                     #regexExpression
    | left=booleanExpression operator=AND right=booleanExpression                #logicalBinary
    | left=booleanExpression operator=OR right=booleanExpression                 #logicalBinary
    | valueExpression (NOT)? IN LP valueExpression (COMMA valueExpression)* RP   #logicalIn
    | valueExpression IS NOT? NULL                                               #isNull
    ;

regexBooleanExpression
    : valueExpression (NOT)? kind=LIKE pattern=string
    | valueExpression (NOT)? kind=RLIKE pattern=string
    ;

valueExpression
    : operatorExpression                                                                      #valueExpressionDefault
    | left=operatorExpression comparisonOperator right=operatorExpression                     #comparison
    ;

operatorExpression
    : primaryExpression                                                                       #operatorExpressionDefault
    | operator=(MINUS | PLUS) operatorExpression                                              #arithmeticUnary
    | left=operatorExpression operator=(ASTERISK | SLASH | PERCENT) right=operatorExpression  #arithmeticBinary
    | left=operatorExpression operator=(PLUS | MINUS) right=operatorExpression                #arithmeticBinary
    ;

primaryExpression
    : constant                                                                          #constantDefault
    | qualifiedName                                                                     #dereference
    | functionExpression                                                                #function
    | LP booleanExpression RP                                                           #parenthesizedExpression
    | primaryExpression CAST_OP dataType                                                #inlineCast
    ;

functionExpression
    : identifier LP (ASTERISK | (booleanExpression (COMMA booleanExpression)*))? RP
    ;

dataType
    : identifier                                                                        #toDataType
    ;

rowCommand
    : ROW fields
    ;

fields
    : field (COMMA field)*
    ;

field
    : booleanExpression
    | qualifiedName ASSIGN booleanExpression
    ;

fromCommand
    : FROM indexPattern (COMMA indexPattern)* metadata?
    ;

indexPattern
    : clusterString COLON indexString
    | indexString
    ;

clusterString
    : UNQUOTED_SOURCE
    ;

indexString
    : UNQUOTED_SOURCE
    | QUOTED_STRING
    ;

metadata
    : metadataOption
    | deprecated_metadata
    ;

metadataOption
    : METADATA UNQUOTED_SOURCE (COMMA UNQUOTED_SOURCE)*
    ;

deprecated_metadata
    : OPENING_BRACKET metadataOption CLOSING_BRACKET
    ;

metricsCommand
    : METRICS indexPattern (COMMA indexPattern)* aggregates=fields? (BY grouping=fields)?
    ;

evalCommand
    : EVAL fields
    ;

statsCommand
    : STATS stats=fields? (BY grouping=fields)?
    ;

inlinestatsCommand
    : INLINESTATS stats=fields (BY grouping=fields)?
    ;


qualifiedName
    : identifier (DOT identifier)*
    ;

qualifiedNamePattern
    : identifierPattern (DOT identifierPattern)*
    ;

qualifiedNamePatterns
    : qualifiedNamePattern (COMMA qualifiedNamePattern)*
    ;

identifier
    : UNQUOTED_IDENTIFIER
    | QUOTED_IDENTIFIER
    ;

identifierPattern
    : ID_PATTERN
    ;

constant
    : NULL                                                                              #nullLiteral
    | integerValue UNQUOTED_IDENTIFIER                                                  #qualifiedIntegerLiteral
    | decimalValue                                                                      #decimalLiteral
    | integerValue                                                                      #integerLiteral
    | booleanValue                                                                      #booleanLiteral
    | params                                                                            #inputParams
    | string                                                                            #stringLiteral
    | OPENING_BRACKET numericValue (COMMA numericValue)* CLOSING_BRACKET                #numericArrayLiteral
    | OPENING_BRACKET booleanValue (COMMA booleanValue)* CLOSING_BRACKET                #booleanArrayLiteral
    | OPENING_BRACKET string (COMMA string)* CLOSING_BRACKET                            #stringArrayLiteral
    ;

params
    : PARAM                        #inputParam
    | NAMED_OR_POSITIONAL_PARAM    #inputNamedOrPositionalParam
    ;

limitCommand
    : LIMIT INTEGER_LITERAL
    ;

sortCommand
    : SORT orderExpression (COMMA orderExpression)*
    ;

orderExpression
    : booleanExpression ordering=(ASC | DESC)? (NULLS nullOrdering=(FIRST | LAST))?
    ;

keepCommand
    :  KEEP qualifiedNamePatterns
    ;

dropCommand
    : DROP qualifiedNamePatterns
    ;

renameCommand
    : RENAME renameClause (COMMA renameClause)*
    ;

renameClause:
    oldName=qualifiedNamePattern AS newName=qualifiedNamePattern
    ;

dissectCommand
    : DISSECT primaryExpression string commandOptions?
    ;

grokCommand
    : GROK primaryExpression string
    ;

mvExpandCommand
    : MV_EXPAND qualifiedName
    ;

commandOptions
    : commandOption (COMMA commandOption)*
    ;

commandOption
    : identifier ASSIGN constant
    ;

booleanValue
    : TRUE | FALSE
    ;

numericValue
    : decimalValue
    | integerValue
    ;

decimalValue
    : (PLUS | MINUS)? DECIMAL_LITERAL
    ;

integerValue
    : (PLUS | MINUS)? INTEGER_LITERAL
    ;

string
    : QUOTED_STRING
    ;

comparisonOperator
    : EQ | NEQ | LT | LTE | GT | GTE
    ;

explainCommand
    : EXPLAIN subqueryExpression
    ;

subqueryExpression
    : OPENING_BRACKET query CLOSING_BRACKET
    ;

showCommand
    : SHOW INFO                                                           #showInfo
    ;

metaCommand
    : META FUNCTIONS                                                      #metaFunctions
    ;

enrichCommand
    : ENRICH policyName=ENRICH_POLICY_NAME (ON matchField=qualifiedNamePattern)? (WITH enrichWithClause (COMMA enrichWithClause)*)?
    ;

enrichWithClause
    : (newName=qualifiedNamePattern ASSIGN)? enrichField=qualifiedNamePattern
    ;

lookupCommand
    : LOOKUP tableName=indexPattern ON matchFields=qualifiedNamePatterns
    ;

searchCommand
    : SEARCH searchIndexPattern (COMMA searchIndexPattern)* (OPENING_BRACKET (PIPE searchSubCommand)* CLOSING_BRACKET)*
    ;

searchIndexPattern
    : clusterString COLON indexString
    | indexString
    ;

searchSubCommand
    : searchFilterCommand
    | searchLimitCommand
    | searchRankCommand
    | searchSortCommmand
    ;

searchFilterCommand
    : WHERE searchFilterExpression
    ;

searchFilterExpression
    : searchQueryExpression
    ;

searchLimitCommand
    : LIMIT INTEGER_LITERAL
    ;

searchRankCommand
    : RANK searchRankExpression
    ;

searchRankExpression
    : searchQueryExpression
    ;

searchSortCommmand
    : sortCommand
    ;

searchQueryExpression
    : NOT searchQueryExpression                                                     #searchLogicalNot
    | valueExpression                                                               #searchBooleanDefault
    | LP searchQueryExpression RP                                                   #searchParenthesizedExpression
    | SEARCH_EXPR_MATCH LP singleField=qualifiedName COMMA queryString=string RP    #searchMatchQuery
    | left=searchQueryExpression operator=AND right=searchQueryExpression           #searchLogicalBinary
    | left=searchQueryExpression operator=OR right=searchQueryExpression            #searchLogicalBinary
    ;
