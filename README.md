# linker-parser: JavaCC on steroids.
Linker-parser is a FSM-backed non-recursive top-down LL(k) parser that uses Java language to define grammar rules. In other words, it accepts Java classes as grammar definitions and instantiates objects from these classes using text it parses as a template.

## But... Why?
I started this project out of frustration of working with javacc (*Java* *C*ompiler *C*ompiler), which was created decades ago and is very complicated to use. I also didn't want to deal with BNF notations as Java can be used to describe grammars on itself and I love Java :)

## Creating a grammar rule
Linker-parser grammar rules are defined as Java classes using a set of simple conventions:
* Each non-transient field of the class represents a token (other rule or a terminal);
* Terminal tokens defined using static String fields by setting their values to the token itself;
* Capture tokens defined as String fields with CapturePattern annotation;
* Token repititions defined as array fields of corresponding to the token type;
* Alternatives can be defined as fields of an interface type - each class that implements the interface will be processed as an alternative token; 
* Repetitions are always greedy;
* Repetition limits can be defined using `Limit` annotation;
* Optional fields marked with `OptionalToken` annotation;


For example a Java multiline comment can be defined as follows:
```java
public class MultilineComment implements Rule {
  private static final String OPEN_MARKER = "/*";
 
  @CapturePattern(until="\\*/");
  private String comment;
  
  private static final String CLOSE_MARKER = "*/";
}
```
Other examples are available in [Linker-Sail](https://github.com/dmitriic/lisa) project, most interstingly:
- [BinaryOperatorStatement](https://github.com/dmitriic/lisa/blob/master/src/main/java/com/onkiup/linker/sail/operator/BinaryOperatorStatement.java)
- [RuleInvocation](https://github.com/dmitriic/lisa/blob/master/src/main/java/com/onkiup/linker/sail/grammar/RuleInvocation.java)

## Creating a parser
Invoke `TokenGrammar.forClass(Class<? extends Rule> rule)` with your root token class as parameter.

## Parsing 
Invoking `TokenGrammar::parse(Reader source)` will read and parse the text from the source into a token and will return the resulting token as an object.

## Evaluating
Linker-parser will invoke `Rule::reevaluate` callback each time a token field is populated. 

[Linker-Sail](https://github.com/dmitriic/lisa) evaluator `Rule` definitions, for example, use that callback to test whether the token has been populated (`Rule::populated`) and then recalculate their result value and push it either to its subscriber (parent token), or in case of variable declaration/assignment -- pass that value into shared context which propagates this value to any tokens that subscribe to the variable.

## Left recursion
As any leftmost variation parser, Linker-parser is susceptible to infinite loops when processing alternatives that invoke themselves. Consider the following set of rules:

```java
public interface Token extends Rule { } 
public class Test implements Token {
  private Token token;
}
```
which is equivalent to:
```
A -> X
X -> A 
```
Classic LL(k) parser would not be able to handle these rules and fail by falling into infinite loop. Linker-parser deals with such situation by keeping a list of all tested alternative rules for current position and not re-testing rules that are in that list. The list is dropped every time parser changes its current position.

Alternatively, the order in which variations are tested can be manipulated by marking a variation with `AdjustPriority` annotation. Variations are tested in ascending order of their priority level, so variations with smaller priorities are tested first.

## Token Rotation
Left-recursive tokens pose another challenge when parsing nested statements like this one:
```
1 + 1 + 1
```
The problem here is that, as all captures are greedy, any token that defines a binary operator statement would first consume characters `1 + 1` and will be marked as populated, leaving unparsed characters ` + 1` that don't anymore match binary operator statement and are likely to cause parser to throw a SyntaxError. 

To resolve this issue, Linker-Parser will try to rotate children of failing PartialToken before discarding it. A child PartialToken can be rotated only if it satisfies all of these conditions:
* The PartialToken is populated
* The resulting token can be assigned to the first field of the PartialToken

Token rotations are similar to tree rotations when balancing BSTs: rotating PartialToken clones its state into a new PartialToken, resets its state and then advances to the second field by assigning created PartialToken its first field.

Rotations can also be attempted on root tokens upon parser unexpectedly hitting end of input.

Token rotations are always performed before testing if the token has any alternatives left and successful rotations prevent parser from advancing to the next possible alternative (as rotated token is an alternative on itself).

## Support
For any questions or issues -- please either open a github issue in this project or tweet directly at [chedim](http://twitter.com/chedim) and I will do my best to help you. It would help me a lot if you include definitions for your failing rules in the message ;-)

## Version History
* 0.6 - Major refactoring triggered by a design mistake in token rollback logic. 
  Known bug: this version may not report column/line position correctly
* 0.5 - Added left-recursion avoidance logic
* 0.3.1 - transient fields now will be ignored
* 0.3 
  major refactoring from recursive to stack-based algorithm
  Support for token repetitions
* 0.2.2 - first publicly available version
* -100 - my first parser, used at Politico to parse and normalize HTML for articles migrated from capitalnewyork.com to politico.com (no source code of that parser was used here, only experience) :)

## Development Roadmap
* Implement object pool for PartialTokens and TokenMatchers
* Investigate possibility for multi-threaded VariantToken processing
* Add support for Number terminals
* Add support for Enum terminals
