---
sidebar_position: 5
---

# Web-RPC

Web-RPC is a very simple RPC protocol which enables all the flexibility typically offered by REST APIs without any
boilerplate while allowing reuse of the existing REST API tools.


## Overview

### Motivation

[REST](https://en.wikipedia.org/wiki/Representational_state_transfer) as a theoretical concept is deemed to be
independent of specific data formats, transport protocols or even calling conventions. In reality, virtually all
most REST APIs simply model CRUD operations by translating HTTP requests and responses carrying JSON payload into
function calls on the remote server.

Such translation requires deciding how to represent and where to store REST API call data and metadata within HTTP and JSON structures. There are many ways to do that which achieve exactly the same result via slightly
differrent and thus incompatible means. While some good practices are sometimes discussed, a significant widely used
standard does not seem to exist.

These are the same concerns which various [RPC](https://en.wikipedia.org/wiki/Remote_procedure_call) or
protocols already solved. Consequently, creation of a typical REST API requires additional effort largely
equivalent to designing and partially implementing a new custom RPC protocol.


### Goals

Web-RPC is an attempt to demonstrate that the REST-compatible remote API functionality required by web applications
and online services can be achieved without effectivelly ending up designing a RPC protocol for each API.

Web-RPC can be understood to be any of the following:
- Minimalistic sibling of [JSON-RPC](https://www.jsonrpc.org/specification) storing metadata in THTTP requests
- RPC protocol supporting various practical features often provided by REST APIs
- REST-style protocol prescribing a standard way to represent data and meta-data in REST API requests and responses


### Features

- Messages in JSON format over HTTP
- HTTP method freely selected by the client
- Additional metadata in HTTP headers


## Request

### HTTP method

HTTP methods are not specified by the API but chosen by the client from the following options depending on the desired
call semantics:
- POST - standard non-cached call with arguments in the request body
- GET - cacheable call with arguments as URL query parameters

### URL format

- Remote API endpoint: http://example.org/api
- Remote API function: hello
- Remote API function arguments:
  * who = world
  * n = 1

```http
http://example.org/api/hello?who=world&n=1
```

- URL path components following an API-dependent prefix must specify the invoked function
- URL query parameters may specify additional arguments for the invoked function

Identically named invoked function arguments must not be supplied both in the request body and as URL query parameter.
Such an ambiguous call must cause an error.

### POST request

All invoked function arguments must be supplied in the request body consisting of a JSON object with its field names
representing the remote function parameter names and field values their respective argument values. Invoked function
arguments must not be specified as URL query parameters

- Message format: JSON
- Method: POST
- Content-Type: application/json

**Remote call**

```scala
hello(who = "world", n = 1)
```

**Request headers**

```http
POST http://example.org/api/hello
Content-Type: application/json
```

**Request body**

```json
{
  "who": "world",
  "n": 1
}
```

### GET request

All invoked function arguments must be supplied as URL query parameters with query parameter names representing the
remote function parameter names and query parameter values their respective argument values. Multiple instances of
identically named query parameters must not be used.

- Method: GET

**Remote call**

```scala
remoteApi.hello(who = "world", n = 1)
```

**Request headers**

```http
GET http://example.org/api/hello?who=world&n=1
```

**Request body**

*Empty*


## Response

### Success response body

Response body is interpreted as a successful invocation result if it consists of a JSON object containing a `result`
field. The `result` field value represents the return value of the invoked remote function.

- Message format: JSON
- Content-Type: application/json

**Response headers**

```http
Content-Type: application/json
```

**Response body**

```json
{
  "result": "test"
}
```

### Error response body

Response body is interpreted as a failed invocation result if it consists of a JSON object containing an `error` field.
The `error` field value is a JSON object providing further information about the failure and consisting of the following
fields:

- `message` - A JSON string representing an error message. This field is mandatory.
- `code` - A JSON number representing an error code. This field is optional.
- `details` - An arbitrary JSON value representing additional error information. This field is optional.

Error codes in inclusive range between -32768 and -32000 are reserved for protocol errors with specific meaning as follows:

- `-32600` - Invalid request. Request is malformed or missing.
- `-32601` - Function not found. Remote function does not exist.
- `-32602` - Invalid arguments. Supplied arguments have incorrect data type.
- `-32603` - Server error. Internal request processing error.

- Message format: JSON
- Content-Type: application/json

**Response headers**

```http
Content-Type: application/json
```

**Response body**

```json
{
  "error": {
    "message": "Some error",
    "code": 1,
    "details": {
      ...
    }
  }
}
```
