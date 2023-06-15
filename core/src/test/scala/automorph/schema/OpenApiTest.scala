package automorph.schema

import automorph.RpcFunction
import automorph.RpcFunction.Parameter
import automorph.schema.OpenApi
import automorph.schema.openapi.{Info, MediaType, Operation, PathItem, RequestBody, Response, RpcSchema, Schema}
import test.base.BaseTest

class OpenApiTest extends BaseTest {

  private val function = RpcFunction(
    "test",
    Seq(
      Parameter("foo", "String"),
      Parameter("bar", "Integer"),
      Parameter("alt", "Option[Map[String, Boolean]"),
    ),
    "Seq[String]",
    Some("Test function"),
  )
  private val functionSchemas = Seq(
    function -> RpcSchema(
      Schema(
        Some(OpenApi.objectType),
        Some(OpenApi.requestTitle),
        Some(s"Test ${OpenApi.requestTitle}"),
        Some(Map(
          "function" -> Schema(Some("string"), Some("function"), Some("Invoked function name")),
          "arguments" -> Schema(
            Some(OpenApi.objectType),
            Some(function.name),
            Some(OpenApi.argumentsDescription),
            Option(Schema.parameters(function)).filter(_.nonEmpty),
            Option(Schema.requiredParameters(function).toList).filter(_.nonEmpty),
          ),
        )),
        Some(List("function", "arguments")),
      ),
      Schema(
        Some(OpenApi.objectType),
        Some(OpenApi.resultTitle),
        Some(s"Test ${OpenApi.resultTitle}"),
        Some(Map("result" -> Schema.result(function))),
        Some(List("result")),
      ),
      Schema(
        Some(OpenApi.objectType),
        Some(OpenApi.errorTitle),
        Some(s"Test ${OpenApi.errorTitle}"),
        Some(Map(
          "error" -> Schema(
            Some("string"),
            Some("error"),
            Some("Failed function call error details"),
            Some(Map("message" -> Schema(Some("string"), Some("message"), Some("Error message")))),
            Some(List("message")),
          )
        )),
        Some(List("error")),
      ),
    )
  )
  private val expected = OpenApi(
    openapi = "3.1.0",
    info = Info(title = "", version = ""),
    paths = Some(value =
      Map(
        "test" -> PathItem(
          description = Some(value = "Test function"),
          post = Some(value =
            Operation(
              requestBody = Some(value =
                RequestBody(
                  content = Map(
                    "application/json" -> MediaType(schema =
                      Some(value =
                        Schema(
                          `type` = Some(value = "object"),
                          title = Some(value = "Request"),
                          description = Some(value = "Test Request"),
                          properties = Some(value =
                            Map(
                              "function" -> Schema(
                                `type` = Some(value = "string"),
                                title = Some(value = "function"),
                                description = Some(value = "Invoked function name"),
                              ),
                              "arguments" -> Schema(
                                `type` = Some(value = "object"),
                                title = Some(value = "test"),
                                description = Some(value = "Function argument values by name"),
                                properties = Some(value =
                                  Map(
                                    "foo" -> Schema(`type` = Some(value = "String"), title = Some(value = "foo")),
                                    "bar" -> Schema(`type` = Some(value = "Integer"), title = Some(value = "bar")),
                                    "alt" -> Schema(
                                      `type` = Some(value = "Option[Map[String, Boolean]"),
                                      title = Some(value = "alt"),
                                    ),
                                  )
                                ),
                                required = Some(value = List("foo", "bar")),
                              ),
                            )
                          ),
                          required = Some(value = List("function", "arguments")),
                        )
                      )
                    )
                  ),
                  required = Some(value = true),
                )
              ),
              responses = Map(
                "default" -> Response(
                  description = "Failed function call error details",
                  content = Some(value =
                    Map(
                      "application/json" -> MediaType(schema =
                        Some(value =
                          Schema(
                            `type` = Some(value = "object"),
                            title = Some(value = "Error"),
                            description = Some(value = "Test Error"),
                            properties = Some(value =
                              Map(
                                "error" -> Schema(
                                  `type` = Some(value = "string"),
                                  title = Some(value = "error"),
                                  description = Some(value = "Failed function call error details"),
                                  properties = Some(value =
                                    Map(
                                      "message" -> Schema(
                                        `type` = Some(value = "string"),
                                        title = Some(value = "message"),
                                        description = Some(value = "Error message"),
                                      )
                                    )
                                  ),
                                  required = Some(value = List("message")),
                                )
                              )
                            ),
                            required = Some(value = List("error")),
                          )
                        )
                      )
                    )
                  ),
                ),
                "200" -> Response(
                  description = "Succesful function call result value",
                  content = Some(value =
                    Map(
                      "application/json" -> MediaType(schema =
                        Some(value =
                          Schema(
                            `type` = Some(value = "object"),
                            title = Some(value = "Result"),
                            description = Some(value = "Test Result"),
                            properties = Some(value =
                              Map(
                                "result" -> Schema(`type` = Some(value = "Seq[String]"), title = Some(value = "result"))
                              )
                            ),
                            required = Some(value = List("result")),
                          )
                        )
                      )
                    )
                  ),
                ),
              ),
            )
          ),
        )
      )
    ),
  )

  "" - {
    "Schema" in {
      val schema = OpenApi(functionSchemas)
      schema.shouldEqual(expected)
    }
  }
}
