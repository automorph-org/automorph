---
sidebar_position: 9
---

# Contributing

Please feel free to open an [issue](https://github.com/automorph-org/automorph/issues/new) or a
[pull request](https://github.com/automorph-org/automorph/compare)
with questions, ideas, features, improvements or fixes.


## Potential improvements

* Better documentation
* More transport layer integrations
* Additional RPC protocol implementations
* Stricter type checking in API method invocation macros
* [ScalaJS](https://www.scala-js.org/) support


## Build requirements

* [JDK](https://openjdk.java.net/) 11+
* [SBT](https://www.scala-sbt.org/) 1.9+
* [NodeJS](https://nodejs.org/) 19+
* [Yarn](https://yarnpkg.com/) 1.22+

**Note**: uPickle plugin might take a long time to build but it works.


## Testing

### Basic tests

```shell
sbt '+ test'
```

### Simple API tests

```shell
TEST_LEVEL=simple sbt '+ test'
```

### Complex API and integration tests

```shell
TEST_LEVEL=all sbt '+ test'
```

### Console log level

```shell
LOG_LEVEL=DEBUG sbt '+ test'
```

### Generated code logging

```shell
LOG_CODE=true sbt '+ test'
```

### Test log file

```
less target/test.log
```


## Documentation

### Generate website

```shell
sbt site
```

### Serve website

```shell
sbt serveSite
```

