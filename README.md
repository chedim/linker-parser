# linker-parser: JavaCC on steroids.
Linker-parser is a FSM-backed non-recursive top-down depth-first LL(k) parser that uses Java language to define grammar rules. 

## Creating a grammar rule
Linker-parser grammar rules are defined as Java classes using a set of simple conventions:
* Each field of the class represents a token (other rule or a terminal);
* Terminal tokens defined using static String fields by setting their values to the token itself;
* Capture tokens defined as String fields with CapturePattern annotation;
* Token repititions defined as array fields of corresponding to the token type;
* Alternatives can be defined as fields of an interface type - each class that implements the interface will be processed as an alternative token; 
* Repetitions are always greedy;
* Repetition limits can be defined using Limit annotation;

For example a Java miltiline comment can be defined as follows:
```java
public class MultilineComment implements Rule {
  private static final String OPEN_MARKER = "/*";
 
  @CapturePattern(until="([^\\\\)])\\*/", replacement="$1");
  private String comment;
  
  private static final String CLOSE_MARKER = "*/";
}
```
## Creating a parser
Invoke `TokenGrammar.forClass(Class<? extends Rule> rule)` with your root token class as parameter.

## Parsing 
Invoking `TokenGrammar::parse(Reader source)` will read and parse the text from the source into a token and will return the resulting token as an object.

## Transpiling/Compiling
Linker-parser provides support for transpiling into other languages via Rule::transpile hook. Currently this feature is limited to one target language.

## Evaluating
Passing a context object as second argument to `TokenGrammar::parse` will cause parser to pass this parameter to `Rule::apply` evaluation callback on each Rule as soon as the rule object is populated.

## Version History
* 0.3 
- major refactoring from recursive to stack-based algorithm
- Support for token repetitions
* 0.2.2 - first publicly available version
* -100 - my first parser, used at Politico to parse and normalize HTML for articles migrated from capitalnewyork.com to politico.com (no source code of that parser was used here, only experience) :)
