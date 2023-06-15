<br>

![automorph](https://github.com/automorph-org/automorph/raw/main/site/static/banner.jpg)

[![Documentation](https://img.shields.io/badge/Website-Documentation-purple)](https://automorph.org)
[![API](https://img.shields.io/badge/Scaladoc-API-blue)](https://automorph.org/api/index.html)
[![Artifacts](https://img.shields.io/badge/Releases-Artifacts-yellow)](
https://mvnrepository.com/artifact/org.automorph/automorph)
[![Build](https://github.com/automorph-org/automorph/workflows/Build/badge.svg)](
https://github.com/automorph-org/automorph/actions/workflows/build.yml)

**Automorph** is an [RPC](https://en.wikipedia.org/wiki/Remote_procedure_call) client and server library for [Scala](
https://www.scala-lang.org/) providing an easy way to invoke and expose remote APIs using [JSON-RPC](
https://www.jsonrpc.org/specification) and [Web-RPC](https://automorph.org/docs/Web-RPC) protocols.

* [Quick Start](https://automorph.org/docs/Quickstart)
* [Documentation](https://automorph.org)
* [API](https://automorph.org/api/index.html)
* [Artifacts](https://mvnrepository.com/artifact/org.automorph/automorph)
* [Contact](mailto:automorph.org@proton.me)


# Build

## Requirements

* [JDK](https://openjdk.java.net/) 11+
* [SBT](https://www.scala-sbt.org/) 1.9+
* [NodeJS](https://nodejs.org/) 19+
* [Yarn](https://yarnpkg.com/) 1.22+

**Note**: uPickle plugin build may take a long time but it works.


## Testing

### Test using basic tests

```shell
sbt '+ test'
```

### Test using simple remote API tests only

```shell
TEST_LEVEL=simple sbt '+ test'
```

### Test using complex remote API tests including all integration tests

```shell
TEST_LEVEL=all sbt '+ test'
```

### Test with specific console log level

```shell
LOG_LEVEL=DEBUG sbt '+ test'
```

### Test with generated code logging

```shell
LOG_CODE=true sbt '+ test'
```

### Review test logs

```
less target/test.log
```


## Documentation

### Generate documentation

```shell
sbt site
```

### Serve documentation

```shell
sbt serveSite
```

