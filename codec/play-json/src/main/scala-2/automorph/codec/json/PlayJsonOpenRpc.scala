package automorph.codec.json

import automorph.schema.openrpc.{Components, Contact, ContentDescriptor, Error, Example, ExamplePairing, ExternalDocumentation, Info, License, Link, Method, Server, ServerVariable, Tag}
import automorph.schema.{OpenRpc, Schema}
import play.api.libs.json.{Json, Reads, Writes}

/** OpenRPC schema support for Play JSON message codec plugin. */
private[automorph] object PlayJsonOpenRpc {

  val reads: Reads[OpenRpc] = {
    implicit val schemaFrom: Reads[Schema] = Json.reads
    implicit val contactReads: Reads[Contact] = Json.reads
    implicit val contentDescriptorReads: Reads[ContentDescriptor] = Json.reads
    implicit val externalDocumentationReads: Reads[ExternalDocumentation] = Json.reads
    implicit val errorReads: Reads[Error] = Json.reads
    implicit val exampleReads: Reads[Example] = Json.reads
    implicit val licenseReads: Reads[License] = Json.reads
    implicit val serverVariableReads: Reads[ServerVariable] = Json.reads
    implicit val examplePairingReads: Reads[ExamplePairing] = Json.reads
    implicit val infoReads: Reads[Info] = Json.reads
    implicit val serverReads: Reads[Server] = Json.reads
    implicit val tagReads: Reads[Tag] = Json.reads
    implicit val linkReads: Reads[Link] = Json.reads
    implicit val componentsReads: Reads[Components] = Json.reads
    implicit val methodReads: Reads[Method] = Json.reads
    Json.reads[OpenRpc]
  }

  val writes: Writes[OpenRpc] = {
    implicit val schemaFrom: Writes[Schema] = Json.writes
    implicit val contactWrites: Writes[Contact] = Json.writes
    implicit val contentDescriptorWrites: Writes[ContentDescriptor] = Json.writes
    implicit val externalDocumentationWrites: Writes[ExternalDocumentation] = Json.writes
    implicit val errorWrites: Writes[Error] = Json.writes
    implicit val exampleWrites: Writes[Example] = Json.writes
    implicit val licenseWrites: Writes[License] = Json.writes
    implicit val serverVariableWrites: Writes[ServerVariable] = Json.writes
    implicit val examplePairingWrites: Writes[ExamplePairing] = Json.writes
    implicit val infoWrites: Writes[Info] = Json.writes
    implicit val serverWrites: Writes[Server] = Json.writes
    implicit val tagWrites: Writes[Tag] = Json.writes
    implicit val linkWrites: Writes[Link] = Json.writes
    implicit val componentsWrites: Writes[Components] = Json.writes
    implicit val methodWrites: Writes[Method] = Json.writes
    Json.writes[OpenRpc]
  }
}
