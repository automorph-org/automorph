package automorph.system

/**
 * Placeholder to include Monix in global Scaladoc index during documentation generation.
 *
 * Monix is excluded from main Scaladoc generation due to dependency conflicts with Cats Effect.
 * Monix Scaladoc is generated separately and copied over to the global Scaladoc target directory.
 */
final case class MonixSystem()

case object MonixSystem
