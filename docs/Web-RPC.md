---
sidebar_position: 5
---

# Web-RPC

Web-RPC is a very simple RPC protocol which enables all the flexibility typically offered by REST APIs without any
boilerplate while allowing reuse of the existing REST API tools.


## Overview

### Motivation

[REST](https://en.wikipedia.org/wiki/Representational_state_transfer) as a theoretical concept is deemed to be
independent of specific data formats, transport protocols or even calling conventions. However, the vast majority of
REST APIs created for web applications and online services involve translating HTTP requests and responses carrying
JSON payload into function calls on the remote site.

Such translation requires deciding how to represent REST API call data and metadata using the underlying transport
protocol. This includes determining the message format, its structure, transport protocol meta-data such as various
headers and so on. There are many ways how to do this ultimately achieving exactly the same result using slightly
different means. And while some good practices can be discussed, there does not seem to be an agreed upon standard.

These are virtually the same concerns which various [RPC](https://en.wikipedia.org/wiki/Remote_procedure_call) or
messaging protocols need to solve. Consequently, creation of a typical REST API requires additional effort largely
equivalent to designing and implementing a unique custom RPC protocol.


### Goals

Web-RPC is an attempt to demonstrate that the REST-compatible remote API functionality required by web applications
and online services can be achieved without effectively ending up designing an RPC protocol for each API.

Web-RPC can be understood to be any of the following:
- Minimalistic sibling of [JSON-RPC](https://www.jsonrpc.org/specification) using HTTP as transport protocol
- RPC protocol supporting various practical mechanisms often provided by typical REST APIs
- REST-style protocol prescribing a standard way to represent data and meta-data in REST API requests and responses


### Features

- HTTP as transport protocol
- Structured messages in JSON format
- API function name as a last URL path element
- API function arguments can be supplied either in the request body or as URL query parameters
- Call meta-data in HTTP headers


## Request

### HTTP method

HTTP methods are not specified by the API but chosen by the client from the following options
depending on the desired call semantics:
- POST - standard function call with arguments either in the request body or as URL query parameters
- GET - pure function call with cacheable result and arguments as URL query parameters only

### URL format

- URL path components following an API endpoint prefix must specify the invoked remote function name
- URL query parameters for cacheable requests may specify additional arguments for the remote function

Identically named invoked function arguments must not be supplied both in the request body and as URL query parameter.
Such an ambiguous call must cause an error.

```http
http[s]:://host[:port]/API-ENDPOINT/FUNCTION[?PARAMETER1=VALUE1&PARAMETER2=VALUE2]
```

#### Example

- Remote API endpoint: http://example.org/api
- Remote API function: hello
- Remote API function arguments:
  * some = world
  * n = 1

```http
http://example.org/api/hello?some=world&n=1
```

### Standard request

- All invoked function arguments must be supplied in the request body as a JSON object.
- Request body field names represent remote function argument names and field values their respective values.
- Remote function arguments must not be specified as URL query parameters

- Message format: JSON
- Method: POST
- Content-Type: application/json

### Example

**Remote call**

```scala
rpcClient.hello(some = "world", n = 1)
```

**Request headers**

```http
POST http://example.org/api/hello
Content-Type: application/json
```

**Request body**

```json
{
  "some": "world",
  "n": 1
}
```

### Cacheable request

- All invoked function arguments must be supplied as request URL query parameters.
- Query parameter names represent remote function argument names and query parameter values their respective values.
- Multiple instances of identically named query parameters must not be used.
- Request body must be empty.

- Method: GET

#### Example

**Remote call**

```scala
rpcClient.hello(some = "world", n = 1)
```

**Request headers**

```http
GET http://example.org/api/hello?some=world&n=1
```

**Request body**

*Empty*


## Response

### Structured response body

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
