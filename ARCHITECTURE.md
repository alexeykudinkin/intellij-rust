We use Kotlin language for the plugin. If you can program in Java or Rust, you
should be able to read/write Kotlin right away. Learn more about [Kotlin].

See the IDEA platform [documentation][sdk-docs]. Of a particular interest are
the following sections:
  * custom language [tutorial][lang-tutorial]
  * custom language support [reference][lang-reference].

If you find any piece of SDK docs missing or unclear, do open an issue
at [YouTrack][sdk-YouTrack]. You can
also [contribute directly][sdk-contributing] to the plugin development docs.

It's also very inspirational to browse existing plugins. Check out [Erlang] and
[Go] plugins. There is also [plugin repository] and, of course, [Kotlin plugin].

[Kotlin]: https://kotlinlang.org/
[sdk-docs]: http://www.jetbrains.org/intellij/sdk/docs/

[lang-reference]: http://www.jetbrains.org/intellij/sdk/docs/reference_guide/custom_language_support.html
[lang-tutorial]: http://www.jetbrains.org/intellij/sdk/docs/tutorials/custom_language_support_tutorial.html

[sdk-YouTrack]: https://youtrack.jetbrains.com/issues/IJSDK
[sdk-contributing]: https://github.com/JetBrains/intellij-sdk-docs/blob/master/CONTRIBUTING.md

[Erlang]: https://github.com/ignatov/intellij-erlang
[Go]: https://github.com/go-lang-plugin-org/go-lang-idea-plugin
[plugin repository]: https://github.com/JetBrains/intellij-plugins
[Kotlin plugin]: https://github.com/JetBrains/kotlin

# Packages

The plugin is composed of three main packages: `org.rust.lang`, `org.rust.ide`
and `org.rust.cargo`.

The `lang` package is the heart of the plugin. It includes a parser for the Rust
language, machinery for connecting declaration and usages and a type inference
algorithm. Completion and go to declarations is built using the `lang` package.

The `cargo` package is used for integration with Cargo and rustup.

The `ide` package uses `cargo` and `lang` packages to provide useful
functionality for the user. It consists of numerous sub packages. Some of them
are

* `intentions`: actions that the user can invoke with `Alt+Enter`,
* `inspections`: warnings and quick fixes,
* `navigation.goto`: leverages `lang.core.stubs.index` to provide GoToSymbol
  action.
  
# Lexer

The lexer is specified in the `RustLexer.flex` file. Refer to the [JFlex]
documentation to learn how it works. There is `generateRustLexer` gradle task
which calls jflex and produces a Java class from this `.jflex` file. Lexer
rarely changes and is mostly done.

# Parser

The parser is generated from the BNF-like description of the language grammar in
the file `RustParser.bng`. We use Intellij-specific parser generator [Grammar
Kit]. The corresponding gralde task is `generateRustParser`.

Grammar Kit [documentation][GK-docs] is on GitHub. You can also use
<kbd>Ctrl+Q</kbd> shortcut on any attribute in `RustGrammar.bnf` to read its
documentation.

At the high level, Grammar Kit generates a hand-written recursive descent
backtracking parser, which employs Pratt parser technique for parsing
left-recursive fragments of grammar. So, if you squint really hard, the
generated parser in `RustParser.java` looks like the rustc own parser in
`libsyntax`.

Besides the parser itself, Grammar Kit also generates the AST classes. Take a
look at the `struct_item` rule in the `RustParser.bnf` file and at the
corresponding `org.rust.lang.core.psi.RustStructItemElement` interface. You'll
see that each element at the right hand side of the `struct_item` rule has the
corresponding accessor method the interface. `?` modifier will cause the
accessor to be nullable, and `*` or `+` will result in the accessor returning a
`List` of elements.

As the IDE often works with incomplete code, a good parser recovery is
mandatory. The parser recovery consists of two parts. 

Let's say that the user has typed `fn foo`. The parser must understand that this
is a function, despite the fact that argument list and body are missing. This is
handled with the `pin` attribute. For example, `pin = 'FN'` will cause parser to
produce a function AST node as soon as `fn` keyword is parsed. Consequently, the
accessor for the identifier in the AST interface will be nullable. 

The second part of parser recovery is token skipping. Suppose the user added `fn
foo` to some existing code and got

```
fn foo
struct Bar {
    f: f32
}
```

Here, the parser should parse `Bar` as a struct despite the fact that the
preceding function is incomplete. This is handled with the `recoverWhile`
attribute which specifies the tokens to skip after completing (successfully or
not!) some rule. For example, `!(FN | STRUCT)` would work for language where
each declaration starts either with `fn` or with `struct`.

[JFlex]: http://www.jflex.de/
[Grammar Kit]: https://github.com/JetBrains/Grammar-Kit
[GK-docs]: https://github.com/JetBrains/Grammar-Kit/blob/master/HOWTO.md


# PSI

The parser actually generates something called PSI (Program Structure Interface)
and not AST (Abstract Syntax Tree). You can think of PSI as an AST, but it is
more general. For one thing, it includes things which are usually omitted from
an AST: whitespace, comments and parenthesis. PSI is a facade for program
structure, which can have several implementations. Use **View PSI Structure of
Current File** action to explore PSI, or install the PSI viewer plugin and use
`Ctrl+Shift+Q` shortcut.

In the plugin, PSI is organized in a rather convoluted way because we need to
marry generated Java code and hand written Kotlin extensions. Each PSI element
has a Java interface, generated by the parser (`RustStructItemElement`) and a
Java class implementing the interface, also generated by the parser
(`RustStructItemElementImpl`). The generated implementation can inherit from a
hand-written Kotlin class (`RustStructItemImplMixin`). Mixins allow to add
custom logic to the generated classes. Another way to do this is to add an
extension method. You can't do everything with extensions because they are
dispatched statically can't override methods and implement interfaces.

Read more about parse and PSI in the [sdk documentation][psi-doc]
[psi-doc]: http://www.jetbrains.org/intellij/sdk/docs/reference_guide/custom_language_support/implementing_parser_and_psi.html

# Name resolution

PSI itself does not carry much semantic information. You need to connect
definitions of things with their usages to be able to do code completion and
navigation. This is handled by references. Some PSI elements can return a
special `RustReference` object from their `getReference` method. This
`RustReference` has `resolve` method, which will return the definition the
reference element refers to. The definitions usually implement
`RustNamedElement` interface. Here's an example

```
// This is function, functions are `NamedElement`s.
fn foo() {}

fn bar() {
    // This `foo` is actually a reference to the function defined above. 
    // So, `this_foo.reference.resolve()` will return `above_foo`
    foo()
}
```

The implementation of name resolution is different in Intellij-Rust and in
rustc. Compiler resolves the whole crate at once, walking the tree of modules in
the top-down fashion. Intellij-Rust lazily resolves names by walking the PSI
tree upwards from the reference. This allows to do resolve only in the file
currently opened in the editor and its dependencies, ignoring most of the
crates. The major chunk of resolve is handled by the `innerDeclarations` and
`outerDeclarations` functions. 

`innerDeclarations` takes a reference and walks the AST tree upwards, collecting
all the `let` declarations, function parameters, item definitions and other
"local" declarations. This is the function which does the work of resolving a
local variable or a first component `foo` of the `foo::bar::baz` path.

`outerDeclarations` takes a module and lists all items defined in it. It handles
resolution of all the subsequent components of the `foo::bar::baz` path.

These functions allow to list *all* visible declarations and are used in
completion as well.

The result of resolve is cached. The caches are flushed after every PSI
modification. That is, after you type a key in the editor, all the name
resolution info is forgotten and recalculated, but only for the currently opened
file. See `com.intellij.psi.util.CachedValuesManager` and
`com.intellij.psi.util.PsiModificationTracker` for the details about caching.

# Indexing

Intellij provides a powerful API for indexing source code. Roughly speaking, you
can build an arbitrary map from some keys to some values using all the source
code in the project and then query the map. The Intellij will make sure that the
mapping always stays fresh. The crucial restriction is that for each file the
mapping must be computed independently. That is, you can store a mapping from
struct names to struct definitions, but you can't map a struct to the
grandparent module. This restriction allows fast recalculation of the index:
only the part corresponding to the changed files needs to be flushed.

These indexes power go to class and go to symbol functionality. They are also
used during resolve to find the parent module for a file and to get the list of
`impl`s for a type.

But the main use of indexes is for building a stub tree. Stub tree is a
condensed AST, which includes information necessary for the resolve and nothing
more. That is, `struct`s and functions declarations are present in stubs, but
function bodies and local variables are omitted. Stubs are build from source
files during indexing and are stored in binary format. The cool thing is that
PSI can
dynamically
[switch](https://github.com/intellij-rust/intellij-rust/blob/1cc9e40248bd36e43cc016d008270d0e0f4d7f8a/src/main/kotlin/org/rust/lang/core/psi/impl/RustStubbedNamedElementImpl.kt#L27) between
stub based and AST based implementation. That means that a lot of resolve
operations do not need to actually parse the files. Instead, they use only stub
trees, which is much faster. Rust stubs are in `org.rust.lang.core.stubs`
package.


Read more about [indexing].

[indexing]: http://www.jetbrains.org/intellij/sdk/docs/basics/indexing_and_psi_stubs.html
