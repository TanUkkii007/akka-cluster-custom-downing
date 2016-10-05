package tanukki.akka.cluster.autodown

/**
  * Thrown when split brain is resolved and resolved member should shutdown itself.
  * This error is fired in DowningProvider and handled by ClusterCoreSupervisor, which leads to cluster shutdown.
  * @param strategyName
  */
@SerialVersionUID(1L) class SplitBrainResolvedError(strategyName: String) extends Error(s"Resolution of split brain by $strategyName results in shutdown")