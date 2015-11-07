# Rust IDE built using the IntelliJ Platform

[![Build Status Travis](https://img.shields.io/travis/alexeykudinkin/intellij-rust/master.svg?label=TravisCI)](https://travis-ci.org/alexeykudinkin/intellij-rust)
[![Build Status Appveyor](https://img.shields.io/appveyor/ci/alexeykudinkin/intellij-rust/master.svg?label=AppVeyor)](https://ci.appveyor.com/project/alexeykudinkin/intellij-rust/branch/master)
[![Join the chat at https://gitter.im/alexeykudinkin/intellij-rust](https://img.shields.io/badge/Gitter-Join%20Chat-blue.svg)](https://gitter.im/alexeykudinkin/intellij-rust?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) 

**BEWARE**

This is experimental implementation targeting bleeding-edge version of Rust language and (as may be assumed reasonably enough) 
is highly *unstable* just yet.

## Installation

We intentionally do not provide a download for the plugin just yet. If you are
brave enough and want to use the plugin, you have to build it from source.

Building:

```
$ git clone https://github.com/alexeykudinkin/intellij-rust
$ cd intellij-rust
$ ./gradlew buildPlugin
```

This creates a zip archive in `build/distributions` which you can install with
`install plugin from disk` action.


## Usage

See the [usage docs](doc/Usage.md).

## FAQ

Here would be a list of the most frequent asked questions: [FAQ](https://github.com/alexeykudinkin/intellij-rust/wiki/FAQ)
 
## Bugs

Current high-volatility state entails no support just yet, so be patient, please, and save your anger until it hits stability milestone (at least)
 Until then, no crusade to fight any issues (even completely relevant ones) may be possible.

## Contributing

You're encouraged to contribute to the plugin in any form if you've found any issues or missing
functionality that you'd want to see. In order to get started, check out
[CONTRIBUTING.md](CONTRIBUTING.md) guide.
