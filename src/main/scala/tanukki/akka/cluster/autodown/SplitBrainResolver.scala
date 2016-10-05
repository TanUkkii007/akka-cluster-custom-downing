package tanukki.akka.cluster.autodown

trait SplitBrainResolver {
  def shutdownSelf(): Unit
}
