---
sidebar_position: 8
---

# Contributing

Please feel free to open an [issue](https://github.com/automorph-org/automorph/issues/new) or a
[pull request](https://github.com/automorph-org/automorph/compare)
with questions, ideas, features, improvements or fixes.


## Build requirements

* [JDK](https://openjdk.java.net/) 11+
* [SBT](https://www.scala-sbt.org/) 1.9+
* [NodeJS](https://nodejs.org/) 19+
* [Yarn](https://yarnpkg.com/) 1.22+

**Note**: uPickle plugin build might take a long time.


## Testing

### Basic tests

```shell
sbt '+ test'
```

### Simple remote API tests only

```shell
TEST_LEVEL=simple sbt '+ test'
```

### Complex remote API tests including all integration tests

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

### Test logs

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

