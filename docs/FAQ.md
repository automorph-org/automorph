---
sidebar_position: 8
---

# FAQ

## Integration

### Can I use Automorph with my existing effect system ?

Yes.

All Scala effect systems are supported. Please see the relevant [examples](Examples#effect-system).

### Can I use Automorph with my existing JSON parser ?

Probably.

Several Scala and Java JSON parsers are supported. Please see the relevant [examples](Examples#message-codec).

Additional data serialization layer support can be added if the following conditions are met:
- The underlying data format must support arbitrarily nested structures of basic data types.
- Given library must provide intermediate representation of structured data (i.e. document object model).

### Can I use Automorph with my existing HTTP server ?

Almost certainly.

Many Scala and Java HTTP server libraries are supported. Please see the relevant
[server examples](Examples#server-transport) and [endpoint examples](Examples#endpoint-transport).

Integrating with unsupported HTTP server involves the following steps:
- Create an instance of [ApiRequestHandler](https://automorph.org/api/automorph/handler/ApiRequestHandler.html)
  - Supply the desired [effect system](Plugins#effect-system) and [RPC protocol](Plugins#rpc-protocol).
  - Define a message context type parameter or just set it to `Unit` if no context is needed
- In the HTTP server request handling code
  - Pass the request body to `processRequest()` method of the `ApiRequstHandler` instance
  - Send the response body or an error returned by `processRequest()` to the client

### Can I use Automorph with my existing HTTP client ?

Most likely.

Many Scala and Java HTTP client libraries are supported. Please see the relevant [examples](Examples#client-transport).


## Features

### What is the Context type parameter for ?

Context represents any metadata which a message transport layer provides to the API implementation layer.

Since a message transport layer can be anything the context is a generic type parameter which is then defined
by specific message transport implementations. For example:
- HTTP transport provides request headers and query parameters
- AMQP transport provides correlation identifier and delivery mode
- Homing pigeon transport provides name and color of the bird

### Is it possible to bind individual functions as opposed to classes/trait methods ?

Not at the moment.

This feature was considered since it would fit well with overall functional style of the library.
However, this would introduce the following additional complexity which did not seem worth it:
- Function parameter names would have to be extracted via additional compile-time introspection since function values
in Scala do not preserve them
- Bind method variants for all function parameter numbers would have to be created for all entry point classes


## Web-RPC

### When to use Web-RPC ?

In case any of the following remote API concerns need to be addressed with minimal effort:
* Caching GET requests
* Using URLs to pass arguments
* External constraints requiring a simple REST-style API with RPC semantics
 
In other situations it is [probably](https://youtu.be/XyJh3qKjSMk?t=53) better to use an established remote call
protocol such as:
* [JSON-RPC](https://en.wikipedia.org/wiki/JSON-RPC)
* [Avro](https://en.wikipedia.org/wiki/Apache_Avro)
* [GraphQL](https://en.wikipedia.org/wiki/GraphQL)

### Can Web-RPC be used in without a specific Web-RPC library ?

Yes. Any REST client or server library will suffice. However, using a specific Web-RPC library minimizes the
implementation effort.

### Why call it a REST-style protocol when it is conceptually unrelated to REST ?

To illustrate that it provides remote API authors with a solution with capabilities equivalent to and compatible with
typical REST API protocols but does so by openly embracing RPC principles.


## Request

### HTTP method

HTTP methods are not specified by the API but chosen by the client from the following options depending on the desired
call semantics:
* POST - standard non-cached call with arguments either in the request body
* GET - cacheable call with arguments as URL query parameters only

### URL format

* Remote API endpoint: http://example.org/api
* Remote API function: hello
* Remote API function arguments:
  * some = world
  * n = 1

```http
http://example.org/api/hello?some=world&n=1
```

* URL path components following an API-dependent prefix must specify the invoked function
* URL query parameters may specify additional arguments for the invoked function

Identically named invoked function arguments must not be supplied both in the request body and as URL query parameter.
Such an ambiguous call must cause an error.

### Structured request body

All invoked function arguments must be supplied in the request body consisting of a JSON object with its field names
representing the remote function parameter names and field values their respective argument values. Invoked function
arguments must not be specified as URL query parameters

- Message format: JSON
- Method: POST
- Content-Type: application/json

**Remote call**

```scala
hello(some = "world", n = 1)
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

### Empty request body

All invoked function arguments must be supplied as URL query parameters with query parameter names representing the
remote function parameter names and query parameter values their respective argument values. Multiple instances of
identically named query parameters must not be used.

- Method: GET

**Remote call**

```scala
remoteApi.hello(some = "world", n = 1)
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

* `message` - A JSON string representing an error message. This field is mandatory.
* `code` - A JSON number representing an error code. This field is optional.
* `details` - An arbitrary JSON value representing additional error information. This field is optional.

Error codes in inclusive range between -32768 and -32000 are reserved for protocol errors with specific meaning as follows:

* `-32600` - Invalid request. Request is malformed or missing.
* `-32601` - Function not found. Remote function does not exist.
* `-32602` - Invalid arguments. Supplied arguments have incorrect data type.
* `-32603` - Server error. Internal request processing error.

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
