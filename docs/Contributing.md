---
sidebar_position: 9
---

# Contributing

Please feel free to open an [issue](https://github.com/automorph-org/automorph/issues/new) or a
[pull request](https://github.com/automorph-org/automorph/compare)
with questions, ideas, features, improvements or fixes.


## Suggested improvements

- Stricter type checking in API method invocation macros
- Additional RPC protocol implementations
- More transport layer integrations
- Context-based request filtering
- Better documentation


## Build requirements

- [JDK](https://openjdk.java.net/) 11+

**Note**: Due to the latest Scala 3 compiler defect the build currently does not work on JDK 21. Use `-java-home` SBT option to select an alternative JDK if needed.

### Documentation

- [Yarn](https://yarnpkg.com/) 1.22+

### Release

- [GnuPG](https://www.gnupg.org/)
- [GitHub CLI](https://cli.github.com/)


## Testing

**Note**: Due to uPickle design peculiarities the uPickle plugin takes a very long time to build but it works.


### Basic tests

```shell
sbt -client + test
```

### Simple standard API tests for all transport plugins and default codec plugin only

```shell
TEST_LEVEL=simple sbt '+ test'
```

### Complex generative API tests for all transport plugins and default codec plugin only

```shell
TEST_LEVEL=complex sbt '+ test'
```

### Complex generative API tests for all transport plugins and all codec plugins

```shell
TEST_LEVEL=all sbt '+ test'
```

### Console log level

```shell
LOG_LEVEL=debug sbt '+ test'
```

### Generated code logging

```shell
LOG_CODE=true sbt '+ test'
```

### Test log files

```
ls target/test-*.log
```


## Documentation

### Generate website

```shell
./sbt site
```

### Serve website

```shell
./sbt serveSite
```

