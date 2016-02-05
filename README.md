# ![intellij-rust](doc/logo.png) Rust IDE built using the IntelliJ Platform

[![Build Status](https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:IntellijIdeaPlugins_Rust_Tests)/statusIcon.svg)](https://teamcity.jetbrains.com/viewType.html?buildTypeId=IntellijIdeaPlugins_Rust_Tests&guest=1) [![Build Status](https://img.shields.io/travis/intellij-rust/intellij-rust/master.svg)](https://travis-ci.org/intellij-rust/intellij-rust) [![Join the chat at https://gitter.im/intellij-rust/intellij-rust](https://img.shields.io/badge/Gitter-Join%20Chat-blue.svg)](https://gitter.im/intellij-rust/intellij-rust?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

**BEWARE**

This is experimental implementation targeting bleeding-edge version of Rust language and (as may be assumed reasonably enough) 
is highly *unstable* just yet.

## Installation

We intentionally do not provide a download for the plugin just yet. If you are
brave enough and want to use the plugin, you have to build it from source. Note that it will download all build dependencies, so make sure you have enough space (one GB should be enough). 
You need IDEA 15 (or newer) to use this plugin. 

Building:

```
$ git clone https://github.com/intellij-rust/intellij-rust --recursive
$ cd intellij-rust
$ ./gradlew buildPlugin
```

This creates a zip archive in `build/distributions` which you can install with the `Install plugin from disk...` action found in `Settings > Plugins`.

## Usage

See the [usage docs](doc/Usage.md).

## Bugs

Current high-volatility state entails no support just yet, so be patient, please, and save your anger until it hits stability milestone (at least)
 Until then, no crusade to fight any issues (even completely relevant ones) may be possible.

## Contributing

You're encouraged to contribute to the plugin in any form if you've found any issues or missing
functionality that you'd want to see. In order to get started, check out
[CONTRIBUTING.md](CONTRIBUTING.md) guide.
